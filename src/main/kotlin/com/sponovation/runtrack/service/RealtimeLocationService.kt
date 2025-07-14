package com.sponovation.runtrack.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.util.GeoUtils
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 실시간 위치 추적 서비스
 * 
 * 주요 기능:
 * - 마라톤/러닝 대회 참가자들의 실시간 위치 정보 관리
 * - Redis를 사용한 고성능 위치 데이터 저장 및 조회
 * - 실시간 순위 계산 및 업데이트
 * - 위치 보정 및 정밀도 조정
 * - 경과 시간 및 거리 계산
 * 
 * @author Sponovation
 * @since 1.0
 */
@Service
class RealtimeLocationService(
    /** Redis 템플릿 - 위치 데이터 저장소 */
    private val redisTemplate: RedisTemplate<String, Any>,
    /** JSON 직렬화/역직렬화를 위한 ObjectMapper */
    private val objectMapper: ObjectMapper,
    /** 코스 데이터 서비스 - 거리 계산을 위한 코스 정보 제공 */
    val courseDataService: CourseDataService
) {
    
    /** 로깅을 위한 로거 인스턴스 */
    private val logger = LoggerFactory.getLogger(RealtimeLocationService::class.java)
    
    companion object {
        /** Redis 키 상수 정의 */
        
        /** 위치 정보 저장을 위한 키 접두사 - "location:event:{eventDetailId}:participant:{userId}" 형태로 사용 */
        private const val LOCATION_KEY_PREFIX = "location:event:"
        
        /** 참가자 키 접두사 - 위치 키와 조합하여 사용 */
        private const val PARTICIPANT_KEY_PREFIX = "participant:"
        
        /** 순위 정보 저장을 위한 키 접두사 - "ranking:event:{eventDetailId}" 형태로 사용 */
        private const val RANKING_KEY_PREFIX = "ranking:event:"
        
        /** 위치 데이터 TTL (Time To Live) - 30분 후 자동 삭제 */
        private const val LOCATION_TTL_MINUTES = 30L
    }
    
    /**
     * 참가자 위치 정보를 Redis에 저장합니다.
     * 
     * 처리 과정:
     * 1. 닉네임 설정 (임시로 "러너{userId}" 형태)
     * 2. 시작점으로부터의 거리 계산
     * 3. 경과 시간 계산
     * 4. 위치 데이터 객체 생성
     * 5. Redis에 JSON 형태로 저장 (TTL 30분 설정)
     * 6. 순위 정보 업데이트
     * 
     * @param userId 사용자 고유 ID
     * @param eventDetailId 대회 상세 정보 ID
     * @param originalLat 원본 위도 (GPS에서 직접 받은 값)
     * @param originalLng 원본 경도 (GPS에서 직접 받은 값)
     * @param correctedLat 보정된 위도 (알고리즘으로 보정된 값)
     * @param correctedLng 보정된 경도 (알고리즘으로 보정된 값)
     * @param alt 고도 (선택사항)
     * @param heading 방향각 (선택사항)
     * @param speed 속도 (선택사항)
     * @param timestamp 위치 측정 시간 (ISO 8601 형식)
     * 
     * @throws RuntimeException 위치 저장 실패 시
     */
    fun saveParticipantLocation(
        userId: Long,
        eventDetailId: Long,
        originalLat: Double,
        originalLng: Double,
        correctedLat: Double,
        correctedLng: Double,
        alt: Double? = null,
        heading: Double? = null,
        speed: Double? = null,
        timestamp: String
    ) {
        try {
            logger.info("참가자 위치 저장 시작: userId=$userId, eventDetailId=$eventDetailId")
            
            // TODO: 실제 환경에서는 사용자 서비스에서 닉네임을 조회해야 함
            // 현재는 테스트를 위해 임시 닉네임 생성
            val nickname = "러너$userId"
            
            // 코스 시작점으로부터의 실제 거리 계산
            // 이 값은 순위 결정에 중요한 역할을 함
            val distanceFromStart = calculateDistanceFromStart(eventDetailId, correctedLat, correctedLng)
            
            // 대회 시작부터 현재까지의 경과 시간 계산 (초 단위)
            // 실제로는 대회 공식 시작 시간을 기준으로 해야 함
            val elapsedTimeSeconds = calculateElapsedTime(eventDetailId, userId)
            
            // 캐시에 저장할 위치 데이터 객체 생성
            val locationData = ParticipantLocationCache(
                userId = userId,
                eventDetailId = eventDetailId,
                nickname = nickname,
                lat = originalLat,              // 원본 GPS 위치
                lng = originalLng,              // 원본 GPS 위치
                alt = alt,
                heading = heading,
                speed = speed,
                timestamp = timestamp,
                correctedLat = correctedLat,    // 보정된 위치 (지도에서 실제 표시될 위치)
                correctedLng = correctedLng,    // 보정된 위치
                distanceFromStart = distanceFromStart,
                elapsedTimeSeconds = elapsedTimeSeconds
            )
            
            // Redis 키 생성: "location:event:{eventDetailId}:participant:{userId}"
            val redisEventDetailId = eventDetailId.toString()
            val redisUserId = userId.toString()
            val locationKey = "$LOCATION_KEY_PREFIX$redisEventDetailId:$PARTICIPANT_KEY_PREFIX$redisUserId"
            
            // 객체를 JSON 문자열로 직렬화
            val jsonData = objectMapper.writeValueAsString(locationData)
            
            // Redis에 저장 (30분 TTL 설정으로 메모리 관리)
            redisTemplate.opsForValue().set(locationKey, jsonData, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)
            
            // 실시간 순위 업데이트 (거리 기준)
            updateRanking(eventDetailId, userId, distanceFromStart, elapsedTimeSeconds)
            
            logger.info("참가자 위치 저장 완료: userId=$userId, distance=${String.format("%.2f", distanceFromStart)}m")
            
        } catch (e: Exception) {
            logger.error("참가자 위치 저장 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            throw RuntimeException("참가자 위치 저장에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 대회의 모든 참가자 위치를 조회합니다.
     * 
     * 처리 과정:
     * 1. Redis에서 해당 대회의 모든 참가자 위치 키 검색
     * 2. 각 키에서 위치 데이터 조회 및 역직렬화
     * 3. 줌 레벨에 따른 위치 정밀도 조정
     * 4. 상위 3명 순위 정보 조회
     * 5. 응답 DTO 생성 및 반환
     * 
     * @param eventDetailId 대회 상세 정보 ID
     * @param zoomLevel 지도 줌 레벨 (정밀도 조정용, 선택사항)
     * @return 실시간 위치 응답 DTO (참가자 위치 목록 + 상위 3명 순위)
     * 
     * @throws RuntimeException 위치 조회 실패 시
     */
    fun getParticipantsLocations(eventDetailId: Long, zoomLevel: Int? = null): RealtimeLocationResponseDto {
        try {
            logger.info("참가자 위치 조회 시작: eventDetailId=$eventDetailId, zoomLevel=$zoomLevel")
            
            // 해당 대회의 모든 참가자 키 패턴 생성
            val redisEventDetailId = eventDetailId.toString()
            val pattern = "$LOCATION_KEY_PREFIX$redisEventDetailId:$PARTICIPANT_KEY_PREFIX*"
            
            // Redis에서 패턴에 매칭되는 모든 키 조회
            val keys = redisTemplate.keys(pattern)
            
            logger.info("발견된 참가자 수: ${keys.size}")
            
            val participants = mutableListOf<ParticipantLocationDto>()
            
            // 각 참가자의 위치 데이터 처리
            for (key in keys) {
                try {
                    // Redis에서 JSON 데이터 조회
                    val jsonData = redisTemplate.opsForValue().get(key) as? String
                    if (jsonData != null) {
                        // JSON을 객체로 역직렬화
                        val locationData = objectMapper.readValue(jsonData, ParticipantLocationCache::class.java)
                        
                        // 줌 레벨에 따른 위치 정밀도 조정
                        // 네트워크 대역폭 절약 및 개인정보 보호 목적
                        val adjustedLocation = adjustLocationPrecision(
                            locationData.correctedLat, 
                            locationData.correctedLng, 
                            zoomLevel
                        )
                        
                        // 클라이언트에 전송할 DTO 생성
                        participants.add(
                            ParticipantLocationDto(
                                userId = locationData.userId,
                                nickname = locationData.nickname,
                                lat = adjustedLocation.first,      // 조정된 위도
                                lng = adjustedLocation.second,     // 조정된 경도
                                alt = locationData.alt,
                                heading = locationData.heading,
                                speed = locationData.speed,
                                timestamp = locationData.timestamp
                            )
                        )
                    }
                } catch (e: Exception) {
                    // 개별 참가자 데이터 파싱 실패 시 로그만 남기고 계속 진행
                    logger.warn("참가자 위치 데이터 파싱 실패: key=$key", e)
                }
            }
            
            // 상위 3명 순위 정보 조회
            val top3 = getTop3Ranking(eventDetailId)
            
            logger.info("참가자 위치 조회 완료: 총 ${participants.size}명, 상위 3명")
            
            // 최종 응답 DTO 생성
            return RealtimeLocationResponseDto(
                data = RealtimeLocationDataDto(
                    participants = participants,
                    top3 = top3
                )
            )
            
        } catch (e: Exception) {
            logger.error("참가자 위치 조회 실패: eventDetailId=$eventDetailId", e)
            throw RuntimeException("참가자 위치 조회에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 시작점으로부터의 거리를 계산합니다.
     * 
     * 처리 과정:
     * 1. 대회의 코스 데이터 조회
     * 2. 코스 시작점 좌표 추출
     * 3. 현재 위치와 시작점 간의 직선 거리 계산 (하버사인 공식 사용)
     * 
     * 주의사항:
     * - 실제 러닝 거리가 아닌 직선 거리를 계산함
     * - 향후 코스 경로를 따른 실제 거리 계산으로 개선 필요
     * 
     * @param eventDetailId 대회 상세 정보 ID
     * @param lat 현재 위도
     * @param lng 현재 경도
     * @return 시작점으로부터의 거리 (미터 단위), 실패 시 0.0
     */
    private fun calculateDistanceFromStart(eventDetailId: Long, lat: Double, lng: Double): Double {
        return try {
            // 코스 데이터 서비스에서 해당 대회의 코스 정보 조회
            val courseData = courseDataService.getCourseDataByEventId(eventDetailId)
            
            // 코스 데이터가 존재하고 보간된 포인트들이 있는 경우
            if (courseData != null && courseData.interpolatedPoints.isNotEmpty()) {
                // 첫 번째 포인트를 시작점으로 사용
                val startPoint = courseData.interpolatedPoints.first()
                
                // 하버사인 공식을 사용한 거리 계산 (GeoUtils.calculateDistance)
                GeoUtils.calculateDistance(startPoint.latitude, startPoint.longitude, lat, lng)
            } else {
                logger.warn("코스 데이터가 없어 거리 계산을 건너뜀: eventDetailId=$eventDetailId")
                0.0
            }
        } catch (e: Exception) {
            logger.warn("시작점 거리 계산 실패: eventDetailId=$eventDetailId", e)
            0.0
        }
    }
    
    /**
     * 경과 시간을 계산합니다 (초 단위).
     * 
     * 처리 과정:
     * 1. Redis에서 해당 사용자의 시작 시간 조회
     * 2. 시작 시간이 없으면 현재 시간을 시작 시간으로 설정
     * 3. 현재 시간과 시작 시간의 차이 계산
     * 
     * 개선 필요사항:
     * - 실제 대회 공식 시작 시간 기준으로 계산
     * - 개인별 스타트 시간 고려 (웨이브 스타트 등)
     * - 일시정지/재시작 기능 지원
     * 
     * @param eventDetailId 대회 상세 정보 ID
     * @param userId 사용자 ID
     * @return 경과 시간 (초 단위), 실패 시 0
     */
    private fun calculateElapsedTime(eventDetailId: Long, userId: Long): Long {
        return try {
            // 개별 사용자의 시작 시간을 저장하는 Redis 키
            val startTimeKey = "start:event:$eventDetailId:user:$userId"
            
            // Redis에서 시작 시간 조회
            val startTimeString = redisTemplate.opsForValue().get(startTimeKey) as? String
            
            if (startTimeString != null) {
                // 기존 시작 시간이 있는 경우: 경과 시간 계산
                val startTime = Instant.parse(startTimeString)
                val currentTime = Instant.now()
                (currentTime.epochSecond - startTime.epochSecond)
            } else {
                // 시작 시간이 없는 경우: 현재 시간을 시작 시간으로 설정
                val currentTime = Instant.now()
                
                // 12시간 TTL로 시작 시간 저장 (하루 종일 대회를 고려)
                redisTemplate.opsForValue().set(startTimeKey, currentTime.toString(), 12, TimeUnit.HOURS)
                0L  // 첫 번째 위치 전송이므로 경과 시간은 0
            }
        } catch (e: Exception) {
            logger.warn("경과 시간 계산 실패: eventDetailId=$eventDetailId, userId=$userId", e)
            0L
        }
    }
    
    /**
     * 순위 정보를 업데이트합니다.
     * 
     * 사용하는 데이터 구조:
     * - Redis Sorted Set을 사용하여 자동 정렬된 순위 관리
     * - Score: 시작점으로부터의 거리 (더 멀리 간 사람이 높은 점수)
     * - Member: 사용자 ID
     * 
     * 순위 결정 기준:
     * 1. 1순위: 시작점으로부터 더 멀리 간 사람
     * 2. 향후 개선: 실제 코스 경로상의 진행률, 경과 시간 등 고려
     * 
     * @param eventDetailId 대회 상세 정보 ID
     * @param userId 사용자 ID
     * @param distanceFromStart 시작점으로부터의 거리
     * @param elapsedTimeSeconds 경과 시간 (현재 미사용, 향후 확장용)
     */
    private fun updateRanking(eventDetailId: Long, userId: Long, distanceFromStart: Double, elapsedTimeSeconds: Long) {
        try {
            // 순위 정보를 저장할 Redis Sorted Set 키
            val redisEventDetailId = eventDetailId.toString()
            val rankingKey = "$RANKING_KEY_PREFIX$redisEventDetailId"
            
            // Sorted Set에 사용자 추가/업데이트
            // Score가 높을수록 상위 순위 (거리가 멀수록 앞순위)
            redisTemplate.opsForZSet().add(rankingKey, userId.toString(), distanceFromStart)
            
            // 순위 데이터도 30분 TTL 설정
            redisTemplate.expire(rankingKey, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)
            
        } catch (e: Exception) {
            logger.warn("순위 업데이트 실패: eventDetailId=$eventDetailId, userId=$userId", e)
        }
    }
    
    /**
     * 상위 3명의 순위를 조회합니다.
     * 
     * 처리 과정:
     * 1. Redis Sorted Set에서 상위 3명 조회 (점수 내림차순)
     * 2. 각 사용자의 상세 정보를 위치 캐시에서 조회
     * 3. 순위, 닉네임, 배번, 경과 시간 등 포함한 DTO 생성
     * 
     * 반환 정보:
     * - 순위 (1, 2, 3위)
     * - 사용자 ID 및 닉네임
     * - 배번 (임시로 "A{userId}"로 생성)
     * - 경과 시간 (HH:MM:SS 형식)
     * 
     * @param eventDetailId 대회 상세 정보 ID
     * @return 상위 3명의 순위 정보 리스트
     */
    private fun getTop3Ranking(eventDetailId: Long): List<RankingDto> {
        return try {
            val redisEventDetailId = eventDetailId.toString()
            val rankingKey = "$RANKING_KEY_PREFIX$redisEventDetailId"
            
            // Sorted Set에서 점수 내림차순으로 상위 3명 조회
            // reverseRange: 높은 점수부터 조회 (더 멀리 간 순서)
            val top3UserIds = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, 2)
            
            val rankings = mutableListOf<RankingDto>()
            
            // 각 상위 사용자에 대해 상세 정보 생성
            top3UserIds?.forEachIndexed { index, userIdStr ->
                val userId = userIdStr.toString()
                val eventDetailId = eventDetailId.toString()
                if (userId != null) {
                    // 해당 사용자의 위치 캐시에서 추가 정보 조회
                    val locationKey = "$LOCATION_KEY_PREFIX$eventDetailId:$PARTICIPANT_KEY_PREFIX$userId"
                    val jsonData = redisTemplate.opsForValue().get(locationKey) as? String
                    
                    if (jsonData != null) {
                        val locationData = objectMapper.readValue(jsonData, ParticipantLocationCache::class.java)
                        
                        rankings.add(
                            RankingDto(
                                rank = index + 1,  // 1위, 2위, 3위
                                userId = userId.toLong(),
                                nickname = locationData.nickname,
                                bibNumber = "A${userId.toString().padStart(3, '0')}", // TODO: 실제 배번 시스템 연동 필요
                                elapsedTime = formatElapsedTime(locationData.elapsedTimeSeconds)
                            )
                        )
                    }
                }
            }
            
            rankings
            
        } catch (e: Exception) {
            logger.warn("상위 3명 순위 조회 실패: eventDetailId=$eventDetailId", e)
            emptyList()
        }
    }
    
    /**
     * 줌 레벨에 따라 위치 정밀도를 조정합니다.
     * 
     * 목적:
     * 1. 네트워크 대역폭 절약 (불필요한 정밀도 제거)
     * 2. 개인정보 보호 (과도한 위치 정밀도 방지)
     * 3. 지도 렌더링 성능 최적화
     * 
     * 정밀도 레벨:
     * - 낮은 줌 (1-10): 소수점 3자리 (약 111m 정밀도)
     * - 중간 줌 (11-15): 소수점 5자리 (약 1.1m 정밀도)
     * - 높은 줌 (16+): 원본 정밀도 유지 (약 1cm 정밀도)
     * 
     * @param lat 원본 위도
     * @param lng 원본 경도
     * @param zoomLevel 지도 줌 레벨 (null인 경우 원본 정밀도 유지)
     * @return 조정된 위도, 경도 쌍
     */
    private fun adjustLocationPrecision(lat: Double, lng: Double, zoomLevel: Int?): Pair<Double, Double> {
        return when (zoomLevel) {
            in 1..10 -> {
                // 낮은 줌 레벨: 소수점 3자리까지 (약 111m 정밀도)
                val adjustedLat = String.format("%.3f", lat).toDouble()
                val adjustedLng = String.format("%.3f", lng).toDouble()
                Pair(adjustedLat, adjustedLng)
            }
            in 11..15 -> {
                // 중간 줌 레벨: 소수점 5자리까지 (약 1.1m 정밀도)
                val adjustedLat = String.format("%.5f", lat).toDouble()
                val adjustedLng = String.format("%.5f", lng).toDouble()
                Pair(adjustedLat, adjustedLng)
            }
            else -> {
                // 높은 줌 레벨 또는 줌 레벨 미지정: 원본 정밀도 유지
                Pair(lat, lng)
            }
        }
    }
    
    /**
     * 경과 시간을 HH:MM:SS 형식으로 포맷합니다.
     * 
     * 변환 과정:
     * 1. 전체 초를 시간, 분, 초로 분할
     * 2. 각 단위를 2자리 형식으로 포맷 (01, 02, ... 형태)
     * 3. 콜론(:)으로 구분된 시간 문자열 생성
     * 
     * 예시:
     * - 3665초 → "01:01:05"
     * - 7323초 → "02:02:03"
     * - 86400초 → "24:00:00"
     * 
     * @param seconds 경과 시간 (초 단위)
     * @return HH:MM:SS 형식의 시간 문자열
     */
    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600      // 3600초 = 1시간
        val minutes = (seconds % 3600) / 60  // 나머지를 60으로 나눈 몫 = 분
        val secs = seconds % 60         // 60으로 나눈 나머지 = 초
        
        // %02d: 2자리 수로 포맷, 부족하면 0으로 패딩
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
} 