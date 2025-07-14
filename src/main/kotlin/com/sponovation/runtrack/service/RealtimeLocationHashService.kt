package com.sponovation.runtrack.service

import com.sponovation.runtrack.dto.RealtimeLocationDto
import com.sponovation.runtrack.dto.GpsDataDto
import com.sponovation.runtrack.util.GeoUtils
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * 실시간 위치 정보 Redis Hash 저장 서비스
 * 
 * 사용자의 실시간 위치 정보를 Redis Hash 형태로 저장하고 관리합니다.
 * 키 형태: location:{userId}:{eventDetailId}
 * 
 * 주요 기능:
 * - GPS 위치 보정 후 Redis Hash로 저장
 * - 체크포인트 도달 여부 확인
 * - 누적 거리 및 시간 계산
 * - 가장 멀리 도달한 체크포인트 추적
 * 
 * @author Sponovation
 * @since 1.0
 */
@Service
class RealtimeLocationHashService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val courseDataService: CourseDataService
) {
    
    private val logger = LoggerFactory.getLogger(RealtimeLocationHashService::class.java)
    
    companion object {
        /** Redis 키 접두사 */
        private const val LOCATION_KEY_PREFIX = "location:"
        
        /** 위치 데이터 TTL - 4시간 후 자동 삭제 */
        private const val LOCATION_TTL_HOURS = 4L
        
        /** 체크포인트 도달 허용 반경 (미터) */
        private const val CHECKPOINT_RADIUS_METERS = 50.0
        
        /** 체크포인트 간격 (미터) */
        private const val CHECKPOINT_INTERVAL_METERS = 1000.0
    }
    
    /**
     * 실시간 위치 정보를 Redis Hash에 저장합니다.
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param originalGpsData 원본 GPS 데이터
     * @param correctedLatitude 보정된 위도
     * @param correctedLongitude 보정된 경도
     * @param correctedAltitude 보정된 고도
     * @return 저장된 실시간 위치 정보 DTO
     */
    fun saveRealtimeLocation(
        userId: String,
        eventId: String,
        eventDetailId: Long,
        originalGpsData: GpsDataDto,
        correctedLatitude: Double,
        correctedLongitude: Double,
        correctedAltitude: Double? = null
    ): RealtimeLocationDto {
        
        logger.info("실시간 위치 정보 저장 시작: userId=$userId, eventDetailId=$eventDetailId")
        
        try {
            // Redis 키 생성
            val redisKey = generateRedisKey(userId, eventDetailId.toString())
            
            // 기존 위치 정보 조회 (누적 계산용)
            val existingLocation = getExistingLocation(redisKey)
            
            // 누적 거리 계산
            val distanceCovered = calculateDistanceCovered(
                existingLocation,
                correctedLatitude,
                correctedLongitude
            )
            
            // 누적 시간 계산
            val cumulativeTime = calculateCumulativeTime(
                existingLocation,
                originalGpsData.timestamp ?: 0L
            )
            
            // 체크포인트 도달 여부 확인
            val checkpointInfo = checkCheckpointReach(
                eventDetailId,
                correctedLatitude,
                correctedLongitude,
                distanceCovered,
                existingLocation
            )
            
            // 현재 시간 (Unix 타임스탬프)
            val currentTime = Instant.now().epochSecond
            
            // 실시간 위치 정보 DTO 생성
            val locationData = RealtimeLocationDto(
                userId = userId,
                eventId = eventId,
                eventDetailId = eventDetailId.toString(),
                rawLatitude = originalGpsData.latitude ?: 0.0,
                rawLongitude = originalGpsData.longitude ?: 0.0,
                rawAltitude = originalGpsData.altitude,
                rawAccuracy = originalGpsData.accuracy,
                rawTime = originalGpsData.timestamp ?: 0L,
                rawSpeed = originalGpsData.speed,
                correctedLatitude = correctedLatitude,
                correctedLongitude = correctedLongitude,
                correctedAltitude = correctedAltitude,
                lastUpdated = currentTime,
                heading = originalGpsData.bearing.toDouble(), // bearing을 heading으로 사용
                distanceCovered = distanceCovered,
                cumulativeTime = cumulativeTime,
                farthestCpId = checkpointInfo.first,
                farthestCpIndex = checkpointInfo.second,
                cumulativeTimeAtFarthestCp = checkpointInfo.third
            )
            
            // Redis Hash에 저장
            saveToRedisHash(redisKey, locationData)
            
            logger.info("실시간 위치 정보 저장 완료: userId=$userId, 누적거리=${String.format("%.2f", distanceCovered)}m")
            
            return locationData
            
        } catch (e: Exception) {
            logger.error("실시간 위치 정보 저장 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            throw RuntimeException("실시간 위치 정보 저장에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 실시간 위치 정보를 Redis Hash에서 조회합니다.
     * 
     * @param userId 사용자 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 실시간 위치 정보 DTO (없으면 null)
     */
    fun getRealtimeLocation(userId: String, eventDetailId: String): RealtimeLocationDto? {
        val redisKey = generateRedisKey(userId, eventDetailId)
        
        return try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            val allFields = hashOps.entries(redisKey)
            
            if (allFields.isEmpty()) {
                logger.info("실시간 위치 정보 없음: userId=$userId, eventDetailId=$eventDetailId")
                return null
            }
            
            // Hash에서 데이터 추출하여 DTO 생성
            RealtimeLocationDto(
                userId = allFields["userId"] ?: userId,
                eventId = allFields["eventId"] ?: "",
                eventDetailId = allFields["eventDetailId"] ?: eventDetailId,
                rawLatitude = allFields["rawLatitude"]?.toDoubleOrNull() ?: 0.0,
                rawLongitude = allFields["rawLongitude"]?.toDoubleOrNull() ?: 0.0,
                rawAltitude = allFields["rawAltitude"]?.toDoubleOrNull(),
                rawAccuracy = allFields["rawAccuracy"]?.toDoubleOrNull(),
                rawTime = allFields["rawTime"]?.toLongOrNull() ?: 0L,
                rawSpeed = allFields["rawSpeed"]?.toDoubleOrNull(),
                correctedLatitude = allFields["correctedLatitude"]?.toDoubleOrNull() ?: 0.0,
                correctedLongitude = allFields["correctedLongitude"]?.toDoubleOrNull() ?: 0.0,
                correctedAltitude = allFields["correctedAltitude"]?.toDoubleOrNull(),
                lastUpdated = allFields["lastUpdated"]?.toLongOrNull() ?: 0L,
                heading = allFields["heading"]?.toDoubleOrNull(),
                distanceCovered = allFields["distanceCovered"]?.toDoubleOrNull(),
                cumulativeTime = allFields["cumulativeTime"]?.toLongOrNull(),
                farthestCpId = allFields["farthestCpId"],
                farthestCpIndex = allFields["farthestCpIndex"]?.toIntOrNull(),
                cumulativeTimeAtFarthestCp = allFields["cumulativeTimeAtFarthestCp"]?.toLongOrNull()
            )
            
        } catch (e: Exception) {
            logger.error("실시간 위치 정보 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            null
        }
    }
    
    /**
     * 모든 사용자의 실시간 위치 정보를 조회합니다.
     * 
     * @param eventDetailId 이벤트 상세 ID
     * @return 실시간 위치 정보 리스트
     */
    fun getAllRealtimeLocations(eventDetailId: String): List<RealtimeLocationDto> {
        val pattern = "$LOCATION_KEY_PREFIX*:$eventDetailId"
        
        return try {
            val keys = redisTemplate.keys(pattern)
            val locations = mutableListOf<RealtimeLocationDto>()
            
            keys?.forEach { key ->
                val userId = extractUserIdFromKey(key)
                if (userId != null) {
                    val location = getRealtimeLocation(userId, eventDetailId)
                    if (location != null) {
                        locations.add(location)
                    }
                }
            }
            
            logger.info("실시간 위치 정보 전체 조회 완료: eventDetailId=$eventDetailId, ${locations.size}개")
            locations
            
        } catch (e: Exception) {
            logger.error("실시간 위치 정보 전체 조회 실패: eventDetailId=$eventDetailId", e)
            emptyList()
        }
    }
    
    /**
     * Redis 키 생성
     */
    private fun generateRedisKey(userId: String, eventDetailId: String): String {
        return "$LOCATION_KEY_PREFIX$userId:$eventDetailId"
    }
    
    /**
     * 기존 위치 정보 조회
     */
    private fun getExistingLocation(redisKey: String): RealtimeLocationDto? {
        return try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            val allFields = hashOps.entries(redisKey)
            
            if (allFields.isEmpty()) return null
            
            RealtimeLocationDto(
                userId = allFields["userId"] ?: "",
                eventId = allFields["eventId"] ?: "",
                eventDetailId = allFields["eventDetailId"] ?: "",
                rawLatitude = allFields["rawLatitude"]?.toDoubleOrNull() ?: 0.0,
                rawLongitude = allFields["rawLongitude"]?.toDoubleOrNull() ?: 0.0,
                rawAltitude = allFields["rawAltitude"]?.toDoubleOrNull(),
                rawAccuracy = allFields["rawAccuracy"]?.toDoubleOrNull(),
                rawTime = allFields["rawTime"]?.toLongOrNull() ?: 0L,
                rawSpeed = allFields["rawSpeed"]?.toDoubleOrNull(),
                correctedLatitude = allFields["correctedLatitude"]?.toDoubleOrNull() ?: 0.0,
                correctedLongitude = allFields["correctedLongitude"]?.toDoubleOrNull() ?: 0.0,
                correctedAltitude = allFields["correctedAltitude"]?.toDoubleOrNull(),
                lastUpdated = allFields["lastUpdated"]?.toLongOrNull() ?: 0L,
                heading = allFields["heading"]?.toDoubleOrNull(),
                distanceCovered = allFields["distanceCovered"]?.toDoubleOrNull(),
                cumulativeTime = allFields["cumulativeTime"]?.toLongOrNull(),
                farthestCpId = allFields["farthestCpId"],
                farthestCpIndex = allFields["farthestCpIndex"]?.toIntOrNull(),
                cumulativeTimeAtFarthestCp = allFields["cumulativeTimeAtFarthestCp"]?.toLongOrNull()
            )
        } catch (e: Exception) {
            logger.warn("기존 위치 정보 조회 실패: $redisKey", e)
            null
        }
    }
    
    /**
     * 누적 거리 계산
     */
    private fun calculateDistanceCovered(
        existingLocation: RealtimeLocationDto?,
        currentLatitude: Double,
        currentLongitude: Double
    ): Double {
        return if (existingLocation != null) {
            val distance = GeoUtils.calculateDistance(
                existingLocation.correctedLatitude,
                existingLocation.correctedLongitude,
                currentLatitude,
                currentLongitude
            )
            (existingLocation.distanceCovered ?: 0.0) + distance
        } else {
            0.0
        }
    }
    
    /**
     * 누적 시간 계산
     */
    private fun calculateCumulativeTime(
        existingLocation: RealtimeLocationDto?,
        currentTimestamp: Long
    ): Long {
        return if (existingLocation != null) {
            val timeDiff = currentTimestamp - existingLocation.rawTime
            (existingLocation.cumulativeTime ?: 0L) + timeDiff
        } else {
            0L
        }
    }
    
    /**
     * 체크포인트 도달 여부 확인
     * 
     * @return Triple<체크포인트ID, 체크포인트인덱스, 누적시간>
     */
    private fun checkCheckpointReach(
        eventDetailId: Long,
        latitude: Double,
        longitude: Double,
        distanceCovered: Double,
        existingLocation: RealtimeLocationDto?
    ): Triple<String?, Int?, Long?> {
        
        return try {
            // 거리 기반 체크포인트 계산
            val checkpointIndex = (distanceCovered / CHECKPOINT_INTERVAL_METERS).toInt()
            
            // 기존 체크포인트와 비교
            val currentFarthestIndex = existingLocation?.farthestCpIndex ?: -1
            
            if (checkpointIndex > currentFarthestIndex) {
                // 새로운 체크포인트 도달
                val checkpointId = when (checkpointIndex) {
                    0 -> "START"
                    else -> "CP$checkpointIndex"
                }
                
                val cumulativeTime = existingLocation?.cumulativeTime ?: 0L
                
                logger.info("새로운 체크포인트 도달: $checkpointId, 누적거리=${String.format("%.2f", distanceCovered)}m")
                
                Triple(checkpointId, checkpointIndex, cumulativeTime)
            } else {
                // 기존 체크포인트 정보 유지
                Triple(
                    existingLocation?.farthestCpId,
                    existingLocation?.farthestCpIndex,
                    existingLocation?.cumulativeTimeAtFarthestCp
                )
            }
            
        } catch (e: Exception) {
            logger.warn("체크포인트 도달 확인 실패: eventDetailId=$eventDetailId", e)
            Triple(null, null, null)
        }
    }
    
    /**
     * Redis Hash에 데이터 저장
     */
    private fun saveToRedisHash(redisKey: String, locationData: RealtimeLocationDto) {
        try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            
            // Hash 필드 맵 생성
            val hashMap = mutableMapOf<String, String>()
            hashMap["userId"] = locationData.userId
            hashMap["eventId"] = locationData.eventId
            hashMap["eventDetailId"] = locationData.eventDetailId
            hashMap["rawLatitude"] = locationData.rawLatitude.toString()
            hashMap["rawLongitude"] = locationData.rawLongitude.toString()
            hashMap["correctedLatitude"] = locationData.correctedLatitude.toString()
            hashMap["correctedLongitude"] = locationData.correctedLongitude.toString()
            hashMap["lastUpdated"] = locationData.lastUpdated.toString()
            hashMap["rawTime"] = locationData.rawTime.toString()
            
            // 선택적 필드 추가
            locationData.rawAltitude?.let { hashMap["rawAltitude"] = it.toString() }
            locationData.rawAccuracy?.let { hashMap["rawAccuracy"] = it.toString() }
            locationData.rawSpeed?.let { hashMap["rawSpeed"] = it.toString() }
            locationData.correctedAltitude?.let { hashMap["correctedAltitude"] = it.toString() }
            locationData.heading?.let { hashMap["heading"] = it.toString() }
            locationData.distanceCovered?.let { hashMap["distanceCovered"] = it.toString() }
            locationData.cumulativeTime?.let { hashMap["cumulativeTime"] = it.toString() }
            locationData.farthestCpId?.let { hashMap["farthestCpId"] = it }
            locationData.farthestCpIndex?.let { hashMap["farthestCpIndex"] = it.toString() }
            locationData.cumulativeTimeAtFarthestCp?.let { hashMap["cumulativeTimeAtFarthestCp"] = it.toString() }
            
            // Hash 저장
            hashOps.putAll(redisKey, hashMap)
            
            // TTL 설정
            redisTemplate.expire(redisKey, LOCATION_TTL_HOURS, TimeUnit.HOURS)
            
            logger.debug("Redis Hash 저장 완료: $redisKey, ${hashMap.size}개 필드")
            
        } catch (e: Exception) {
            logger.error("Redis Hash 저장 실패: $redisKey", e)
            throw RuntimeException("Redis Hash 저장 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
    
    /**
     * Redis 키에서 사용자 ID 추출
     */
    private fun extractUserIdFromKey(key: String): String? {
        return try {
            val keyWithoutPrefix = key.removePrefix(LOCATION_KEY_PREFIX)
            val parts = keyWithoutPrefix.split(":")
            if (parts.size >= 2) parts[0] else null
        } catch (e: Exception) {
            logger.warn("키에서 사용자 ID 추출 실패: $key", e)
            null
        }
    }
} 