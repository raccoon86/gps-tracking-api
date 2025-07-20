package com.sponovation.runtrack.service

import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.algorithm.KalmanFilter
import com.sponovation.runtrack.algorithm.MapMatcher
import com.sponovation.runtrack.algorithm.MatchResult
import com.sponovation.runtrack.util.GeoUtils
import io.jenetics.jpx.Track
import io.jenetics.jpx.WayPoint
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * GPX 파일 처리 및 실시간 위치 보정 서비스
 *
 * ================================ 핵심 비즈니스 로직 ================================
 * 
 * 1. **GPX 경로 관리**
 *    - GPX 파일 업로드 및 파싱
 *    - 웨이포인트 추출 및 100미터 간격 보간 포인트 생성
 *    - Redis 기반 코스 데이터 저장 (DB 저장 기능은 비활성화됨)
 *    - 체크포인트 자동 생성 (시작점, 중간점, 종료점)
 * 
 * 2. **실시간 GPS 위치 보정**
 *    - 참가자의 실시간 GPS 데이터 수신
 *    - 3차원 칼만 필터를 통한 GPS 노이즈 제거 및 정확도 향상
 *    - 맵 매칭 알고리즘을 통한 GPS 위치를 실제 경로에 스냅
 *    - GPS 신뢰도 및 보정 품질 평가
 * 
 * 3. **체크포인트 통과 관리**
 *    - 실시간 체크포인트 통과 감지 (반경 30m 기준)
 *    - 구간별 소요 시간 계산 및 누적 시간 관리
 *    - 체크포인트 통과 이력 Redis 저장
 *    - 중복 통과 방지 로직
 * 
 * 4. **리더보드 시스템**
 *    - 참가자별 진행 상황 추적 (가장 먼 체크포인트 기준)
 *    - Redis Sorted Set 기반 실시간 리더보드 업데이트
 *    - 체크포인트 인덱스와 누적 시간을 조합한 순위 계산
 * 
 * 5. **데이터 저장 구조**
 *    - Redis: 실시간 위치, 체크포인트 통과 시간, 코스 데이터
 *    - Hash: 개별 참가자 위치 정보
 *    - Sorted Set: 이벤트별 리더보드
 *    - String: GPX 파싱 데이터
 * 
 * ================================ 알고리즘 특징 ================================
 * 
 * - **칼만 필터**: GPS 정확도와 속도를 고려한 가중치 기반 3차원 노이즈 제거
 * - **맵 매칭**: 보간된 경로 포인트와의 거리, 방향을 고려한 최적 매칭점 탐색
 * - **체크포인트 감지**: 이전 위치와 현재 위치의 경계 통과 여부로 정확한 통과 시점 판정
 * - **보정 품질 평가**: GPS 신뢰도, 매칭 점수, 보정 강도를 종합한 4단계 품질 등급
 * 
 * ================================ 주요 제약사항 ================================
 * 
 * - 체크포인트 통과 인식 반경: 30미터
 * - 최대 구간 시간: 24시간
 * - GPX 파일 크기 제한: 10MB
 * - Redis TTL: 위치 데이터 24시간, 리더보드 7일
 * - 보간 간격: 100미터
 * 
 * 현재 도메인 엔티티들이 삭제되어 데이터베이스 저장 기능은 비활성화됨
 * Redis 기반 코스 데이터 저장만 지원
 */
@Service
@Transactional(readOnly = true)
class GpxService(
    private val realtimeLocationService: RealtimeLocationService,
    private val courseDataService: CourseDataService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val checkpointTimesService: CheckpointTimesService,
    private val gpxParsingRedisService: GpxParsingRedisService
) {

    private val logger = LoggerFactory.getLogger(GpxService::class.java)

    companion object {
        private const val CHECKPOINT_CROSSING_THRESHOLD = 30.0 // 체크포인트 통과 인식 반경 (미터)
        private const val MAX_SEGMENT_TIME_HOURS = 24L // 최대 구간 시간 (시간)
        private const val MAX_EXPECTED_SEGMENT_TIME = 360000L // 최대 예상 구간 시간 (100시간, 초 단위)
    }

    /**
     * 웨이포인트 통과 감지 데이터 클래스
     */
    data class CheckpointCrossing(
        val cpId: String,
        val cpIndex: Int,
        val checkpointId: String,
        val passTime: Long,
        val latitude: Double,
        val longitude: Double,
        val distanceFromStart: Double?
    )

    /**
     * ================================ GPS 위치 데이터 클래스 ================================
     * 
     * Redis에 저장되는 참가자의 GPS 위치 및 보정 정보를 담는 데이터 클래스입니다.
     * 원본 GPS 센서 데이터와 칼만 필터 + 맵 매칭으로 보정된 데이터를 모두 포함합니다.
     * 
     * **데이터 분류:**
     * 1. 식별 정보: userId, eventId, eventDetailId
     * 2. 원본 GPS: raw로 시작하는 필드들 (센서 직접 수신값)
     * 3. 보정 GPS: corrected로 시작하는 필드들 (알고리즘 처리값)
     * 4. 계산 정보: 이동거리, 시간 등 파생 데이터
     * 
     * @property userId 사용자 ID
     * @property eventId 이벤트 ID  
     * @property eventDetailId 이벤트 상세 ID (코스 구분용)
     * @property rawLatitude 원본 GPS 위도 (도, WGS84)
     * @property rawLongitude 원본 GPS 경도 (도, WGS84)
     * @property rawAltitude 원본 GPS 고도 (미터, 해수면 기준, null 가능)
     * @property rawAccuracy GPS 신호 정확도 (미터, 작을수록 정확, null 가능)
     * @property rawTime GPS 수신 시간 (Unix timestamp, 초)
     * @property rawSpeed 원본 GPS 속도 (km/h 또는 m/s, null 가능)
     * @property correctedLatitude 보정된 위도 (칼만 필터 + 맵 매칭 적용)
     * @property correctedLongitude 보정된 경도 (칼만 필터 + 맵 매칭 적용)
     * @property correctedAltitude 보정된 고도 (칼만 필터 노이즈 제거, null 가능)
     * @property lastUpdated 마지막 업데이트 시간 (Unix timestamp, 초)
     * @property heading GPS 이동 방향 (도, 0-359, 북쪽=0, null 가능)
     * @property distanceCovered 누적 이동 거리 (미터)
     * @property cumulativeTime 총 소요 시간 (초)
     */
    data class LocationData(
        val userId: Long,
        val eventId: Long,
        val eventDetailId: Long,
        val rawLatitude: Double,
        val rawLongitude: Double,
        val rawAltitude: Double?,
        val rawAccuracy: Float?,
        val rawTime: Long,
        val rawSpeed: Float?,
        val correctedLatitude: Double,
        val correctedLongitude: Double,
        val correctedAltitude: Double?,
        val lastUpdated: Long,
        val heading: Float?,
        val distanceCovered: Double,
        val cumulativeTime: Long
    )

    /**
     * 참가자의 가장 먼 체크포인트 정보
     */
    data class FarthestCheckpointInfo(
        val cpIndex: Int,
        val cpId: String,
        val cumulativeTime: Long
    )

    /**
     * ================================ 체크포인트 통과 감지 핵심 로직 ================================
     * 
     * **비즈니스 목적:**
     * 마라톤 참가자가 코스 상의 체크포인트를 정확히 통과했는지 실시간으로 감지하여
     * 구간별 기록 측정 및 순위 계산의 기준점을 제공합니다.
     * 
     * **감지 알고리즘:**
     * 1. **경계 교차 감지**: 이전 위치에서 현재 위치로 이동하면서 체크포인트 반경을 통과했는지 확인
     *    - 이전 위치: 체크포인트 반경(30m) 밖
     *    - 현재 위치: 체크포인트 반경(30m) 안
     *    - 단순히 반경 안에 있는 것이 아니라 '경계를 통과'해야 통과로 인정
     * 
     * 2. **중복 통과 방지**: Redis에서 해당 체크포인트 통과 이력 확인
     *    - 이미 통과한 체크포인트는 재통과로 인정하지 않음
     *    - 참가자가 같은 지점을 여러 번 지나가더라도 첫 통과만 기록
     * 
     * 3. **정확한 통과 시점 기록**: GPS 수신 시점을 기준으로 통과 시간 기록
     *    - Unix timestamp 기반 정확한 시간 기록
     *    - 구간별 소요 시간 계산의 기준점 제공
     * 
     * **처리 과정:**
     * STEP 1: Redis에서 이전 위치 조회
     * STEP 2: GPX 파싱 데이터에서 체크포인트 정보 로드
     * STEP 3: 각 체크포인트별로 통과 여부 검사
     * STEP 4: 경계 교차 및 중복 검사
     * STEP 5: 새로 통과한 체크포인트 목록 반환
     * 
     * **중요 제약사항:**
     * - 체크포인트 인식 반경: 30미터 (CHECKPOINT_CROSSING_THRESHOLD)
     * - 체크포인트 타입: start, checkpoint, finish만 인식
     * - 순서 무관: 체크포인트를 순서대로 통과하지 않아도 인정 (다양한 코스 상황 고려)
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID  
     * @param eventDetailId 이벤트 상세 ID
     * @param currentLat 현재 위도
     * @param currentLng 현재 경도
     * @param currentTime 현재 시간 (Unix Timestamp)
     * @param routeProgress 경로 진행률 (0.0 ~ 1.0, 선택적)
     * @param distanceFromStart 시작점으로부터 거리 (미터, 선택적)
     * @return 새로 통과한 체크포인트 리스트 (CheckpointCrossing 객체들)
     */
    private fun detectCheckpointCrossings(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        currentLat: Double,
        currentLng: Double,
        currentTime: Long,
        routeProgress: Double?,
        distanceFromStart: Double?
    ): List<CheckpointCrossing> {

        val crossings = mutableListOf<CheckpointCrossing>()

        try {
            // 이전 위치 조회
            val previousLocation = checkpointTimesService.getPreviousLocation(
                userId = userId.toString(),
                eventId = eventId.toString(),
                eventDetailId = eventDetailId.toString()
            )

            logger.debug("체크포인트 통과 감지: userId=$userId, eventDetailId=$eventDetailId")
            logger.debug("현재 위치: ($currentLat, $currentLng), 시간: $currentTime")
            logger.debug("이전 위치: ${previousLocation?.let { "(${it.latitude}, ${it.longitude}), 시간: ${it.timestamp}" } ?: "없음"}")

            // GPX 파싱 데이터에서 체크포인트 정보 조회
            val gpxParsingData = gpxParsingRedisService.getGpxParsingData(eventId, eventDetailId)

            if (!gpxParsingData.success || gpxParsingData.points.isEmpty()) {
                logger.warn("GPX 파싱 데이터가 없어 체크포인트 통과 감지를 건너뜀: eventId=$eventId, eventDetailId=$eventDetailId")
                return crossings
            }

            // 체크포인트만 필터링 (start, checkpoint, finish)
            val checkpoints = gpxParsingData.points.filter {
                it.type in listOf("start", "checkpoint", "finish")
            }.sortedBy { it.cpIndex }

            logger.debug("체크포인트 개수: ${checkpoints.size}")

            // 각 체크포인트에 대해 통과 여부 확인
            checkpoints.forEach { checkpoint ->
                val cpLat = checkpoint.latitude
                val cpLng = checkpoint.longitude
                val cpId = checkpoint.cpId ?: "UNKNOWN"
                val cpIndex = checkpoint.cpIndex ?: 0

                // 현재 위치와 체크포인트 거리 계산
                val distanceToCheckpoint = GeoUtils.calculateDistance(currentLat, currentLng, cpLat, cpLng)

                // 체크포인트 통과 인식 반경 내에 있는지 확인
                if (distanceToCheckpoint <= CHECKPOINT_CROSSING_THRESHOLD) {

                    // 이미 통과한 체크포인트인지 확인
                    val alreadyPassed = checkpointTimesService.hasPassedCheckpoint(
                        userId = userId.toString(),
                        eventId = eventId.toString(),
                        eventDetailId = eventDetailId.toString(),
                        checkpointId = cpId
                    )

                    if (!alreadyPassed) {
                        // 이전 위치에서 현재 위치로 이동하면서 체크포인트 경계를 통과했는지 확인
                        val crossedBoundary = if (previousLocation != null) {
                            val prevDistanceToCheckpoint = GeoUtils.calculateDistance(
                                previousLocation.latitude, previousLocation.longitude, cpLat, cpLng
                            )

                            // 이전에는 반경 밖에 있었지만 현재는 반경 안에 있는 경우
                            prevDistanceToCheckpoint > CHECKPOINT_CROSSING_THRESHOLD &&
                                    distanceToCheckpoint <= CHECKPOINT_CROSSING_THRESHOLD
                        } else {
                            // 이전 위치가 없으면 현재 위치가 반경 안에 있으면 통과로 간주
                            true
                        }

                        if (crossedBoundary) {
                            logger.info(
                                "새로운 체크포인트 통과 감지: cpId=$cpId, cpIndex=$cpIndex, " +
                                        "거리=${String.format("%.2f", distanceToCheckpoint)}m"
                            )

                            crossings.add(
                                CheckpointCrossing(
                                    cpId = cpId,
                                    cpIndex = cpIndex,
                                    checkpointId = cpId,
                                    passTime = currentTime,
                                    latitude = cpLat,
                                    longitude = cpLng,
                                    distanceFromStart = distanceFromStart
                                )
                            )
                        }
                    }
                }
            }

            logger.debug("새로 통과한 체크포인트 개수: ${crossings.size}")

        } catch (e: Exception) {
            logger.error("체크포인트 통과 감지 중 오류 발생: userId=$userId, eventDetailId=$eventDetailId", e)
        }

        return crossings
    }

    /**
     * 체크포인트 통과 정보를 Redis에 저장
     *
     * @param crossings 통과한 체크포인트 리스트
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 체크포인트 통과 정보 DTO 리스트
     */
    private fun saveCheckpointCrossings(
        crossings: List<CheckpointCrossing>,
        userId: Long,
        eventId: Long,
        eventDetailId: Long
    ): List<CheckpointReachDto> {

        val checkpointReaches = mutableListOf<CheckpointReachDto>()

        crossings.forEach { crossing ->
            try {
                // 체크포인트 통과 시간 기록
                checkpointTimesService.recordCheckpointPassTime(
                    userId = userId.toString(),
                    eventId = eventId.toString(),
                    eventDetailId = eventDetailId.toString(),
                    checkpointId = crossing.cpId,
                    passTimeSeconds = crossing.passTime
                )

                // 구간 시간 계산
                val (segmentDuration, cumulativeTime) = calculateSegmentTimes(
                    userId = userId,
                    eventId = eventId,
                    eventDetailId = eventDetailId,
                    currentCheckpointId = crossing.cpId,
                    currentPassTime = crossing.passTime
                )

                // 구간 기록 저장
                if (segmentDuration != null && cumulativeTime != null) {
                    saveSegmentRecord(
                        userId = userId.toString(),
                        eventId = eventId.toString(),
                        eventDetailId = eventDetailId.toString(),
                        checkpointId = crossing.cpId,
                        segmentDuration = segmentDuration,
                        cumulativeTime = cumulativeTime
                    )
                }

                // 응답 DTO 생성
                checkpointReaches.add(
                    CheckpointReachDto(
                        checkpointId = crossing.checkpointId,
                        cpId = crossing.cpId,
                        cpIndex = crossing.cpIndex,
                        passTime = crossing.passTime,
                        segmentDuration = segmentDuration,
                        cumulativeTime = cumulativeTime,
                        reachedAt = crossing.passTime
                    )
                )

                logger.info(
                    "체크포인트 통과 기록 저장 완료: userId=$userId, cpId=${crossing.cpId}, " +
                            "구간시간=${segmentDuration ?: "N/A"}초, 누적시간=${cumulativeTime ?: "N/A"}초"
                )

            } catch (e: Exception) {
                logger.error("체크포인트 통과 기록 저장 실패: userId=$userId, cpId=${crossing.cpId}", e)
            }
        }

        return checkpointReaches
    }

    /**
     * 구간 시간 계산
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param currentCheckpointId 현재 체크포인트 ID
     * @param currentPassTime 현재 통과 시간
     * @return Pair<구간 시간, 누적 시간>
     */
    private fun calculateSegmentTimes(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        currentCheckpointId: String,
        currentPassTime: Long
    ): Pair<Long?, Long?> {

        return try {
            // 모든 체크포인트 통과 시간 조회
            val allPassTimes = checkpointTimesService.getAllCheckpointPassTimes(userId, eventId, eventDetailId)

            // 시간 순으로 정렬
            val sortedPassTimes = allPassTimes.entries.sortedBy { it.value }

            // 현재 체크포인트 이전의 체크포인트 찾기
            val currentIndex = sortedPassTimes.indexOfFirst { it.key == currentCheckpointId }

            val segmentDuration = if (currentIndex <= 0) {
                // 첫 번째 체크포인트인 경우 시작 시간부터 계산
                // 실제 구현에서는 이벤트 시작 시간을 사용해야 함
                val startTime = getEventStartTime(eventId, eventDetailId)
                if (startTime != null) {
                    val duration = currentPassTime - startTime
                    if (duration > 0 && duration < MAX_SEGMENT_TIME_HOURS * 3600) duration else null
                } else {
                    null
                }
            } else {
                // 이전 체크포인트부터 현재 체크포인트까지 계산
                val previousPassTime = sortedPassTimes[currentIndex - 1].value
                val duration = currentPassTime - previousPassTime
                if (duration > 0 && duration < MAX_SEGMENT_TIME_HOURS * 3600) duration else null
            }

            // 누적 시간 계산
            val cumulativeTime = if (segmentDuration != null) {
                if (currentIndex <= 0) {
                    segmentDuration
                } else {
                    // 이전 체크포인트들의 누적 시간 + 현재 구간 시간
                    val previousCumulativeTime =
                        getPreviousCumulativeTime(userId, eventId, eventDetailId, currentIndex - 1, sortedPassTimes)
                    if (previousCumulativeTime != null) {
                        previousCumulativeTime + segmentDuration
                    } else {
                        segmentDuration
                    }
                }
            } else {
                null
            }

            Pair(segmentDuration, cumulativeTime)

        } catch (e: Exception) {
            logger.error("구간 시간 계산 실패: userId=$userId, checkpointId=$currentCheckpointId", e)
            Pair(null, null)
        }
    }

    /**
     * 이벤트 시작 시간 조회 (임시 구현)
     */
    private fun getEventStartTime(eventId: Long, eventDetailId: Long): Long {
        // 임시로 현재 시간에서 12시간 전을 시작 시간으로 사용
        // 실제 구현에서는 Event 테이블에서 시작 시간을 조회해야 함
        return Instant.now().epochSecond - 12 * 3600
    }

    /**
     * 이전 누적 시간 조회
     */
    private fun getPreviousCumulativeTime(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        previousIndex: Int,
        sortedPassTimes: List<Map.Entry<String, Long>>
    ): Long? {
        // 임시로 구간 시간들을 합산하여 누적 시간 계산
        // 실제 구현에서는 Redis에서 저장된 누적 시간을 조회해야 함
        return null
    }

    /**
     * 구간 기록 저장
     */
    private fun saveSegmentRecord(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String,
        segmentDuration: Long,
        cumulativeTime: Long
    ) {
        try {
            val key = "participantSegmentRecords:$userId:$eventId:$eventDetailId"

            // 구간 시간 저장
            redisTemplate.opsForHash<String, String>().put(
                key,
                "${checkpointId}_duration",
                segmentDuration.toString()
            )

            // 누적 시간 저장
            redisTemplate.opsForHash<String, String>().put(
                key,
                "${checkpointId}_cumulative",
                cumulativeTime.toString()
            )

            logger.debug(
                "구간 기록 저장 완료: userId=$userId, checkpointId=$checkpointId, " +
                        "구간시간=${segmentDuration}초, 누적시간=${cumulativeTime}초"
            )

        } catch (e: Exception) {
            logger.error("구간 기록 저장 실패: userId=$userId, checkpointId=$checkpointId", e)
            throw e
        }
    }

    /**
     * GPX 트랙에서 모든 웨이포인트를 추출하고 100미터 간격으로 보간 포인트 추가
     *
     * GPX 트랙은 여러 세그먼트로 구성될 수 있으며,
     * 각 세그먼트는 연속된 웨이포인트들을 포함합니다.
     * 두 웨이포인트 사이의 거리가 100미터를 초과하는 경우 중간에 보간 포인트를 추가합니다.
     *
     * @param track GPX 트랙 객체
     * @return 모든 웨이포인트의 리스트 (보간 포인트 포함)
     */
    private fun extractWaypointsFromTrack(track: Track): List<WayPoint> {
        val allWaypoints = mutableListOf<WayPoint>()
        val interpolationInterval = 100.0 // 100미터 간격

        logger.debug("웨이포인트 추출 시작: 세그먼트 개수={}", track.segments.count())

        // 모든 세그먼트를 순회하며 웨이포인트 수집 및 보간
        track.segments.forEach { segment ->
            val segmentPoints = segment.points.toList()
            logger.debug("세그먼트 처리 시작: 원본 포인트 개수={}", segmentPoints.size)

            if (segmentPoints.isNotEmpty()) {
                // 첫 번째 포인트 추가
                allWaypoints.add(segmentPoints.first())

                // 연속된 포인트들 사이에 보간 포인트 추가
                for (i in 1 until segmentPoints.size) {
                    val prevPoint = segmentPoints[i - 1]
                    val currPoint = segmentPoints[i]

                    // 두 포인트 사이의 거리 계산
                    val distance = GeoUtils.calculateDistance(
                        prevPoint.latitude.toDegrees(),
                        prevPoint.longitude.toDegrees(),
                        currPoint.latitude.toDegrees(),
                        currPoint.longitude.toDegrees()
                    )

                    // 거리가 100미터를 초과하는 경우 보간 포인트 추가
                    if (distance > interpolationInterval) {
                        val prevElevation = prevPoint.elevation.map { it.toDouble() }.orElse(null)
                        val currElevation = currPoint.elevation.map { it.toDouble() }.orElse(null)

                        // 보간 포인트 생성
                        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
                            prevPoint.latitude.toDegrees(),
                            prevPoint.longitude.toDegrees(),
                            prevElevation,
                            currPoint.latitude.toDegrees(),
                            currPoint.longitude.toDegrees(),
                            currElevation,
                            interpolationInterval
                        )

                        // 보간 포인트들을 WayPoint 객체로 변환하여 추가
                        interpolatedPoints.forEach { (lat, lon, elev) ->
                            val interpolatedWaypoint = WayPoint.builder()
                                .lat(lat)
                                .lon(lon)
                                .also { builder ->
                                    elev?.let { elevation ->
                                        builder.ele(elevation)
                                    }
                                }
                                .build()
                            allWaypoints.add(interpolatedWaypoint)
                        }

                        logger.debug("보간 포인트 추가: 거리={:.1f}m, 추가된 포인트={}개", distance, interpolatedPoints.size)
                    }

                    // 현재 포인트 추가
                    allWaypoints.add(currPoint)
                }
            }
        }

        logger.info("웨이포인트 추출 완료: 총 {}개 (보간 포인트 포함)", allWaypoints.size)
        return allWaypoints
    }

    /**
     * 경로 통계 계산
     *
     * 웨이포인트 간의 거리를 계산하여 총 거리를 구하고,
     * 고도 변화를 분석하여 상승/하강 고도를 계산합니다.
     *
     * @param waypoints 웨이포인트 리스트
     * @return 경로 통계 데이터 (총 거리, 고도 상승, 고도 하강)
     */
    private fun calculateRouteStatistics(waypoints: List<WayPoint>): RouteStatistics {
        var totalDistance = 0.0
        var elevationGain = 0.0
        var elevationLoss = 0.0
        var previousElevation: Double? = null

        // 연속된 웨이포인트 간의 거리와 고도 변화 계산
        for (i in 1 until waypoints.size) {
            val prev = waypoints[i - 1]
            val curr = waypoints[i]

            // 두 지점 간의 거리 계산 (하버사인 공식 사용)
            val distance = GeoUtils.calculateDistance(
                prev.latitude.toDegrees(),
                prev.longitude.toDegrees(),
                curr.latitude.toDegrees(),
                curr.longitude.toDegrees()
            )
            totalDistance += distance

            // 고도 변화 계산 (유효한 고도 데이터가 있는 경우만)
            val prevElev = prev.elevation.map { it.toDouble() }.orElse(null)
            val currElev = curr.elevation.map { it.toDouble() }.orElse(null)

            // 고도 데이터가 유효한 경우에만 고도 변화 계산 (음수 고도는 유효하지 않은 데이터로 처리)
            if (prevElev != null && currElev != null && prevElev >= 0 && currElev >= 0) {
                previousElevation?.let { validPrevElevation ->
                    if (validPrevElevation >= 0) {
                        val elevationDiff = currElev - validPrevElevation
                        if (elevationDiff > 0) {
                            // 상승 고도 누적
                            elevationGain += elevationDiff
                        } else {
                            // 하강 고도 누적 (절댓값)
                            elevationLoss += kotlin.math.abs(elevationDiff)
                        }
                    }
                }
                previousElevation = currElev
            }
        }

        return RouteStatistics(totalDistance, elevationGain, elevationLoss)
    }

    /**
     * ================================ 실시간 GPS 위치 보정 핵심 메서드 ================================
     * 
     * 이 메서드는 전체 시스템의 핵심 비즈니스 로직으로, 다음과 같은 단계로 GPS 위치를 보정합니다:
     * 
     * **STEP 1: GPS 데이터 검증 및 준비**
     * - 입력받은 GPS 데이터 유효성 검증
     * - Redis에서 해당 이벤트의 코스 데이터 조회
     * - 3차원 칼만 필터 인스턴스 초기화
     * 
     * **STEP 2: 순차적 GPS 필터링**
     * - 각 GPS 포인트에 대해 칼만 필터 적용
     * - GPS 정확도(accuracy)와 속도(speed)를 고려한 가중치 계산
     * - 3차원 좌표(위도, 경도, 고도) 노이즈 제거
     * 
     * **STEP 3: 맵 매칭 (경로 스냅)**
     * - 마지막(최신) GPS 포인트에 대해서만 맵 매칭 수행
     * - 보간된 경로 포인트들과의 거리 및 방향 비교
     * - 최적의 경로상 위치로 GPS 좌표 보정
     * - 경로 진행률 및 시작점으로부터 거리 계산
     * 
     * **STEP 4: 체크포인트 통과 감지**
     * - 이전 위치와 현재 위치 비교하여 체크포인트 경계 통과 여부 판정
     * - 30미터 반경 내 체크포인트 감지
     * - 중복 통과 방지 (이미 통과한 체크포인트 제외)
     * - 구간 시간 및 누적 시간 계산
     * 
     * **STEP 5: 데이터 저장 및 업데이트**
     * - Redis에 보정된 위치 정보 저장
     * - 체크포인트 통과 기록 저장
     * - 이전 위치 정보 업데이트 (다음 요청 시 사용)
     * - 리더보드 Sorted Set 업데이트
     *
     * 
     * @param request GPS 위치 보정 요청 (userId, eventId, eventDetailId, gpsData 포함)
     * @return 보정된 위치, 체크포인트 통과 정보, 매칭 품질 정보 포함한 응답
     * 
     * **핵심 알고리즘:**
     * - 칼만 필터: 연속된 GPS 측정값의 노이즈 제거 및 정확도 향상
     * - 맵 매칭: 거리 기반 + 방향 기반 매칭으로 경로상 최적 위치 탐색
     * - 체크포인트 감지: 이전-현재 위치의 경계 교차 여부로 정확한 통과 시점 판정
     */
    @Transactional(readOnly = true)
    fun correctLocation(request: CorrectLocationRequestDto): CorrectLocationResponseDto {
        logger.info("위치 보정 요청: userId=${request.userId}, eventDetailId=${request.eventDetailId}, GPS 데이터 수신")

        try {
            val userId = request.userId
            val eventId = request.eventId
            val eventDetailId = request.eventDetailId
            val gpsDataList = request.gpsData

            // GPS 데이터 리스트 검증
            if (gpsDataList.isEmpty()) {
                throw IllegalArgumentException("GPS 데이터가 비어있습니다.")
            }

            // 마지막 GPS 데이터를 사용 (가장 최신 데이터)
            val lastGpsData = gpsDataList.last()
            logger.info("GPS 데이터 처리: 총 ${gpsDataList.size}개 중 마지막 데이터 사용")

            // Redis에서 코스 데이터 조회 (없으면 자동 로드)
            val courseData = realtimeLocationService.courseDataService.getCourseDataByEventId(eventDetailId)

            // 3차원 칼만 필터 초기화
            val kalman = KalmanFilter()

            // GPS 포인트들을 순차적으로 칼만 필터에 적용 (정확도 향상을 위해)
            gpsDataList.forEach { gpsData ->
                if (gpsData.accuracy != null && gpsData.accuracy > 0) {
                    val confidence = calculateGpsConfidence(gpsData.accuracy, gpsData.speed)
                    kalman.filterWithWeights(gpsData.lat, gpsData.lng, gpsData.altitude, gpsData.accuracy, confidence)
                } else {
                    kalman.filter(gpsData.lat, gpsData.lng, gpsData.altitude)
                }
            }
            var finalCorrectedLat = 0.0
            var finalCorrectedLng = 0.0
            var finalCorrectedAlt: Double? = null

            // 마지막 GPS 포인트 처리 (맵 매칭용)
            run {
                val lat = lastGpsData.lat
                val lng = lastGpsData.lng
                val alt = lastGpsData.altitude
                val accuracy = lastGpsData.accuracy
                val timestamp = lastGpsData.timestamp

                // 칼만 필터에서 최종 보정된 위치 가져오기
                val filtered = kalman.getCurrentPosition3D() ?: Triple(lat, lng, alt)

                logger.debug(
                    "3차원 칼만 필터 적용: 원본=($lat, $lng, $alt) -> " +
                            "필터링=(${"%.6f".format(filtered.first)}, ${"%.6f".format(filtered.second)}, " +
                            "${filtered.third?.let { "%.2f".format(it) } ?: "null"})")
                logger.debug(
                    "GPS 정확도: ${accuracy}m, 신뢰도: ${
                        if (accuracy != null) calculateGpsConfidence(
                            accuracy,
                            lastGpsData.speed
                        ) else "기본값"
                    }"
                )

                // 경로 매칭 수행
                    val matchResult = if (courseData == null) {
                        // 코스 데이터가 없으면 기본 GPS 위치 사용 (GpxRoute 엔티티 삭제됨)
                        logger.warn("코스 데이터가 Redis에 없음. 기본 GPS 위치 사용: eventDetailId=$eventDetailId")

                        // 기본 매칭 결과 생성
                        MatchResult(
                            matched = false,
                            distanceToRoute = Double.MAX_VALUE,
                            matchedLatitude = filtered.first,
                            matchedLongitude = filtered.second,
                            routeProgress = 0.0
                        )
                    } else {
                        // 코스 데이터가 있는 경우 보간된 포인트를 사용한 정밀 매칭
                        logger.debug("코스 데이터 발견: ${courseData.totalPoints}개 보간 포인트 사용")

                        val matcher = MapMatcher()
                        val result = matcher.matchToInterpolatedRoute(
                            gpsLat = filtered.first,
                            gpsLon = filtered.second,
                            bearing = lastGpsData.heading?.toDouble() ?: 0.0,
                            interpolatedPoints = courseData.interpolatedPoints
                        )

                        // 상세 매칭 정보 로깅
                        logger.info("GPS 매칭 결과:")
                        logger.info("- 매칭 성공: ${result.matched}")
                        logger.info("- 경로까지 거리: ${"%.2f".format(result.distanceToRoute)}m")
                        logger.info("- 매칭 점수: ${"%.4f".format(result.matchScore)}")
                        logger.info("- 현재 이동 방향: ${result.currentBearing}°")
                        logger.info("- 경로 방향: ${result.routeBearing}°")
                        logger.info("- 방향 차이: ${"%.1f".format(result.bearingDifference ?: 0.0)}°")
                        logger.info("- 경로 진행률: ${"%.2f".format(result.routeProgress * 100)}%")
                        logger.info("- 시작점으로부터 거리: ${"%.2f".format(result.distanceFromStart)}m")

                        result.nearestGpxPoint?.let { nearest ->
                            logger.info("가장 가까운 GPX 포인트:")
                            logger.info("- 위치: (${"%.6f".format(nearest.latitude)}, ${"%.6f".format(nearest.longitude)})")
                            logger.info("- 거리: ${"%.2f".format(nearest.distanceToPoint)}m")
                            logger.info("- 경로 방향: ${nearest.routeBearing}°")
                        }

                        result
                    }

                    finalCorrectedLat = matchResult.matchedLatitude
                    finalCorrectedLng = matchResult.matchedLongitude
                    finalCorrectedAlt = filtered.third // 칼만 필터에서 보정된 고도 사용

                    logger.info(
                        "최종 맵매칭 완료: 보정위치=(${"%.6f".format(finalCorrectedLat)}, ${"%.6f".format(finalCorrectedLng)}, " +
                                "${finalCorrectedAlt?.let { "%.2f".format(it) } ?: "null"}m)")

                    // 실시간 위치 저장
                    try {
                        realtimeLocationService.saveParticipantLocation(
                            userId = userId,
                            eventId = eventId,
                            eventDetailId = eventDetailId,
                            latitude = finalCorrectedLat,
                            longitude = finalCorrectedLng,
                            altitude = finalCorrectedAlt,
                            speed = lastGpsData.speed,
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        logger.warn("실시간 위치 저장 실패 (계속 진행): userId=$userId", e)
                    }
            }

            logger.info(
                "위치 보정 완료: 1개 포인트 처리, " +
                        "최종 보정위치=(${"%.6f".format(finalCorrectedLat)}, ${"%.6f".format(finalCorrectedLng)}, " +
                        "${finalCorrectedAlt?.let { "%.2f".format(it) } ?: "null"}m)")

            // 매칭 결과에서 상세 정보 추출
            val kalmanForFinal = KalmanFilter()
            if (lastGpsData.accuracy != null && lastGpsData.accuracy > 0) {
                val confidence = calculateGpsConfidence(lastGpsData.accuracy, lastGpsData.speed)
                kalmanForFinal.filterWithWeights(
                    lastGpsData.lat,
                    lastGpsData.lng,
                    lastGpsData.altitude,
                    lastGpsData.accuracy,
                    confidence
                )
            } else {
                kalmanForFinal.filter(lastGpsData.lat, lastGpsData.lng, lastGpsData.altitude)
            }
            val filtered3D = kalmanForFinal.getCurrentPosition3D()
            val filtered = if (filtered3D != null) Pair(filtered3D.first, filtered3D.second) else Pair(
                lastGpsData.lat,
                lastGpsData.lng
            )

            val finalMatchResult = if (courseData != null) {
                val matcher = MapMatcher()
                matcher.matchToInterpolatedRoute(
                    gpsLat = filtered.first,
                    gpsLon = filtered.second,
                    bearing = lastGpsData.heading?.toDouble() ?: 0.0,
                    interpolatedPoints = courseData.interpolatedPoints
                )
            } else {
                null
            }

            // 체크포인트 통과 감지 및 기록
            val currentTime = lastGpsData.timestamp.let { timeStr ->
                try {
                    // 먼저 Unix timestamp로 파싱 시도
                    if (timeStr.matches(Regex("^\\d{10,13}$"))) {
                        // Unix timestamp (10자리 초, 13자리 밀리초)
                        val timestamp = timeStr.toLong()
                        if (timestamp > 1000000000000L) {
                            // 밀리초 단위인 경우 초로 변환
                            timestamp / 1000
                        } else {
                            timestamp
                        }
                    } else {
                        // ISO 8601 형식 파싱
                        val zonedDateTime = ZonedDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
                        zonedDateTime.toInstant().epochSecond
                    }
                } catch (e: Exception) {
                    logger.warn("GPS 시간 파싱 실패, 현재 시간 사용: $timeStr", e)
                    Instant.now().epochSecond
                }
            }

            // 체크포인트 통과 감지
            val checkpointCrossings = detectCheckpointCrossings(
                userId = userId,
                eventId = request.eventId,
                eventDetailId = eventDetailId,
                currentLat = finalCorrectedLat,
                currentLng = finalCorrectedLng,
                currentTime = currentTime,
                routeProgress = finalMatchResult?.routeProgress,
                distanceFromStart = finalMatchResult?.distanceFromStart
            )

            // 체크포인트 통과 정보 저장
            val checkpointReaches = saveCheckpointCrossings(
                crossings = checkpointCrossings,
                userId = userId,
                eventId = request.eventId,
                eventDetailId = eventDetailId
            )

            // 현재 위치를 이전 위치로 저장 (다음 요청 시 사용)
            try {
                checkpointTimesService.savePreviousLocation(
                    userId = userId.toString(),
                    eventId = request.eventId.toString(),
                    eventDetailId = eventDetailId.toString(),
                    latitude = finalCorrectedLat,
                    longitude = finalCorrectedLng,
                    altitude = finalCorrectedAlt,
                    timestamp = currentTime,
                    distanceFromStart = finalMatchResult?.distanceFromStart
                )
            } catch (e: Exception) {
                logger.warn("이전 위치 저장 실패 (계속 진행): userId=$userId", e)
            }

            // 보정된 위치 정보를 Redis에 업데이트 (location:{userId}:{eventDetailId})
            try {
                updateCorrectedLocationInRedis(
                    userId = userId,
                    eventId = request.eventId,
                    eventDetailId = eventDetailId,
                    rawGpsData = lastGpsData,
                    correctedLat = finalCorrectedLat,
                    correctedLng = finalCorrectedLng,
                    correctedAlt = finalCorrectedAlt,
                    timestamp = currentTime,
                    distanceFromStart = finalMatchResult?.distanceFromStart
                )
            } catch (e: Exception) {
                logger.warn("보정 위치 Redis 업데이트 실패 (계속 진행): userId=$userId", e)
            }

            return CorrectLocationResponseDto(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId,
                latitude = finalCorrectedLat,
                longitude = finalCorrectedLng,
                altitude = finalCorrectedAlt,
                speed = lastGpsData.speed,
                timestamp = lastGpsData.timestamp
            )

        } catch (e: NoSuchElementException) {
            logger.error("위치 보정 실패 - 대회 데이터 없음: eventDetailId=${request.eventDetailId}", e)
            throw e
        } catch (e: IllegalArgumentException) {
            logger.error("위치 보정 실패 - 잘못된 파라미터: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error("위치 보정 실패 - 예상치 못한 오류: userId=${request.userId}, eventDetailId=${request.eventDetailId}", e)
            throw RuntimeException("위치 보정 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * GPS 정확도와 속도를 기반으로 신뢰도를 계산합니다.
     *
     * @param accuracy GPS 정확도 (미터)
     * @param speed GPS 속도 (미터/초)
     * @return 신뢰도 (0.1 ~ 1.0)
     */
    private fun calculateGpsConfidence(accuracy: Float, speed: Float?): Double {
        // 기본 정확도 기반 신뢰도 (정확도가 좋을수록 높은 신뢰도)
        val accuracyConfidence = when {
            accuracy <= 3.0 -> 1.0    // 매우 정확 (3m 이하)
            accuracy <= 5.0 -> 0.9    // 정확 (5m 이하)
            accuracy <= 10.0 -> 0.7   // 보통 (10m 이하)
            accuracy <= 20.0 -> 0.5   // 낮음 (20m 이하)
            else -> 0.3               // 매우 낮음 (20m 초과)
        }

        // 속도 기반 보정 (정지 상태에서는 신뢰도 감소)
        val speedConfidence = if (speed != null) {
            when {
                speed < 0.5 -> 0.8     // 정지 상태 (신뢰도 약간 감소)
                speed < 1.0 -> 0.9     // 느린 이동
                speed < 5.0 -> 1.0     // 정상 이동
                speed < 15.0 -> 0.95   // 빠른 이동
                else -> 0.8            // 매우 빠른 이동 (GPS 오차 가능성)
            }
        } else {
            1.0 // 속도 정보 없음
        }

        // 최종 신뢰도 계산 (가중평균)
        val finalConfidence = (accuracyConfidence * 0.7 + speedConfidence * 0.3)

        // 0.1 ~ 1.0 범위로 제한
        return finalConfidence.coerceIn(0.1, 1.0)
    }

    /**
     * ================================ GPS 위치 및 추가 정보 Redis 저장 ================================
     * 
     * **저장되는 GPS 데이터:**
     * 1. **원본 GPS 정보**
     *    - 위도/경도: GPS 센서에서 직접 수신한 원본 좌표
     *    - 고도: GPS 센서 고도값 (해수면 기준 미터)
     *    - 정확도: GPS 신호 정확도 (미터, 작을수록 정확)
     *    - 속도: GPS 센서 속도 (km/h 또는 m/s)
     *    - 방향: GPS 이동 방향 (도, 0-359)
     *    - 수신 시간: Unix timestamp
     * 
     * 2. **보정된 GPS 정보**
     *    - 위도/경도: 칼만 필터 + 맵 매칭으로 보정된 좌표
     *    - 고도: 칼만 필터로 노이즈 제거된 고도
     *    - 이동 거리: 누적 이동 거리 (미터)
     *    - 누적 시간: 총 소요 시간 (초)
     * 
     * 3. **추가 메타 정보**
     *    - 마지막 업데이트 시간
     *    - 시작점으로부터 직선 거리
     * 
     * **Redis 저장 구조: Hash**
     * - Key: location:{userId}:{eventDetailId}
     * - Fields: 각종 GPS 및 보정 정보
     * - TTL: 24시간
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param rawGpsData 원본 GPS 데이터 (위도, 경도, 고도, 정확도, 속도, 방향 포함)
     * @param correctedLat 보정된 위도
     * @param correctedLng 보정된 경도
     * @param correctedAlt 보정된 고도 (칼만 필터 적용)
     * @param timestamp 타임스탬프 (Unix seconds)
     * @param distanceFromStart 시작점으로부터 거리 (미터)
     */
    private fun updateCorrectedLocationInRedis(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        rawGpsData: CorrectLocationRequestDto.GpsLocationData,
        correctedLat: Double,
        correctedLng: Double,
        correctedAlt: Double?,
        timestamp: Long,
        distanceFromStart: Double?
    ) {
        try {
            val redisKey = "gps:$eventId:$eventDetailId:$userId"

            // 기존 데이터 조회 (누적 계산용)
            val existingData = getCorrectedLocationFromRedis(userId, eventId, eventDetailId)

            // 이동 거리 계산
            val distanceCovered = if (existingData != null) {
                val moveDistance = GeoUtils.calculateDistance(
                    existingData.correctedLatitude,
                    existingData.correctedLongitude,
                    correctedLat,
                    correctedLng
                )
                existingData.distanceCovered + moveDistance
            } else {
                distanceFromStart ?: 0.0
            }

            // 누적 시간 계산
            val cumulativeTime = if (existingData != null) {
                val timeDiff = timestamp - existingData.rawTime
                if (timeDiff > 0) {
                    existingData.cumulativeTime + timeDiff
                } else {
                    existingData.cumulativeTime
                }
            } else {
                0L // 첫 번째 위치인 경우
            }

            // 위치 데이터 생성
            val locationData = LocationData(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId,
                rawLatitude = rawGpsData.lat,
                rawLongitude = rawGpsData.lng,
                rawAltitude = rawGpsData.altitude,
                rawAccuracy = rawGpsData.accuracy,
                rawTime = timestamp,
                rawSpeed = rawGpsData.speed,
                correctedLatitude = correctedLat,
                correctedLongitude = correctedLng,
                correctedAltitude = correctedAlt,
                lastUpdated = Instant.now().epochSecond,
                heading = rawGpsData.heading,
                distanceCovered = distanceCovered,
                cumulativeTime = cumulativeTime
            )

            // Redis Hash에 GPS 데이터 저장 (원본 + 보정된 정보)
            val hashOps = redisTemplate.opsForHash<String, String>()
            
            // 기본 식별 정보
            hashOps.put(redisKey, "userId", locationData.userId.toString())
            hashOps.put(redisKey, "eventId", locationData.eventId.toString())
            hashOps.put(redisKey, "eventDetailId", locationData.eventDetailId.toString())
            
            // 원본 GPS 데이터 (센서에서 직접 수신한 값들)
            hashOps.put(redisKey, "latitude", locationData.rawLatitude.toString())
            hashOps.put(redisKey, "longitude", locationData.rawLongitude.toString())
            hashOps.put(redisKey, "speed", locationData.rawSpeed?.toString() ?: "null")
            hashOps.put(redisKey, "heading", locationData.heading?.toString() ?: "null")
            
            // 보정된 GPS 데이터 (칼만 필터 + 맵 매칭 적용)
            hashOps.put(redisKey, "latitude", locationData.correctedLatitude.toString())
            hashOps.put(redisKey, "longitude", locationData.correctedLongitude.toString())
            hashOps.put(redisKey, "altitude", locationData.correctedAltitude?.toString() ?: "null")
            
            // 계산된 메타 정보
            hashOps.put(redisKey, "created", locationData.lastUpdated.toString())

            // TTL 설정 (24시간)
            redisTemplate.expire(redisKey, 24, java.util.concurrent.TimeUnit.HOURS)

            logger.info(
                "보정 위치 Redis 업데이트 완료: userId=$userId, eventDetailId=$eventDetailId"
            )
            logger.info(
                "- 원본 GPS: 위치=(${String.format("%.6f", rawGpsData.lat)}, ${String.format("%.6f", rawGpsData.lng)}), " +
                        "고도=${rawGpsData.altitude?.let { String.format("%.2f", it) } ?: "null"}m, " +
                        "속도=${rawGpsData.speed?.let { String.format("%.2f", it) } ?: "null"}km/h, " +
                        "정확도=${rawGpsData.accuracy?.let { String.format("%.1f", it) } ?: "null"}m, " +
                        "방향=${rawGpsData.heading?.let { String.format("%.0f", it) } ?: "null"}°"
            )
            logger.info(
                "- 보정 결과: 위치=(${String.format("%.6f", correctedLat)}, ${String.format("%.6f", correctedLng)}), " +
                        "고도=${correctedAlt?.let { String.format("%.2f", it) } ?: "null"}m, " +
                        "이동거리=${String.format("%.2f", distanceCovered)}m, " +
                        "누적시간=${cumulativeTime}초, " +
                        "시작점거리=${distanceFromStart?.let { String.format("%.2f", it) } ?: "null"}m"
            )

            // 리더보드 Sorted Set 업데이트
            try {
                val farthestCheckpointInfo = getFarthestCheckpointInfo(userId, eventId, eventDetailId)

                if (farthestCheckpointInfo != null) {
                    updateLeaderboardSortedSet(
                        eventDetailId = eventDetailId,
                        userId = userId,
                        cpIndex = farthestCheckpointInfo.cpIndex,
                        cumulativeTime = farthestCheckpointInfo.cumulativeTime
                    )
                } else {
                    // 체크포인트를 아직 통과하지 않은 경우 시작점 기준으로 업데이트
                    updateLeaderboardSortedSet(
                        eventDetailId = eventDetailId,
                        userId = userId,
                        cpIndex = 0, // 시작점
                        cumulativeTime = cumulativeTime
                    )
                }
            } catch (e: Exception) {
                logger.warn("리더보드 업데이트 실패 (계속 진행): userId=$userId, eventDetailId=$eventDetailId", e)
            }

        } catch (e: Exception) {
            logger.error("보정 위치 Redis 업데이트 실패: userId=$userId, eventDetailId=$eventDetailId", e)
        }
    }

    /**
     * ================================ Redis GPS 위치 정보 조회 ================================
     * 
     * **조회되는 GPS 데이터:**
     * - 원본 GPS: 위도, 경도, 고도, 정확도, 속도, 방향, 수신시간
     * - 보정 GPS: 위도, 경도, 고도 (칼만 필터 + 맵 매칭 적용)
     * - 계산 정보: 이동거리, 누적시간, 시작점거리, 마지막 업데이트
     * 
     * **Redis 구조:**
     * - Key: location:{userId}:{eventDetailId}
     * - Type: Hash
     * - Fields: 각종 GPS 및 보정 데이터
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 위치 데이터 (LocationData) 또는 null (데이터 없음)
     */
    private fun getCorrectedLocationFromRedis(
        userId: Long,
        eventId: Long,
        eventDetailId: Long
    ): LocationData? {
        return try {
            val redisKey = "location:$userId:$eventDetailId"
            val hashOps = redisTemplate.opsForHash<String, String>()

            // Redis Hash에서 데이터 조회
            val data = hashOps.entries(redisKey)

            if (data.isEmpty()) {
                logger.debug("Redis에서 위치 데이터 없음: userId=$userId, eventDetailId=$eventDetailId")
                return null
            }

            val locationData = LocationData(
                userId = data["userId"]?.toLongOrNull() ?: userId,
                eventId = data["eventId"]?.toLongOrNull() ?: eventId,
                eventDetailId = data["eventDetailId"]?.toLongOrNull() ?: eventDetailId,
                rawLatitude = data["rawLatitude"]?.toDoubleOrNull() ?: 0.0,
                rawLongitude = data["rawLongitude"]?.toDoubleOrNull() ?: 0.0,
                rawAltitude = data["rawAltitude"]?.takeIf { it != "null" }?.toDoubleOrNull(),
                rawAccuracy = data["rawAccuracy"]?.takeIf { it != "null" }?.toFloatOrNull(),
                rawTime = data["rawTime"]?.toLongOrNull() ?: 0L,
                rawSpeed = data["rawSpeed"]?.takeIf { it != "null" }?.toFloatOrNull(),
                correctedLatitude = data["correctedLatitude"]?.toDoubleOrNull() ?: 0.0,
                correctedLongitude = data["correctedLongitude"]?.toDoubleOrNull() ?: 0.0,
                correctedAltitude = data["correctedAltitude"]?.takeIf { it != "null" }?.toDoubleOrNull(),
                lastUpdated = data["lastUpdated"]?.toLongOrNull() ?: 0L,
                heading = data["heading"]?.takeIf { it != "null" }?.toFloatOrNull(),
                distanceCovered = data["distanceCovered"]?.toDoubleOrNull() ?: 0.0,
                cumulativeTime = data["cumulativeTime"]?.toLongOrNull() ?: 0L
            )

            logger.debug(
                "Redis에서 위치 데이터 조회 성공: userId=$userId, eventDetailId=$eventDetailId" +
                        " - 원본GPS: 속도=${locationData.rawSpeed}km/h, 고도=${locationData.rawAltitude}m" +
                        " - 보정GPS: 고도=${locationData.correctedAltitude}m, 이동거리=${locationData.distanceCovered}m"
            )

            locationData

        } catch (e: Exception) {
            logger.error("보정 위치 Redis 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            null
        }
    }

    /**
     * 참가자의 가장 먼 체크포인트 정보 조회
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 가장 먼 체크포인트 정보 또는 null
     */
    private fun getFarthestCheckpointInfo(
        userId: Long,
        eventId: Long,
        eventDetailId: Long
    ): FarthestCheckpointInfo? {
        return try {
            // 체크포인트 통과 시간 조회
            val allPassTimes = checkpointTimesService.getAllCheckpointPassTimes(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId
            )

            if (allPassTimes.isEmpty()) {
                return null
            }

            // GPX 파싱 데이터에서 체크포인트 정보 조회
            val gpxParsingData = gpxParsingRedisService.getGpxParsingData(eventId, eventDetailId)

            if (!gpxParsingData.success || gpxParsingData.points.isEmpty()) {
                logger.warn("GPX 파싱 데이터가 없어 가장 먼 체크포인트 조회 불가: eventId=$eventId, eventDetailId=$eventDetailId")
                return null
            }

            // 체크포인트 정보 매핑 (cpId -> cpIndex)
            val checkpointMap = gpxParsingData.points
                .filter { it.type in listOf("start", "checkpoint", "finish") && it.cpId != null && it.cpIndex != null }
                .associateBy({ it.cpId!! }, { it.cpIndex!! })

            // 통과한 체크포인트 중 가장 높은 cpIndex 찾기
            var farthestCpIndex = -1
            var farthestCpId = ""
            var farthestPassTime = 0L

            allPassTimes.forEach { (cpId, passTime) ->
                val cpIndex = checkpointMap[cpId]
                if (cpIndex != null && cpIndex > farthestCpIndex) {
                    farthestCpIndex = cpIndex
                    farthestCpId = cpId
                    farthestPassTime = passTime
                }
            }

            if (farthestCpIndex == -1) {
                return null
            }

            // 해당 체크포인트까지의 누적 시간 계산
            val cumulativeTime = calculateCumulativeTimeToCheckpoint(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId,
                targetCpId = farthestCpId,
                targetPassTime = farthestPassTime
            )

            FarthestCheckpointInfo(
                cpIndex = farthestCpIndex,
                cpId = farthestCpId,
                cumulativeTime = cumulativeTime
            )

        } catch (e: Exception) {
            logger.error("가장 먼 체크포인트 정보 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            null
        }
    }

    /**
     * 특정 체크포인트까지의 누적 시간 계산
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param targetCpId 대상 체크포인트 ID
     * @param targetPassTime 대상 체크포인트 통과 시간
     * @return 누적 시간 (초)
     */
    private fun calculateCumulativeTimeToCheckpoint(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        targetCpId: String,
        targetPassTime: Long
    ): Long {
        return try {
            // 구간 기록에서 누적 시간 조회
            val segmentKey = "participantSegmentRecords:$userId:$eventId:$eventDetailId"
            val cumulativeField = "${targetCpId}_cumulative"

            val cumulativeTimeStr = redisTemplate.opsForHash<String, String>().get(segmentKey, cumulativeField)

            if (cumulativeTimeStr != null) {
                cumulativeTimeStr.toLongOrNull() ?: 0L
            } else {
                // 구간 기록이 없으면 이벤트 시작 시간부터 계산
                val eventStartTime = getEventStartTime(eventId, eventDetailId)
                if (eventStartTime != null) {
                    maxOf(0L, targetPassTime - eventStartTime)
                } else {
                    0L
                }
            }

        } catch (e: Exception) {
            logger.error("누적 시간 계산 실패: userId=$userId, targetCpId=$targetCpId", e)
            0L
        }
    }

    /**
     * 리더보드 스코어 계산
     *
     * 공식: (cpIndex * MAX_EXPECTED_SEGMENT_TIME) + cumulativeTime
     * - cpIndex가 높을수록 우선순위
     * - 동일 cpIndex 내에서는 시간이 짧을수록 우선순위
     *
     * @param cpIndex 체크포인트 인덱스
     * @param cumulativeTime 누적 시간 (초)
     * @return 리더보드 스코어
     */
    private fun calculateLeaderboardScore(cpIndex: Int, cumulativeTime: Long): Double {
        // cpIndex가 높을수록 낮은 점수 (높은 순위)
        // 동일 cpIndex 내에서는 cumulativeTime이 짧을수록 낮은 점수 (높은 순위)
        return (cpIndex * MAX_EXPECTED_SEGMENT_TIME + cumulativeTime).toDouble()
    }

    /**
     * Redis Sorted Set 리더보드 업데이트
     *
     * @param eventDetailId 이벤트 상세 ID
     * @param userId 사용자 ID
     * @param cpIndex 체크포인트 인덱스
     * @param cumulativeTime 누적 시간 (초)
     */
    private fun updateLeaderboardSortedSet(
        eventDetailId: Long,
        userId: Long,
        cpIndex: Int,
        cumulativeTime: Long
    ) {
        try {
            val leaderboardKey = "leaderboard:$eventDetailId"
            val score = calculateLeaderboardScore(cpIndex, cumulativeTime)

            // Redis Sorted Set에 업데이트
            redisTemplate.opsForZSet().add(leaderboardKey, userId.toString(), score)

            // TTL 설정 (7일)
            redisTemplate.expire(leaderboardKey, 7, TimeUnit.DAYS)

            logger.info(
                "리더보드 Sorted Set 업데이트: eventDetailId=$eventDetailId, userId=$userId, " +
                        "cpIndex=$cpIndex, cumulativeTime=${cumulativeTime}초, score=$score"
            )

        } catch (e: Exception) {
            logger.error("리더보드 Sorted Set 업데이트 실패: eventDetailId=$eventDetailId, userId=$userId", e)
        }
    }
}

/**
 * 경로 통계 데이터 클래스
 *
 * GPX 경로의 통계 정보를 담는 데이터 클래스입니다.
 *
 * @property totalDistance 총 거리 (미터)
 * @property elevationGain 총 고도 상승 (미터)
 * @property elevationLoss 총 고도 하강 (미터)
 */
private data class RouteStatistics(
    val totalDistance: Double,
    val elevationGain: Double,
    val elevationLoss: Double
) 
