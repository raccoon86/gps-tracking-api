package com.sponovation.runtrack.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 체크포인트 통과 시간 관리 서비스
 * 
 * Redis Hash 자료구조를 사용하여 참가자의 체크포인트 통과 시간을 기록하고 조회합니다.
 * 
 * Redis Key 패턴: checkpointTimes:{userId}:{eventId}:{eventDetailId}
 * Hash Fields: 체크포인트 ID -> Unix Timestamp
 */
@Service
class CheckpointTimesService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    private val logger = LoggerFactory.getLogger(CheckpointTimesService::class.java)
    
    companion object {
        private const val CHECKPOINT_TIMES_KEY_PREFIX = "checkpointTimes"
        private const val PREVIOUS_LOCATION_KEY_PREFIX = "previousLocation"
        private const val KEY_SEPARATOR = ":"
        private const val PREVIOUS_LOCATION_TTL_HOURS = 24L
    }
    
    /**
     * 이전 위치 데이터 클래스
     */
    data class PreviousLocationData(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double? = null,
        val timestamp: Long,
        val distanceFromStart: Double? = null
    )

    /**
     * 이전 위치 저장
     */
    fun savePreviousLocation(
        userId: String,
        eventId: String,
        eventDetailId: String,
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        timestamp: Long,
        distanceFromStart: Double? = null
    ) {
        val key = generatePreviousLocationKey(userId, eventId, eventDetailId)
        
        try {
            
            // JSON 형태로 저장
            val locationJson = "{\"latitude\":$latitude,\"longitude\":$longitude,\"altitude\":$altitude,\"timestamp\":$timestamp,\"distanceFromStart\":$distanceFromStart}"
            redisTemplate.opsForValue().set(key, locationJson, PREVIOUS_LOCATION_TTL_HOURS, TimeUnit.HOURS)
            
            logger.debug("이전 위치 저장 완료: userId=$userId, eventDetailId=$eventDetailId, " +
                "위치=($latitude, $longitude), 시간=$timestamp")
                
        } catch (e: Exception) {
            logger.error("이전 위치 저장 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            throw e
        }
    }

    /**
     * 이전 위치 조회
     */
    fun getPreviousLocation(
        userId: String,
        eventId: String,
        eventDetailId: String
    ): PreviousLocationData? {
        val key = generatePreviousLocationKey(userId, eventId, eventDetailId)
        
        return try {
            val locationJson = redisTemplate.opsForValue().get(key) as? String
            if (locationJson != null) {
                val latitude = locationJson.substringAfter("\"latitude\":").substringBefore(",").toDouble()
                val longitude = locationJson.substringAfter("\"longitude\":").substringBefore(",").toDouble()
                val altitudeStr = locationJson.substringAfter("\"altitude\":").substringBefore(",")
                val altitude = if (altitudeStr == "null") null else altitudeStr.toDouble()
                val timestamp = locationJson.substringAfter("\"timestamp\":").substringBefore(",").toLong()
                val distanceStr = locationJson.substringAfterLast("\"distanceFromStart\":").substringBefore("}")
                val distanceFromStart = if (distanceStr == "null") null else distanceStr.toDouble()
                
                PreviousLocationData(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    timestamp = timestamp,
                    distanceFromStart = distanceFromStart
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("이전 위치 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            null
        }
    }

    /**
     * 체크포인트 통과 시간 기록
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID
     * @param passTimeSeconds 통과 시간 (Unix Timestamp)
     */
    fun recordCheckpointPassTime(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String,
        passTimeSeconds: Long
    ) {
        val key = generateCheckpointTimesKey(userId, eventId, eventDetailId)
        
        try {
            redisTemplate.opsForHash<String, String>().put(key, checkpointId, passTimeSeconds.toString())
            
            logger.info("체크포인트 통과 시간 기록 성공: " +
                "userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId, passTime=$passTimeSeconds")
                
        } catch (e: Exception) {
            logger.error("체크포인트 통과 시간 기록 실패: " +
                "userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            throw e
        }
    }
    
    /**
     * 참가자의 모든 체크포인트 통과 시간 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 체크포인트 ID -> 통과 시간 Map
     */
    fun getAllCheckpointPassTimes(
        userId: Long,
        eventId: Long,
        eventDetailId: Long
    ): Map<String, Long> {
        val key = generateCheckpointTimesKey(userId.toString(), eventId.toString(), eventDetailId.toString())
        
        return try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            val rawData = hashOps.entries(key)
            
            rawData.mapNotNull { (checkpointId, passTimeStr) ->
                passTimeStr.toLongOrNull()?.let { passTime ->
                    checkpointId to passTime
                }
            }.toMap()
            
        } catch (e: Exception) {
            logger.error("모든 체크포인트 통과 시간 조회 실패: " +
                "userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId", e)
            emptyMap()
        }
    }
    
    /**
     * 체크포인트 통과 여부 확인
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID
     * @return 통과 여부
     */
    fun hasPassedCheckpoint(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String
    ): Boolean {
        val key = generateCheckpointTimesKey(userId, eventId, eventDetailId)
        
        return try {
            redisTemplate.opsForHash<String, String>().hasKey(key, checkpointId)
        } catch (e: Exception) {
            logger.error("체크포인트 통과 여부 확인 실패: " +
                "userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            false
        }
    }
    
    /**
     * 체크포인트 통과 시간 Redis Key 생성
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return Redis Key
     */
    private fun generateCheckpointTimesKey(
        userId: String,
        eventId: String,
        eventDetailId: String
    ): String {
        return "$CHECKPOINT_TIMES_KEY_PREFIX$KEY_SEPARATOR$userId$KEY_SEPARATOR$eventId$KEY_SEPARATOR$eventDetailId"
    }

    /**
     * 이전 위치 Redis 키 생성
     */
    private fun generatePreviousLocationKey(userId: String, eventId: String, eventDetailId: String): String {
        return "$PREVIOUS_LOCATION_KEY_PREFIX$KEY_SEPARATOR$userId$KEY_SEPARATOR$eventId$KEY_SEPARATOR$eventDetailId"
    }
} 