package com.sponovation.runtrack.service

import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.algorithm.KalmanFilter
import com.sponovation.runtrack.algorithm.MapMatcher
import com.sponovation.runtrack.util.GeoUtils
import com.sponovation.runtrack.repository.CheckpointRepository
import com.sponovation.runtrack.repository.CourseRepository
import com.sponovation.runtrack.domain.Checkpoint
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.WayPoint
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * GPX 파일 처리 및 경로 관리 서비스
 *
 * 현재 도메인 엔티티들이 삭제되어 데이터베이스 저장 기능은 비활성화됨
 * Redis 기반 코스 데이터 저장만 지원
 */
@Service
@Transactional(readOnly = true)
class GpxService(
    private val checkpointRepository: CheckpointRepository,
    private val realtimeLocationService: RealtimeLocationService,
    private val courseRepository: CourseRepository,
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
     * Redis에 저장할 위치 데이터 클래스
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
     * 웨이포인트 통과 감지 로직
     *
     * 이전 위치와 현재 위치를 비교하여 체크포인트 경계를 통과했는지 확인합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param currentLat 현재 위도
     * @param currentLng 현재 경도
     * @param currentTime 현재 시간 (Unix Timestamp)
     * @param routeProgress 경로 진행률 (0.0 ~ 1.0)
     * @param distanceFromStart 시작점으로부터 거리 (미터)
     * @return 새로 통과한 체크포인트 리스트
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
     * GPX 파일을 파싱하여 DB와 Redis에 통합 저장 (최적화된 단일 파싱)
     *
     * 이 메서드는 GPX 파일을 한 번만 파싱하여 다음 작업을 수행합니다:
     * 1. GPX 파일 파싱 및 100미터 간격 보간
     * 2. 데이터베이스에 GpxRoute 및 GpxWaypoint 저장
     * 3. Redis에 CourseData 저장 (실시간 위치 보정용)
     * 4. 체크포인트 자동 생성
     *
     * @param file 업로드된 GPX 파일
     * @param routeName 경로명
     * @param description 경로 설명
     * @param eventDetailId 이벤트 상세 ID (Redis 저장용)
     * @return 통합 업로드 결과
     */
    fun parseAndSaveGpxFileWithCourseData(
        file: MultipartFile,
        routeName: String,
        description: String,
        eventDetailId: Long
    ): GpxUploadResult {
        logger.info(
            "GPX 파일 통합 처리 시작: 파일명={}, 크기={} bytes, 경로명={}, 이벤트ID={}",
            file.originalFilename, file.size, routeName, eventDetailId
        )

        val startTime = System.currentTimeMillis()

        // 파일 크기 및 형식 검증
        if (file.isEmpty) {
            logger.error("빈 파일 업로드 시도: 파일명={}", file.originalFilename)
            throw IllegalArgumentException("업로드된 파일이 비어있습니다")
        }

        if (file.size > 10 * 1024 * 1024) { // 10MB 제한
            logger.error("파일 크기 초과: 파일명={}, 크기={} bytes", file.originalFilename, file.size)
            throw IllegalArgumentException("파일 크기가 너무 큽니다 (최대 10MB)")
        }

        logger.debug("파일 검증 완료: 파일명={}", file.originalFilename)

        // 임시 파일 생성 (GPX 라이브러리가 파일 경로를 필요로 하기 때문)
        val tempFile = try {
            java.io.File.createTempFile("gpx", ".gpx")
        } catch (e: Exception) {
            logger.error("임시 파일 생성 실패: {}", e.message, e)
            throw RuntimeException("임시 파일 생성에 실패했습니다: ${e.message}", e)
        }

        logger.debug("임시 파일 생성 완료: {}", tempFile.absolutePath)

        try {
            // 업로드된 파일을 임시 파일로 복사
            file.transferTo(tempFile)
            logger.debug("파일 복사 완료: {} -> {}", file.originalFilename, tempFile.absolutePath)

            // GPX 파일 파싱
            val gpx = try {
                logger.debug("GPX 파일 파싱 시작")
                GPX.read(tempFile.toPath())
            } catch (e: Exception) {
                logger.error("GPX 파일 파싱 실패: 파일명={}, 오류={}", file.originalFilename, e.message, e)
                throw IllegalArgumentException("GPX 파일 파싱에 실패했습니다. 올바른 GPX 형식인지 확인해주세요: ${e.message}", e)
            }

            logger.debug("GPX 파일 파싱 완료")

            // 트랙 데이터 검증
            val tracks = gpx.tracks.toList()
            if (tracks.isEmpty()) {
                logger.error("트랙 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 트랙 데이터가 없습니다")
            }

            logger.debug("트랙 개수: {}", tracks.size)

            // 첫 번째 트랙 선택
            val track = tracks.first()
            val segments = track.segments.toList()
            if (segments.isEmpty()) {
                logger.error("세그먼트 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 세그먼트 데이터가 없습니다")
            }

            logger.debug("세그먼트 개수: {}", segments.size)

            // 트랙에서 웨이포인트 추출 (100미터 간격 보간 포함)
            val waypoints = extractWaypointsFromTrack(track)
            logger.info("웨이포인트 추출 완료: {}개 (보간 포함)", waypoints.size)

            // 웨이포인트 최소 개수 검증
            if (waypoints.size < 2) {
                logger.error("웨이포인트 개수 부족: {}개 (최소 2개 필요)", waypoints.size)
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 웨이포인트가 필요합니다 (현재: ${waypoints.size}개)")
            }

            // 경로 통계 계산 (총 거리, 고도 변화 등)
            logger.debug("경로 통계 계산 시작")
            val routeStats = calculateRouteStatistics(waypoints)
            logger.info(
                "경로 통계 계산 완료: 총거리={:.1f}m, 고도상승={:.1f}m, 고도하강={:.1f}m",
                routeStats.totalDistance, routeStats.elevationGain, routeStats.elevationLoss
            )

            // 최소 거리 검증
            if (routeStats.totalDistance < 10.0) { // 10미터 미만
                logger.error("경로 거리 부족: {:.1f}m (최소 10m 필요)", routeStats.totalDistance)
                throw IllegalArgumentException(
                    "경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${
                        String.format(
                            "%.1f",
                            routeStats.totalDistance
                        )
                    }m)"
                )
            }

            // 1. GPX 경로 엔티티 생성 및 저장
            // logger.debug("GPX 경로 엔티티 저장 시작")
            // val gpxRoute = gpxRouteRepository.save(
            //     GpxRoute(
            //         name = routeName,
            //         description = description,
            //         totalDistance = routeStats.totalDistance,
            //         totalElevationGain = routeStats.elevationGain,
            //         totalElevationLoss = routeStats.elevationLoss,
            //         createdAt = LocalDateTime.now()
            //     )
            // )
            // logger.info("GPX 경로 저장 완료: ID={}, 이름={}", gpxRoute.id, gpxRoute.name)

            // 2. 웨이포인트 데이터를 DB에 저장
            // logger.debug("웨이포인트 저장 시작: {}개", waypoints.size)
            // saveWaypoints(gpxRoute, waypoints)
            // logger.info("웨이포인트 저장 완료: {}개", waypoints.size)

            // 3. 자동 체크포인트 생성
            // logger.debug("체크포인트 자동 생성 시작")
            // generateAutoCheckpoints(gpxRoute, waypoints)

            // 4. Redis에 코스 데이터 저장 (파싱된 웨이포인트 재사용)
            logger.debug("Redis에 코스 데이터 저장 시작")
            val courseId = courseDataService.saveFromParsedWaypoints(
                eventId = eventDetailId,
                fileName = file.originalFilename ?: "unknown.gpx",
                waypoints = waypoints
            )
            logger.info("Redis 코스 데이터 저장 완료: courseId={}", courseId)

            val processingTime = System.currentTimeMillis() - startTime
            logger.info(
                "GPX 파일 통합 처리 완료: 처리시간={}ms, 경로ID={}, 코스ID={}, 웨이포인트={}개, 총거리={:.1f}m",
                processingTime, "N/A", courseId, waypoints.size, routeStats.totalDistance
            )

            return GpxUploadResult(
                gpxRoute = null, // GpxRoute 엔티티가 삭제되어 null 반환
                courseId = courseId,
                totalInterpolatedPoints = waypoints.size,
                createdAt = LocalDateTime.now().toString()
            )

        } catch (e: IllegalArgumentException) {
            logger.warn("GPX 파일 처리 실패 (검증 오류): {}", e.message)
            throw e
        } catch (e: Exception) {
            logger.error("GPX 파일 처리 중 예상치 못한 오류 발생: {}", e.message, e)
            throw RuntimeException("GPX 파일 처리 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 임시 파일 정리
            try {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (deleted) {
                        logger.debug("임시 파일 삭제 완료: {}", tempFile.absolutePath)
                    } else {
                        logger.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                logger.warn("임시 파일 삭제 중 오류 발생: {}", e.message)
            }
        }
    }

    /**
     * GPX 파일을 파싱하여 경로 데이터를 저장
     *
     * @param file 업로드된 GPX 파일
     * @param routeName 경로명
     * @param description 경로 설명
     * @return 저장된 GpxRoute 엔티티
     * @throws IllegalArgumentException GPX 파일에 트랙 데이터가 없는 경우
     */
    fun parseAndSaveGpxFile(file: MultipartFile, routeName: String, description: String): Map<String, Any> {
        logger.info(
            "GPX 파일 파싱 시작: 파일명={}, 크기={} bytes, 경로명={}",
            file.originalFilename, file.size, routeName
        )

        val startTime = System.currentTimeMillis()

        // 파일 크기 및 형식 검증
        if (file.isEmpty) {
            logger.error("빈 파일 업로드 시도: 파일명={}", file.originalFilename)
            throw IllegalArgumentException("업로드된 파일이 비어있습니다")
        }

        if (file.size > 10 * 1024 * 1024) { // 10MB 제한
            logger.error("파일 크기 초과: 파일명={}, 크기={} bytes", file.originalFilename, file.size)
            throw IllegalArgumentException("파일 크기가 너무 큽니다 (최대 10MB)")
        }

        logger.debug("파일 검증 완료: 파일명={}", file.originalFilename)

        // 임시 파일 생성 (GPX 라이브러리가 파일 경로를 필요로 하기 때문)
        val tempFile = try {
            java.io.File.createTempFile("gpx", ".gpx")
        } catch (e: Exception) {
            logger.error("임시 파일 생성 실패: {}", e.message, e)
            throw RuntimeException("임시 파일 생성에 실패했습니다: ${e.message}", e)
        }

        logger.debug("임시 파일 생성 완료: {}", tempFile.absolutePath)

        try {
            // 업로드된 파일을 임시 파일로 복사
            file.transferTo(tempFile)
            logger.debug("파일 복사 완료: {} -> {}", file.originalFilename, tempFile.absolutePath)

            // GPX 파일 파싱
            val gpx = try {
                logger.debug("GPX 파일 파싱 시작")
                GPX.read(tempFile.toPath())
            } catch (e: Exception) {
                logger.error("GPX 파일 파싱 실패: 파일명={}, 오류={}", file.originalFilename, e.message, e)
                throw IllegalArgumentException("GPX 파일 파싱에 실패했습니다. 올바른 GPX 형식인지 확인해주세요: ${e.message}", e)
            }

            logger.debug("GPX 파일 파싱 완료")

            // 트랙 데이터 검증
            val tracks = gpx.tracks.toList()
            if (tracks.isEmpty()) {
                logger.error("트랙 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 트랙 데이터가 없습니다")
            }

            logger.debug("트랙 개수: {}", tracks.size)

            // 첫 번째 트랙 선택 (일반적으로 GPX 파일은 하나의 트랙을 포함)
            val track = tracks.first()
            val segments = track.segments.toList()
            if (segments.isEmpty()) {
                logger.error("세그먼트 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 세그먼트 데이터가 없습니다")
            }

            logger.debug("세그먼트 개수: {}", segments.size)

            // 트랙에서 웨이포인트 추출
            val waypoints = extractWaypointsFromTrack(track)
            logger.info("웨이포인트 추출 완료: {}개", waypoints.size)

            // 웨이포인트 최소 개수 검증
            if (waypoints.size < 2) {
                logger.error("웨이포인트 개수 부족: {}개 (최소 2개 필요)", waypoints.size)
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 웨이포인트가 필요합니다 (현재: ${waypoints.size}개)")
            }

            // 경로 통계 계산 (총 거리, 고도 변화 등)
            logger.debug("경로 통계 계산 시작")
            val routeStats = calculateRouteStatistics(waypoints)
            logger.info(
                "경로 통계 계산 완료: 총거리={:.1f}m, 고도상승={:.1f}m, 고도하강={:.1f}m",
                routeStats.totalDistance, routeStats.elevationGain, routeStats.elevationLoss
            )

            // 최소 거리 검증 (너무 짧은 경로 방지)
            if (routeStats.totalDistance < 10.0) { // 10미터 미만
                logger.error("경로 거리 부족: {:.1f}m (최소 10m 필요)", routeStats.totalDistance)
                throw IllegalArgumentException(
                    "경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${
                        String.format(
                            "%.1f",
                            routeStats.totalDistance
                        )
                    }m)"
                )
            }

            // GPX 경로 엔티티 생성 및 저장
            // logger.debug("GPX 경로 엔티티 저장 시작")
            // val gpxRoute = gpxRouteRepository.save(
            //     GpxRoute(
            //         name = routeName,
            //         description = description,
            //         totalDistance = routeStats.totalDistance,
            //         totalElevationGain = routeStats.elevationGain,
            //         totalElevationLoss = routeStats.elevationLoss,
            //         createdAt = LocalDateTime.now()
            //     )
            // )
            // logger.info("GPX 경로 저장 완료: ID={}, 이름={}", gpxRoute.id, gpxRoute.name)

            // 웨이포인트 데이터 저장
            // logger.debug("웨이포인트 저장 시작: {}개", waypoints.size)
            // saveWaypoints(gpxRoute, waypoints)
            // logger.info("웨이포인트 저장 완료: {}개", waypoints.size)

            // 자동으로 체크포인트 생성 (1km 간격으로 거리 기반)
            // logger.debug("체크포인트 자동 생성 시작")
            // generateAutoCheckpoints(gpxRoute, waypoints)

            val processingTime = System.currentTimeMillis() - startTime
            logger.info(
                "GPX 파일 파싱 완료: 처리시간={}ms, 경로ID={}, 웨이포인트={}개, 총거리={:.1f}m",
                processingTime, "N/A", waypoints.size, routeStats.totalDistance
            )

            return mapOf( // GpxRoute 엔티티가 삭제되어 임시로 Map 반환
                "name" to routeName,
                "description" to description,
                "totalDistance" to routeStats.totalDistance,
                "totalElevationGain" to routeStats.elevationGain,
                "totalElevationLoss" to routeStats.elevationLoss,
                "createdAt" to LocalDateTime.now()
            )
        } catch (e: IllegalArgumentException) {
            // 비즈니스 로직 예외는 그대로 전파
            logger.warn("GPX 파일 처리 실패 (검증 오류): {}", e.message)
            throw e
        } catch (e: Exception) {
            // 예상하지 못한 예외 처리
            logger.error("GPX 파일 처리 중 예상치 못한 오류 발생: {}", e.message, e)
            throw RuntimeException("GPX 파일 처리 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 임시 파일 정리 (파일이 존재하고 삭제할 수 있는 경우만)
            try {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (deleted) {
                        logger.debug("임시 파일 삭제 완료: {}", tempFile.absolutePath)
                    } else {
                        logger.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                // 임시 파일 삭제 실패는 로그만 남기고 무시
                logger.warn("임시 파일 삭제 중 오류 발생: {}", e.message)
            }
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
     * 웨이포인트 데이터를 데이터베이스에 저장
     *
     * 현재는 GpxRoute와 GpxWaypoint 엔티티가 삭제되어 비활성화됨
     *
     * @param gpxRoute 저장된 GPX 경로 엔티티 (현재 사용 안함)
     * @param waypoints 웨이포인트 리스트
     */
    private fun saveWaypoints(gpxRoute: Any?, waypoints: List<WayPoint>) {
        // 웨이포인트 저장 기능 비활성화 (GpxWaypoint 엔티티 삭제됨)
        logger.debug("웨이포인트 저장 기능 비활성화됨: {}개", waypoints.size)

        // var cumulativeDistance = 0.0
        //
        // waypoints.forEachIndexed { index, waypoint ->
        //     // 첫 번째 웨이포인트가 아닌 경우 누적 거리 계산
        //     if (index > 0) {
        //         val prevWaypoint = waypoints[index - 1]
        //         val distance = GeoUtils.calculateDistance(
        //             prevWaypoint.latitude.toDegrees(),
        //             prevWaypoint.longitude.toDegrees(),
        //             waypoint.latitude.toDegrees(),
        //             waypoint.longitude.toDegrees()
        //         )
        //         cumulativeDistance += distance
        //     }
        //
        //     // 고도 데이터 처리: 음수이거나 없는 경우 0.0으로 처리
        //     val elevation = waypoint.elevation.map { it.toDouble() }.orElse(0.0)
        //     val validElevation = if (elevation < 0) 0.0 else elevation
        //
        //     // GpxWaypoint 엔티티 생성 및 저장
        //     val gpxWaypoint = GpxWaypoint(
        //         latitude = waypoint.latitude.toDegrees(),
        //         longitude = waypoint.longitude.toDegrees(),
        //         elevation = validElevation, // 유효한 고도 데이터만 저장
        //         sequence = index, // 웨이포인트 순서
        //         distanceFromStart = cumulativeDistance, // 시작점으로부터의 누적 거리
        //         gpxRoute = gpxRoute
        //     )
        //
        //     gpxWaypointRepository.save(gpxWaypoint)
        // }
    }

    /**
     * 자동 체크포인트 생성
     *
     * 현재는 GpxRoute 엔티티가 삭제되어 비활성화됨
     *
     * @param gpxRoute 저장된 GPX 경로 엔티티 (현재 사용 안함)
     * @param waypoints 웨이포인트 리스트
     */
    private fun generateAutoCheckpoints(gpxRoute: Any?, waypoints: List<WayPoint>) {
        // 체크포인트 자동 생성 기능 비활성화 (GpxRoute 엔티티 삭제됨)
        logger.debug("체크포인트 자동 생성 기능 비활성화됨: 웨이포인트={}개", waypoints.size)

        // 체크포인트 자동 생성 기능 비활성화 (GpxRoute 엔티티 삭제됨)
        logger.info("체크포인트 자동 생성 기능 비활성화됨")

        // val checkpointInterval = 1000.0 // 체크포인트 간격: 1km
        // var nextCheckpointDistance = checkpointInterval // 다음 체크포인트까지의 목표 거리
        // var cumulativeDistance = 0.0 // 누적 거리
        // var checkpointSequence = 1 // 체크포인트 순서 번호
        // var generatedCheckpoints = 0
        //
        // // 시작점 체크포인트 생성
        // val startWaypoint = waypoints.first()
        // val startCheckpoint = checkpointRepository.save(
        //     Checkpoint(
        //         name = "시작점",
        //         latitude = startWaypoint.latitude.toDegrees(),
        //         longitude = startWaypoint.longitude.toDegrees(),
        //         radius = 50.0, // 체크포인트 인식 반경: 50m
        //         sequence = 0,
        //         description = "경로 시작점",
        //         gpxRoute = gpxRoute
        //     )
        // )
        // generatedCheckpoints++
        // logger.debug("시작점 체크포인트 생성: ID={}, 좌표=({:.6f}, {:.6f})",
        //     startCheckpoint.id, startCheckpoint.latitude, startCheckpoint.longitude)
        //
        // // 중간 체크포인트들 생성 (1km 간격)
        // for (i in 1 until waypoints.size) {
        //     val prevWaypoint = waypoints[i - 1]
        //     val currWaypoint = waypoints[i]
        //
        //     // 현재 세그먼트의 거리 계산
        //     val segmentDistance = GeoUtils.calculateDistance(
        //         prevWaypoint.latitude.toDegrees(),
        //         prevWaypoint.longitude.toDegrees(),
        //         currWaypoint.latitude.toDegrees(),
        //         currWaypoint.longitude.toDegrees()
        //     )
        //
        //     cumulativeDistance += segmentDistance
        //
        //     // 누적 거리가 다음 체크포인트 거리에 도달한 경우
        //     if (cumulativeDistance >= nextCheckpointDistance) {
        //         val checkpoint = checkpointRepository.save(
        //             Checkpoint(
        //                 name = "체크포인트 $checkpointSequence",
        //                 latitude = currWaypoint.latitude.toDegrees(),
        //                 longitude = currWaypoint.longitude.toDegrees(),
        //                 radius = 50.0,
        //                 sequence = checkpointSequence,
        //                 description = "${kotlin.math.round(cumulativeDistance)}m 지점",
        //                 gpxRoute = gpxRoute
        //             )
        //         )
        //         generatedCheckpoints++
        //         logger.debug("중간 체크포인트 생성: ID={}, 순서={}, 거리={:.0f}m, 좌표=({:.6f}, {:.6f})",
        //             checkpoint.id, checkpointSequence, cumulativeDistance,
        //             checkpoint.latitude, checkpoint.longitude)
        //
        //         // 다음 체크포인트 목표 거리 설정
        //         nextCheckpointDistance += checkpointInterval
        //         checkpointSequence++
        //     }
        // }
        //
        // // 종료점 체크포인트 생성
        // val endWaypoint = waypoints.last()
        // val endCheckpoint = checkpointRepository.save(
        //     Checkpoint(
        //         name = "종료점",
        //         latitude = endWaypoint.latitude.toDegrees(),
        //         longitude = endWaypoint.longitude.toDegrees(),
        //         radius = 50.0,
        //         sequence = checkpointSequence,
        //         description = "경로 종료점",
        //         gpxRoute = gpxRoute
        //     )
        // )
        // generatedCheckpoints++
        // logger.debug("종료점 체크포인트 생성: ID={}, 좌표=({:.6f}, {:.6f})",
        //     endCheckpoint.id, endCheckpoint.latitude, endCheckpoint.longitude)
        //
        // logger.info("체크포인트 자동 생성 완료: 경로ID={}, 총 {}개 생성 (시작점 + 중간점 {}개 + 종료점)",
        //     gpxRoute.id, generatedCheckpoints, generatedCheckpoints - 2)
    }

    /**
     * 모든 GPX 경로 조회
     *
     * 현재는 GpxRoute 엔티티가 삭제되어 비활성화됨
     *
     * @return GPX 경로 리스트 (현재는 빈 리스트)
     */
    fun getAllRoutes(): List<Any> {
        logger.debug("모든 GPX 경로 조회 기능 비활성화됨")
        // val routes = gpxRouteRepository.findAllOrderByCreatedAtDesc()
        // logger.info("GPX 경로 조회 완료: {}개 경로", routes.size)
        return emptyList()
    }

    /**
     * GPX 경로 상세 조회
     *
     * 현재는 GpxRoute 엔티티가 삭제되어 비활성화됨
     *
     * @param routeId 경로 ID
     * @return GPX 경로 엔티티 (현재는 null)
     */
    fun getRouteById(routeId: Long): Any? {
        logger.debug("GPX 경로 상세 조회 기능 비활성화됨: ID={}", routeId)
        // val route = gpxRouteRepository.findById(routeId).orElse(null)
        // if (route != null) {
        //     logger.debug("GPX 경로 조회 성공: ID={}, 이름={}", route.id, route.name)
        // } else {
        //     logger.warn("GPX 경로 조회 실패: ID={}가 존재하지 않음", routeId)
        // }
        return null
    }

    /**
     * 경로의 웨이포인트 조회
     *
     * 현재는 GpxWaypoint 엔티티가 삭제되어 비활성화됨
     *
     * @param routeId 경로 ID
     * @return 웨이포인트 리스트 (현재는 빈 리스트)
     */
    fun getRouteWaypoints(routeId: Long): List<Any> {
        logger.debug("경로 웨이포인트 조회 기능 비활성화됨: 경로ID={}", routeId)
        // val waypoints = gpxWaypointRepository.findByGpxRouteIdOrderBySequenceAsc(routeId)
        // logger.debug("웨이포인트 조회 완료: 경로ID={}, {}개", routeId, waypoints.size)
        return emptyList()
    }

    /**
     * 경로의 체크포인트 조회
     *
     * 특정 경로에 속한 모든 체크포인트를 순서대로 조회합니다.
     *
     * @param routeId 경로 ID
     * @return 체크포인트 리스트 (순서별 정렬)
     */
    fun getRouteCheckpoints(routeId: Long): List<Checkpoint> {
        logger.debug("경로 체크포인트 조회 기능 비활성화됨: 경로ID={}", routeId)
        // val checkpoints = checkpointRepository.findByCourse_CourseIdOrderByCpIndexAsc(routeId)
        // logger.debug("체크포인트 조회 완료: 경로ID={}, {}개", routeId, checkpoints.size)
        return emptyList()
    }

    @Transactional(readOnly = true)
    fun correctLocation(request: CorrectLocationRequestDto): CorrectLocationResponseDto {
        logger.info("위치 보정 요청: userId=${request.userId}, eventDetailId=${request.eventDetailId}, GPS 포인트 수=${request.gpsData.size}")

        try {
            val userId = request.userId
            val eventDetailId = request.eventDetailId
            val gpsDataList = request.gpsData

            // GPS 데이터 리스트 유효성 검증
            if (gpsDataList.isEmpty()) {
                throw IllegalArgumentException("GPS 데이터가 비어있습니다")
            }

            // Redis에서 코스 데이터 조회 (없으면 자동 로드)
            val courseData = realtimeLocationService.courseDataService.getCourseDataByEventId(eventDetailId)

            // 3차원 칼만 필터 초기화
            val kalman = KalmanFilter()

            // 가장 최신(마지막) GPS 포인트로 최종 결과 계산
            val lastGpsData = gpsDataList.last()
            var finalCorrectedLat = 0.0
            var finalCorrectedLng = 0.0
            var finalCorrectedAlt: Double? = null

            // 각 GPS 포인트에 대해 순차 처리 (칼만 필터 상태 업데이트용)
            gpsDataList.forEachIndexed { index, gpsData ->
                val lat = gpsData.lat
                val lng = gpsData.lng
                val alt = gpsData.altitude
                val accuracy = gpsData.accuracy
                val timestamp = gpsData.timestamp

                logger.debug("GPS 포인트 $index 처리: 원본위치=($lat, $lng, $alt)")

                // 가중치 기반 3차원 칼만 필터를 사용한 GPS 노이즈 제거
                val filtered = if (accuracy != null && accuracy > 0) {
                    // 정확도 정보가 있는 경우 가중치 기반 필터링
                    val confidence = calculateGpsConfidence(accuracy, gpsData.speed)
                    kalman.filterWithWeights(lat, lng, alt, accuracy, confidence)
                } else {
                    // 정확도 정보가 없는 경우 기본 3차원 필터링
                    kalman.filter(lat, lng, alt)
                }

                logger.debug(
                    "3차원 칼만 필터 적용: 원본=($lat, $lng, $alt) -> " +
                            "필터링=(${"%.6f".format(filtered.first)}, ${"%.6f".format(filtered.second)}, " +
                            "${filtered.third?.let { "%.2f".format(it) } ?: "null"})")
                logger.debug(
                    "GPS 정확도: ${accuracy}m, 신뢰도: ${
                        if (accuracy != null) calculateGpsConfidence(
                            accuracy,
                            gpsData.speed
                        ) else "기본값"
                    }"
                )

                // 마지막 포인트에 대해서만 경로 매칭 수행
                if (index == gpsDataList.size - 1) {
                    val matchResult = if (courseData == null) {
                        // 코스 데이터가 없으면 기본 GPS 위치 사용 (GpxRoute 엔티티 삭제됨)
                        logger.warn("코스 데이터가 Redis에 없음. 기본 GPS 위치 사용: eventDetailId=$eventDetailId")

                        // 기본 매칭 결과 생성
                        com.sponovation.runtrack.algorithm.MatchResult(
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
                            bearing = gpsData.bearing?.toDouble() ?: 0.0,
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
                            eventDetailId = eventDetailId,
                            originalLat = lat,
                            originalLng = lng,
                            correctedLat = finalCorrectedLat,
                            correctedLng = finalCorrectedLng,
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        logger.warn("실시간 위치 저장 실패 (계속 진행): userId=$userId", e)
                    }
                }
            }

            logger.info(
                "위치 보정 완료: ${gpsDataList.size}개 포인트 처리, " +
                        "최종 보정위치=(${"%.6f".format(finalCorrectedLat)}, ${"%.6f".format(finalCorrectedLng)}, " +
                        "${finalCorrectedAlt?.let { "%.2f".format(it) } ?: "null"}m)")

            // 마지막 매칭 결과에서 상세 정보 추출
            val kalmanForFinal = KalmanFilter()
            gpsDataList.forEach { gpsData ->
                if (gpsData.accuracy != null && gpsData.accuracy > 0) {
                    val confidence = calculateGpsConfidence(gpsData.accuracy, gpsData.speed)
                    kalmanForFinal.filterWithWeights(
                        gpsData.lat,
                        gpsData.lng,
                        gpsData.altitude,
                        gpsData.accuracy,
                        confidence
                    )
                } else {
                    kalmanForFinal.filter(gpsData.lat, gpsData.lng, gpsData.altitude)
                }
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
                    bearing = lastGpsData.bearing?.toDouble() ?: 0.0,
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
                data = CorrectedLocationDataDto(
                    correctedLat = finalCorrectedLat,
                    correctedLng = finalCorrectedLng,
                    correctedAltitude = finalCorrectedAlt
                ),
                checkpointReaches = checkpointReaches.takeIf { it.isNotEmpty() },
                nearestGpxPoint = finalMatchResult?.nearestGpxPoint?.let { nearest ->
                    com.sponovation.runtrack.dto.NearestGpxPointDto(
                        latitude = nearest.latitude,
                        longitude = nearest.longitude,
                        elevation = nearest.elevation,
                        distanceToPoint = nearest.distanceToPoint,
                        distanceFromStart = nearest.distanceFromStart,
                        routeProgress = finalMatchResult.routeProgress,
                        routeBearing = nearest.routeBearing
                    )
                },
                matchingQuality = finalMatchResult?.let { result ->
                    // 최종 GPS 신뢰도 계산
                    val finalGpsConfidence = if (lastGpsData.accuracy != null && lastGpsData.accuracy > 0) {
                        calculateGpsConfidence(lastGpsData.accuracy, lastGpsData.speed)
                    } else null

                    // 칼만 필터 불확실성 계산
                    val uncertainty = kalmanForFinal.getUncertainty3D()
                    val uncertaintyDto = com.sponovation.runtrack.dto.CorrectionUncertaintyDto(
                        latitudeUncertainty = uncertainty.first,
                        longitudeUncertainty = uncertainty.second,
                        altitudeUncertainty = uncertainty.third
                    )

                    // 보정 강도 계산 (원본과 보정된 위치의 차이 기반)
                    val correctionDistance = com.sponovation.runtrack.util.GeoUtils.calculateDistance(
                        lastGpsData.lat, lastGpsData.lng,
                        finalCorrectedLat, finalCorrectedLng
                    )
                    val correctionStrength = when {
                        correctionDistance < 1.0 -> 0.1    // 거의 보정하지 않음
                        correctionDistance < 5.0 -> 0.3    // 약간 보정
                        correctionDistance < 15.0 -> 0.6   // 보통 보정
                        correctionDistance < 50.0 -> 0.8   // 많이 보정
                        else -> 1.0                        // 매우 많이 보정
                    }

                    // 품질 등급 결정
                    val qualityGrade = calculateQualityGrade(
                        result.matched,
                        result.matchScore,
                        finalGpsConfidence ?: 0.5,
                        correctionStrength
                    )

                    com.sponovation.runtrack.dto.MatchingQualityDto(
                        isMatched = result.matched,
                        matchScore = result.matchScore,
                        currentBearing = result.currentBearing,
                        routeBearing = result.routeBearing,
                        bearingDifference = result.bearingDifference,
                        segmentIndex = result.segmentIndex,
                        gpsConfidence = finalGpsConfidence,
                        kalmanUncertainty = uncertaintyDto,
                        correctionStrength = correctionStrength,
                        qualityGrade = qualityGrade
                    )
                }
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
     * 위치 보정 품질 등급을 계산합니다.
     *
     * @param isMatched 경로 매칭 성공 여부
     * @param matchScore 매칭 점수
     * @param gpsConfidence GPS 신뢰도
     * @param correctionStrength 보정 강도
     * @return 품질 등급 ("EXCELLENT", "GOOD", "FAIR", "POOR")
     */
    private fun calculateQualityGrade(
        isMatched: Boolean,
        matchScore: Double,
        gpsConfidence: Double,
        correctionStrength: Double
    ): String {

        // 기본 점수 계산 (0 ~ 100)
        var score = 0.0

        // 경로 매칭 성공 여부 (40점)
        score += if (isMatched) 40.0 else 0.0

        // 매칭 점수 (30점) - 낮을수록 좋음
        val matchScorePoints = when {
            matchScore <= 0.1 -> 30.0
            matchScore <= 0.3 -> 25.0
            matchScore <= 0.5 -> 20.0
            matchScore <= 0.7 -> 15.0
            matchScore <= 1.0 -> 10.0
            else -> 0.0
        }
        score += matchScorePoints

        // GPS 신뢰도 (20점)
        val confidencePoints = gpsConfidence * 20.0
        score += confidencePoints

        // 보정 강도 (10점) - 적당한 보정이 좋음
        val correctionPoints = when {
            correctionStrength <= 0.2 -> 10.0  // 거의 보정하지 않음 (좋음)
            correctionStrength <= 0.4 -> 8.0   // 약간 보정 (좋음)
            correctionStrength <= 0.6 -> 6.0   // 보통 보정 (보통)
            correctionStrength <= 0.8 -> 4.0   // 많이 보정 (나쁨)
            else -> 2.0                         // 매우 많이 보정 (매우 나쁨)
        }
        score += correctionPoints

        // 등급 결정
        return when {
            score >= 85.0 -> "EXCELLENT"  // 우수
            score >= 70.0 -> "GOOD"       // 좋음
            score >= 50.0 -> "FAIR"       // 보통
            else -> "POOR"                // 나쁨
        }
    }

    /**
     * 보정된 위치 정보를 Redis에 업데이트
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param rawGpsData 원본 GPS 데이터
     * @param correctedLat 보정된 위도
     * @param correctedLng 보정된 경도
     * @param correctedAlt 보정된 고도
     * @param timestamp 타임스탬프
     * @param distanceFromStart 시작점으로부터 거리
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
            val redisKey = "location:$userId:$eventDetailId"

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
                heading = rawGpsData.bearing,
                distanceCovered = distanceCovered,
                cumulativeTime = cumulativeTime
            )

            // Redis Hash에 저장
            val hashOps = redisTemplate.opsForHash<String, String>()
            hashOps.put(redisKey, "userId", locationData.userId.toString())
            hashOps.put(redisKey, "eventId", locationData.eventId.toString())
            hashOps.put(redisKey, "eventDetailId", locationData.eventDetailId.toString())
            hashOps.put(redisKey, "rawLatitude", locationData.rawLatitude.toString())
            hashOps.put(redisKey, "rawLongitude", locationData.rawLongitude.toString())
            hashOps.put(redisKey, "rawAltitude", locationData.rawAltitude?.toString() ?: "null")
            hashOps.put(redisKey, "rawAccuracy", locationData.rawAccuracy?.toString() ?: "null")
            hashOps.put(redisKey, "rawTime", locationData.rawTime.toString())
            hashOps.put(redisKey, "rawSpeed", locationData.rawSpeed?.toString() ?: "null")
            hashOps.put(redisKey, "correctedLatitude", locationData.correctedLatitude.toString())
            hashOps.put(redisKey, "correctedLongitude", locationData.correctedLongitude.toString())
            hashOps.put(redisKey, "correctedAltitude", locationData.correctedAltitude?.toString() ?: "null")
            hashOps.put(redisKey, "lastUpdated", locationData.lastUpdated.toString())
            hashOps.put(redisKey, "heading", locationData.heading?.toString() ?: "null")
            hashOps.put(redisKey, "distanceCovered", locationData.distanceCovered.toString())
            hashOps.put(redisKey, "cumulativeTime", locationData.cumulativeTime.toString())

            // TTL 설정 (24시간)
            redisTemplate.expire(redisKey, 24, java.util.concurrent.TimeUnit.HOURS)

            logger.info(
                "보정 위치 Redis 업데이트 완료: userId=$userId, eventDetailId=$eventDetailId, " +
                        "원본위치=(${rawGpsData.lat}, ${rawGpsData.lng}), " +
                        "보정위치=($correctedLat, $correctedLng), " +
                        "이동거리=${String.format("%.2f", distanceCovered)}m, " +
                        "누적시간=${cumulativeTime}초"
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
     * Redis에서 보정된 위치 정보 조회
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 위치 데이터 또는 null
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
                return null
            }

            LocationData(
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
