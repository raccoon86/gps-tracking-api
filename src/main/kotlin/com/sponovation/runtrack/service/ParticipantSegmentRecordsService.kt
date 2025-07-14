package com.sponovation.runtrack.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * 참가자 구간별 기록 관리 서비스
 * 
 * Redis Hash 자료구조를 사용하여 참가자의 구간별 기록과 누적 기록을 관리합니다.
 * 
 * Redis Key 패턴: participantSegmentRecords:{userId}:{eventId}:{eventDetailId}
 * Hash Fields:
 *   - "{checkpointId}_duration": 구간 소요 시간 (초)
 *   - "{checkpointId}_cumulative": 누적 소요 시간 (초)
 * 
 * 예시:
 * Key: participantSegmentRecords:user123:eventA:detail456
 * Fields:
 *   "CP1_duration": "1530"   // Start ~ CP1 구간 소요 시간 (초)
 *   "CP1_cumulative": "1530" // Start ~ CP1 누적 소요 시간 (초)
 *   "CP2_duration": "1440"   // CP1 ~ CP2 구간 소요 시간 (초)
 *   "CP2_cumulative": "2970" // Start ~ CP2 누적 소요 시간 (초)
 */
@Service
class ParticipantSegmentRecordsService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val checkpointTimesService: CheckpointTimesService
) {
    
    private val logger = LoggerFactory.getLogger(ParticipantSegmentRecordsService::class.java)
    
    companion object {
        private const val SEGMENT_RECORDS_KEY_PREFIX = "participantSegmentRecords"
        private const val KEY_SEPARATOR = ":"
        private const val DURATION_SUFFIX = "_duration"
        private const val CUMULATIVE_SUFFIX = "_cumulative"
    }
    
    /**
     * 체크포인트 통과 시간을 기반으로 구간별 기록 계산 및 저장
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID
     * @param passTime 체크포인트 통과 시간 (Unix Timestamp)
     * @param startTime 시작 시간 (Unix Timestamp)
     */
    fun calculateAndSaveSegmentRecord(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        checkpointId: String,
        passTime: Long,
        startTime: Long
    ) {
        val key = generateSegmentRecordsKey(userId.toString(), eventId.toString(), eventDetailId.toString())
        
        try {
            // 누적 시간 계산 (시작 시간부터 현재 체크포인트까지)
            val cumulativeTime = passTime - startTime
            
            // 구간 시간 계산 (이전 체크포인트부터 현재 체크포인트까지)
            val segmentTime = calculateSegmentTime(userId, eventId, eventDetailId, checkpointId, passTime, startTime)
            
            val hashOps = redisTemplate.opsForHash<String, String>()
            
            // 구간 소요 시간 저장
            hashOps.put(key, "${checkpointId}${DURATION_SUFFIX}", segmentTime.toString())
            
            // 누적 소요 시간 저장
            hashOps.put(key, "${checkpointId}${CUMULATIVE_SUFFIX}", cumulativeTime.toString())
            
            logger.info("구간별 기록 저장 성공: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId, segmentTime=${segmentTime}초, cumulativeTime=${cumulativeTime}초")
                
        } catch (e: Exception) {
            logger.error("구간별 기록 저장 실패: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            throw e
        }
    }
    
    /**
     * 구간 소요 시간 계산
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 현재 체크포인트 ID
     * @param currentPassTime 현재 체크포인트 통과 시간
     * @param startTime 시작 시간
     * @return 구간 소요 시간 (초)
     */
    private fun calculateSegmentTime(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        checkpointId: String,
        currentPassTime: Long,
        startTime: Long
    ): Long {
        // 모든 체크포인트 통과 시간을 조회
        val allPassTimes = checkpointTimesService.getAllCheckpointPassTimes(userId, eventId, eventDetailId)
        
        // 시간 순으로 정렬
        val sortedPassTimes = allPassTimes.entries.sortedBy { it.value }
        
        // 현재 체크포인트 이전의 체크포인트 찾기
        val currentIndex = sortedPassTimes.indexOfFirst { it.key == checkpointId }
        
        return if (currentIndex <= 0) {
            // 첫 번째 체크포인트인 경우 시작 시간부터 계산
            currentPassTime - startTime
        } else {
            // 이전 체크포인트부터 현재 체크포인트까지 계산
            val previousPassTime = sortedPassTimes[currentIndex - 1].value
            currentPassTime - previousPassTime
        }
    }
    
    /**
     * 체크포인트 통과 시간 기반 자동 구간 기록 업데이트
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param startTime 시작 시간 (Unix Timestamp)
     */
    fun updateSegmentRecordsFromCheckpoints(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        startTime: Long
    ) {
        try {
            // 모든 체크포인트 통과 시간 조회
            val allPassTimes = checkpointTimesService.getAllCheckpointPassTimes(userId, eventId, eventDetailId)
            
            // 시간 순으로 정렬
            val sortedPassTimes = allPassTimes.entries.sortedBy { it.value }
            
            // 각 체크포인트에 대해 구간 기록 계산 및 저장
            sortedPassTimes.forEach { (checkpointId, passTime) ->
                calculateAndSaveSegmentRecord(userId, eventId, eventDetailId, checkpointId, passTime, startTime)
            }
            
            logger.info("전체 구간 기록 업데이트 완료: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointCount=${sortedPassTimes.size}")
                
        } catch (e: Exception) {
            logger.error("전체 구간 기록 업데이트 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            throw e
        }
    }
    
    /**
     * 특정 체크포인트의 구간 소요 시간 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID
     * @return 구간 소요 시간 (초) 또는 null
     */
    fun getSegmentDuration(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String
    ): Long? {
        val key = generateSegmentRecordsKey(userId, eventId, eventDetailId)
        val field = "${checkpointId}${DURATION_SUFFIX}"
        
        return try {
            val duration = redisTemplate.opsForHash<String, String>().get(key, field)
            duration?.toLongOrNull()
        } catch (e: Exception) {
            logger.error("구간 소요 시간 조회 실패: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            null
        }
    }
    
    /**
     * 특정 체크포인트의 누적 소요 시간 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID
     * @return 누적 소요 시간 (초) 또는 null
     */
    fun getCumulativeTime(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String
    ): Long? {
        val key = generateSegmentRecordsKey(userId, eventId, eventDetailId)
        val field = "${checkpointId}${CUMULATIVE_SUFFIX}"
        
        return try {
            val cumulativeTime = redisTemplate.opsForHash<String, String>().get(key, field)
            cumulativeTime?.toLongOrNull()
        } catch (e: Exception) {
            logger.error("누적 소요 시간 조회 실패: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            null
        }
    }
    
    /**
     * 모든 구간별 기록 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 체크포인트 ID -> (구간 시간, 누적 시간) Map
     */
    fun getAllSegmentRecords(
        userId: String,
        eventId: String,
        eventDetailId: String
    ): Map<String, Pair<Long, Long>> {
        val key = generateSegmentRecordsKey(userId, eventId, eventDetailId)
        
        return try {
            val hashOps = redisTemplate.opsForHash<String, String>()
            val allData = hashOps.entries(key)
            
            // 체크포인트별로 그룹핑
            val groupedData = allData.entries.groupBy { entry ->
                when {
                    entry.key.endsWith(DURATION_SUFFIX) -> 
                        entry.key.removeSuffix(DURATION_SUFFIX)
                    entry.key.endsWith(CUMULATIVE_SUFFIX) -> 
                        entry.key.removeSuffix(CUMULATIVE_SUFFIX)
                    else -> entry.key
                }
            }
            
            // 각 체크포인트별로 구간 시간과 누적 시간 매핑
            groupedData.mapNotNull { (checkpointId, entries) ->
                var segmentTime: Long? = null
                var cumulativeTime: Long? = null
                
                entries.forEach { entry ->
                    when {
                        entry.key.endsWith(DURATION_SUFFIX) -> 
                            segmentTime = entry.value.toLongOrNull()
                        entry.key.endsWith(CUMULATIVE_SUFFIX) -> 
                            cumulativeTime = entry.value.toLongOrNull()
                    }
                }
                
                if (segmentTime != null && cumulativeTime != null) {
                    checkpointId to Pair(segmentTime!!, cumulativeTime!!)
                } else null
            }.toMap()
            
        } catch (e: Exception) {
            logger.error("모든 구간별 기록 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            emptyMap()
        }
    }
    
    /**
     * 구간별 기록 삭제
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointId 체크포인트 ID (null인 경우 모든 기록 삭제)
     */
    fun deleteSegmentRecords(
        userId: String,
        eventId: String,
        eventDetailId: String,
        checkpointId: String? = null
    ) {
        val key = generateSegmentRecordsKey(userId, eventId, eventDetailId)
        
        try {
            if (checkpointId != null) {
                // 특정 체크포인트 기록 삭제
                val hashOps = redisTemplate.opsForHash<String, String>()
                hashOps.delete(key, "${checkpointId}${DURATION_SUFFIX}")
                hashOps.delete(key, "${checkpointId}${CUMULATIVE_SUFFIX}")
                
                logger.info("특정 구간별 기록 삭제 완료: userId=$userId, eventDetailId=$eventDetailId, " +
                    "checkpointId=$checkpointId")
            } else {
                // 모든 기록 삭제
                redisTemplate.delete(key)
                
                logger.info("모든 구간별 기록 삭제 완료: userId=$userId, eventDetailId=$eventDetailId")
            }
            
        } catch (e: Exception) {
            logger.error("구간별 기록 삭제 실패: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointId=$checkpointId", e)
            throw e
        }
    }
    
    /**
     * 구간별 기록 통계 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 구간별 기록 통계 정보
     */
    fun getSegmentRecordStats(
        userId: String,
        eventId: String,
        eventDetailId: String
    ): Map<String, Any> {
        try {
            val allRecords = getAllSegmentRecords(userId, eventId, eventDetailId)
            
            if (allRecords.isEmpty()) {
                return emptyMap()
            }
            
            val segmentTimes = allRecords.values.map { it.first }
            val totalTime = allRecords.values.maxOfOrNull { it.second } ?: 0L
            
            return mapOf(
                "totalCheckpoints" to allRecords.size as Any,
                "totalTime" to totalTime as Any,
                "fastestSegment" to (segmentTimes.minOrNull() ?: 0L) as Any,
                "slowestSegment" to (segmentTimes.maxOrNull() ?: 0L) as Any,
                "averageSegmentTime" to (if (segmentTimes.isNotEmpty()) segmentTimes.average() else 0.0) as Any
            )
            
        } catch (e: Exception) {
            logger.error("구간별 기록 통계 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            return emptyMap()
        }
    }
    
    /**
     * 시간(초)을 시:분:초 형태의 문자열로 변환
     * 
     * @param seconds 시간 (초)
     * @return 시:분:초 형태의 문자열
     */
    fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
    
    /**
     * 구간별 기록 Redis Key 생성
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return Redis Key
     */
    private fun generateSegmentRecordsKey(
        userId: String,
        eventId: String,
        eventDetailId: String
    ): String {
        return "$SEGMENT_RECORDS_KEY_PREFIX$KEY_SEPARATOR$userId$KEY_SEPARATOR$eventId$KEY_SEPARATOR$eventDetailId"
    }
} 