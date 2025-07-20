package com.sponovation.runtrack.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * 대회 리더보드 관리 서비스
 * 
 * Redis Sorted Set 자료구조를 사용하여 참가자들의 실시간 순위를 관리합니다.
 * 
 * Redis Key 패턴: leaderboard:{eventDetailId}
 * Type: Sorted Set
 * Score 계산: (체크포인트 인덱스 * C) + 누적 시간 (초)
 * C = 360000 (100시간을 초 단위로 표현)
 * 
 * 예시:
 * Key: leaderboard:eventDetail456
 * Members (with Scores):
 *   "userABC": 1000000000 // Score: (0*C) + 0 (Start)
 *   "userDEF": 1000001400 // Score: (1*C) + 1400 (CP1까지 1400초)
 *   "userXYZ": 2000002500 // Score: (2*C) + 2500 (CP2까지 2500초)
 * 
 * 낮은 점수가 더 높은 순위 (1등)
 */
@Service
class LeaderboardService(
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    
    private val logger = LoggerFactory.getLogger(LeaderboardService::class.java)
    
    companion object {
        private const val LEADERBOARD_KEY_PREFIX = "leaderboard"
        private const val KEY_SEPARATOR = ":"
        private const val CHECKPOINT_WEIGHT = 360000L // 100시간을 초 단위로 표현 (100 * 60 * 60)
        private const val BASE_SCORE = 1000000000L // 10억 (기본 점수)
    }
    
    /**
     * 참가자의 리더보드 점수 업데이트
     * 
     * @param userId 사용자 ID (Long)
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointOrder 체크포인트 순서 (0부터 시작)
     * @param cumulativeTime 누적 소요 시간 (초)
     */
    fun updateLeaderboard(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        checkpointOrder: Int,
        cumulativeTime: Long
    ) {
        val key = generateLeaderboardKey(eventId.toString(), eventDetailId.toString())
        
        try {
            // 점수 계산: (체크포인트 순서 * 가중치) + 누적 시간 + 기본 점수
            val score = BASE_SCORE + (checkpointOrder * CHECKPOINT_WEIGHT) + cumulativeTime
            
            // Redis Sorted Set에 점수 저장
            redisTemplate.opsForZSet().add(key, userId, score.toDouble())
            
            logger.info("리더보드 업데이트 성공: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointOrder=$checkpointOrder, cumulativeTime=${cumulativeTime}초, score=$score")
                
        } catch (e: Exception) {
            logger.error("리더보드 업데이트 실패: userId=$userId, eventDetailId=$eventDetailId, " +
                "checkpointOrder=$checkpointOrder", e)
            throw e
        }
    }
    
    /**
     * 체크포인트 통과 시간 기반 자동 리더보드 업데이트
     * 
     * @param userId 사용자 ID (Long)
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param cpIndex 체크포인트 인덱스 (0부터 시작)
     * @param cumulativeTime 누적 소요 시간 (초)
     */
    fun updateLeaderboardFromCheckpoint(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        cpIndex: Int,
        cumulativeTime: Long
    ) {
        try {
            updateLeaderboard(userId, eventId, eventDetailId, cpIndex, cumulativeTime)
        } catch (e: Exception) {
            logger.error("체크포인트 기반 리더보드 업데이트 실패: userId=$userId, " +
                "eventDetailId=$eventDetailId, cpIndex=$cpIndex", e)
            throw e
        }
    }
    
    /**
     * 상위 3명의 랭커 userId(Long) 조회
     * 
     * leaderboard:{eventDetailId} Redis Sorted Set에서 해당 대회 코스(eventDetailId)의 상위 3명의 userId(Long)를 조회합니다.
     * 점수가 낮을수록 높은 순위입니다. (체크포인트 진행도 + 누적 시간 기준)
     * 
     * @param eventDetailId 이벤트 상세 ID (EventDetail ID)
     * @return 상위 3명의 userId(Long) 리스트 (순위순으로 정렬)
     */
    fun getTopRankers(eventId: Long, eventDetailId: Long): List<Long> {
        val key = generateLeaderboardKey(eventId.toString(), eventDetailId.toString())
        
        return try {
            logger.info("상위 3명 랭커 조회 시작: eventDetailId=$eventDetailId, key=$key")
            
            // Redis Sorted Set에서 점수 오름차순으로 상위 3명 조회 (낮은 점수가 높은 순위)
            val topRankings = redisTemplate.opsForZSet().range(key, 0, 2)
            
            val userIds = topRankings?.mapNotNull {
                when (it) {
                    is Long -> it
                    is String -> it.toLongOrNull()
                    else -> null
                }
            } ?: emptyList()
            
            logger.info("상위 3명 랭커 조회 완료: eventDetailId=$eventDetailId, " +
                "조회된 사용자 수=${userIds.size}, userIds=$userIds")
            
            userIds
            
        } catch (e: Exception) {
            logger.error("상위 3명 랭커 조회 실패: eventDetailId=$eventDetailId, key=$key", e)
            emptyList()
        }
    }
    
    /**
     * 상위 3명의 랭커 상세 정보 조회 (점수 포함)
     * 
     * @param eventDetailId 이벤트 상세 ID (EventDetail ID)
     * @return 상위 3명의 사용자 ID(Long)와 점수 정보 리스트
     */
    fun getTopRankersWithScores(eventId: String, eventDetailId: String): List<Pair<Long, Double>> {
        val key = generateLeaderboardKey(eventId, eventDetailId)
        
        return try {
            logger.info("상위 3명 랭커 상세 조회 시작: eventDetailId=$eventDetailId, key=$key")
            
            // Redis Sorted Set에서 점수 오름차순으로 상위 3명 조회 (점수 포함)
            val topRankings = redisTemplate.opsForZSet().rangeWithScores(key, 0, 2)
            logger.info("상위 3명 랭커 상세 조회 시작: topRankings=$topRankings")
            val rankerData = topRankings?.mapNotNull { tuple ->
                val member = tuple.value
                val userId = when (member) {
                    is Long -> member
                    is String -> member.toLongOrNull()
                    is Int -> member.toLong()
                    else -> null
                }
                userId?.let { Pair(userId, tuple.score!!) }
            } ?: emptyList()
            
            logger.info("상위 3명 랭커 상세 조회 완료: eventDetailId=$eventDetailId, " +
                "조회된 사용자 수=${rankerData.size}")
            
            rankerData
            
        } catch (e: Exception) {
            logger.error("상위 3명 랭커 상세 조회 실패: eventDetailId=$eventDetailId, key=$key", e)
            emptyList()
        }
    }
//
//    /**
//     * 전체 참가자 수 조회
//     *
//     * @param eventDetailId 이벤트 상세 ID
//     * @return 전체 참가자 수
//     */
//    fun getTotalParticipants(
//        eventDetailId: String
//    ): Long {
//        val key = generateLeaderboardKey(eventDetailId)
//
//        return try {
//            redisTemplate.opsForZSet().count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
//
//        } catch (e: Exception) {
//            logger.error("전체 참가자 수 조회 실패: eventDetailId=$eventDetailId", e)
//            0L
//        }
//    }
    
    /**
     * 체크포인트 ID에서 순서 추출
     * 
     * @param checkpointId 체크포인트 ID (예: "CP1", "CP2")
     * @return 체크포인트 순서 (0부터 시작)
     */
    private fun extractCheckpointOrder(checkpointId: String): Int {
        return try {
            // "CP1" -> 1, "CP2" -> 2 형태에서 숫자 추출
            val numberStr = checkpointId.replace(Regex("[^0-9]"), "")
            if (numberStr.isNotEmpty()) {
                val number = numberStr.toInt()
                number // 1부터 시작하는 체크포인트 번호를 그대로 사용
            } else {
                0
            }
        } catch (e: Exception) {
            logger.warn("체크포인트 순서 추출 실패: checkpointId=$checkpointId, 기본값 0 사용")
            0
        }
    }
    
    /**
     * 리더보드 Redis Key 생성
     * 
     * @param eventDetailId 이벤트 상세 ID
     * @return Redis Key
     */
    private fun generateLeaderboardKey(eventId: String, eventDetailId: String): String {
        return "$LEADERBOARD_KEY_PREFIX$KEY_SEPARATOR$eventId$KEY_SEPARATOR$eventDetailId"
    }
} 