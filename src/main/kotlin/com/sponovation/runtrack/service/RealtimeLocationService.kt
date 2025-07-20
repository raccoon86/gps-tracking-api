package com.sponovation.runtrack.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.repository.ParticipantRepository
import com.sponovation.runtrack.util.GeoUtils
import kotlin.reflect.full.memberProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
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
    val courseDataService: CourseDataService,
    /** 참가자 정보 조회를 위한 레포지토리 */
    private val participantRepository: ParticipantRepository
) {
    
    /** 로깅을 위한 로거 인스턴스 */
    private val logger = LoggerFactory.getLogger(RealtimeLocationService::class.java)
    
    companion object {
        /** Redis 키 상수 정의 */
        
        /** 위치 정보 저장을 위한 키 접두사 - "gps:{eventId}:{eventDetailId}:{userId}" 형태로 사용 */
        private const val GPS_KEY_PREFIX = "gps"

        /** 순위 정보 저장을 위한 키 접두사 - "leaderboard:{eventId}:{eventDetailId}" 형태로 사용 */
        private const val LEADERBOARD_KEY_PREFIX = "leaderboard"
        
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
        eventId: Long,
        eventDetailId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        heading: Double? = null,
        speed: Float? = null,
        timestamp: String
    ) {
        try {
            logger.info("참가자 위치 저장 시작: userId=$userId, eventDetailId=$eventDetailId")
            
            // 코스 시작점으로부터의 실제 거리 계산
            // 이 값은 순위 결정에 중요한 역할을 함
            val distanceFromStart = calculateDistanceFromStart(eventDetailId, latitude, longitude)
            
            // 대회 시작부터 현재까지의 경과 시간 계산 (초 단위)
            // 실제로는 대회 공식 시작 시간을 기준으로 해야 함
            val elapsedTimeSeconds = calculateElapsedTime(eventDetailId, userId)
            
            // 캐시에 저장할 위치 데이터 객체 생성
            val locationData = ParticipantLocationCache(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId,
                latitude = latitude,    // 보정된 위치 (지도에서 실제 표시될 위치)
                longitude = longitude,    // 보정된 위치
                altitude = altitude,
                speed = speed,
                heading = heading,
                created = timestamp,
            )
            
            // Redis 키 생성: "gps:{eventId}:{eventDetailId}:{userId}"
            val key = "$GPS_KEY_PREFIX:$eventId:$eventDetailId:$userId"
            
            // 기존 위치 데이터 존재 여부 확인
            val existingData = redisTemplate.opsForHash<String, Any>().entries(key)
            val isUpdate = existingData.isNotEmpty()
            
            // Redis Hash에 저장/업데이트 (30분 TTL 설정으로 메모리 관리)
            redisTemplate.opsForHash<String, Any>().putAll(key, mapOf(
                "userId" to locationData.userId,
                "eventId" to locationData.eventId,
                "eventDetailId" to locationData.eventDetailId,
                "latitude" to locationData.latitude,
                "longitude" to locationData.longitude,
                "altitude" to locationData.altitude,
                "speed" to locationData.speed,
                "heading" to locationData.heading,
                "created" to locationData.created
            ))
            redisTemplate.expire(key, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)
            
            // 실시간 순위 업데이트 (거리 기준)
            updateRanking(eventId, eventDetailId, userId, distanceFromStart, elapsedTimeSeconds)
            
            val action = if (isUpdate) "업데이트" else "저장"
            logger.info("참가자 위치 $action 완료: userId=$userId, distance=${String.format("%.2f", distanceFromStart)}m")
            
        } catch (e: Exception) {
            logger.error("참가자 위치 저장 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            throw RuntimeException("참가자 위치 저장에 실패했습니다: ${e.message}", e)
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
    private fun updateRanking(eventId: Long, eventDetailId: Long, userId: Long, distanceFromStart: Double, elapsedTimeSeconds: Long) {
        try {
            // 순위 정보를 저장할 Redis Sorted Set 키: "leaderboard:{eventId}:{eventDetailId}"
            val rankingKey = "$LEADERBOARD_KEY_PREFIX:$eventId:$eventDetailId"
            
            // Sorted Set에 사용자 추가/업데이트
            // Score가 높을수록 상위 순위 (거리가 멀수록 앞순위)
            redisTemplate.opsForZSet().add(rankingKey, userId.toString(), distanceFromStart)
            
            // 순위 데이터도 30분 TTL 설정
            redisTemplate.expire(rankingKey, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)
            
        } catch (e: Exception) {
            logger.warn("순위 업데이트 실패: eventId=$eventId, eventDetailId=$eventDetailId, userId=$userId", e)
        }
    }
} 