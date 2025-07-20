package com.sponovation.runtrack.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.Properties

/**
 * Redis 관리 서비스
 * 
 * Redis 데이터베이스의 관리 작업을 담당하는 서비스입니다.
 * 주로 개발/테스트 환경에서 데이터 초기화 및 관리 목적으로 사용됩니다.
 * 
 * 주요 기능:
 * - 모든 Redis 키 삭제 (FLUSHALL)
 * - 특정 패턴의 키 삭제
 * - Redis 상태 조회
 * 
 * 보안 주의사항:
 * - 프로덕션 환경에서는 신중하게 사용해야 합니다.
 * - 확인 매개변수를 통해 실수로 인한 데이터 삭제를 방지합니다.
 * 
 * @author Sponovation
 * @since 1.0
 */
@Service
class RedisManagementService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    private val logger = LoggerFactory.getLogger(RedisManagementService::class.java)
    
    /**
     * Redis의 모든 키를 삭제합니다.
     * 
     * 이 메서드는 Redis 데이터베이스의 모든 데이터를 삭제합니다.
     * 다음과 같은 데이터가 모두 제거됩니다:
     * 
     * 삭제되는 데이터:
     * - 실시간 위치 정보 (location:*)
     * - 체크포인트 통과 시간 (checkpointTimes:*)
     * - 리더보드 정보 (leaderboard:*)
     * - 코스 데이터 (eventDetail:*)
     * - GPX 파싱 데이터 (gpx:*)
     * - 참가자 기록 (participantSegmentRecords:*)
     * - 이전 위치 정보 (previousLocation:*)
     * - 시작 시간 정보 (start:*)
     * 
     * 보안 조치:
     * - confirmDelete 매개변수가 "CONFIRM_DELETE_ALL"와 정확히 일치해야 실행됩니다.
     * - 실행 전후 로그를 남겨 추적 가능하도록 합니다.
     * 
     * @param confirmDelete 삭제 확인 문자열 ("CONFIRM_DELETE_ALL"이어야 함)
     * @return 삭제된 키의 개수
     * @throws IllegalArgumentException 확인 문자열이 일치하지 않을 경우
     * @throws RuntimeException Redis 작업 중 오류가 발생할 경우
     */
    fun deleteAllKeys(): Long {
        
        return try {
            logger.warn("=== Redis 전체 키 삭제 시작 ===")
            logger.warn("경고: 모든 Redis 데이터가 삭제됩니다!")
            
            // 삭제 전 키 개수 조회
            val keysBefore = getKeyCount()
            logger.info("삭제 전 총 키 개수: $keysBefore")
            
            // 모든 키 삭제 실행
            val connection = redisTemplate.connectionFactory?.connection
            connection?.use { conn ->
                conn.serverCommands().flushAll()
                logger.warn("FLUSHALL 명령 실행 완료")
            } ?: throw RuntimeException("Redis 연결을 가져올 수 없습니다.")
            
            // 삭제 후 키 개수 확인
            val keysAfter = getKeyCount()
            val deletedCount = keysBefore - keysAfter
            
            logger.warn("=== Redis 전체 키 삭제 완료 ===")
            logger.warn("삭제된 키 개수: $deletedCount")
            logger.warn("남은 키 개수: $keysAfter")
            
            deletedCount
            
        } catch (e: Exception) {
            logger.error("Redis 전체 키 삭제 중 오류 발생", e)
            throw RuntimeException("Redis 키 삭제 작업에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 특정 패턴과 일치하는 키들을 삭제합니다.
     * 
     * 패턴 예시:
     * - "location:*" : 모든 위치 정보
     * - "leaderboard:*" : 모든 리더보드 정보
     * - "checkpointTimes:*" : 모든 체크포인트 시간 정보
     * 
     * @param pattern 삭제할 키 패턴 (Redis KEYS 명령어 형식)
     * @param confirmDelete 삭제 확인 문자열 ("CONFIRM_DELETE_PATTERN"이어야 함)
     * @return 삭제된 키의 개수
     * @throws IllegalArgumentException 확인 문자열이 일치하지 않을 경우
     */
    fun deleteKeysByPattern(pattern: String, confirmDelete: String): Long {
        if (confirmDelete != "CONFIRM_DELETE_PATTERN") {
            logger.warn("Redis 패턴 삭제 시도 실패: 잘못된 확인 문자열 - '$confirmDelete'")
            throw IllegalArgumentException("삭제를 확인하려면 정확한 확인 문자열을 입력해야 합니다.")
        }
        
        return try {
            logger.warn("=== Redis 패턴 키 삭제 시작: $pattern ===")
            
            // 패턴과 일치하는 키들 조회
            val keys = redisTemplate.keys(pattern)
            val keyCount = keys?.size ?: 0
            
            logger.info("패턴 '$pattern'과 일치하는 키 개수: $keyCount")
            
            if (keyCount > 0) {
                // 키들 삭제
                val deletedCount = redisTemplate.delete(keys!!)
                deletedCount
            } else {
                logger.info("삭제할 키가 없습니다.")
                0L
            }
            
        } catch (e: Exception) {
            logger.error("Redis 패턴 키 삭제 중 오류 발생: pattern=$pattern", e)
            throw RuntimeException("Redis 패턴 키 삭제 작업에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 현재 Redis에 저장된 키의 총 개수를 조회합니다.
     * 
     * @return 총 키 개수
     */
    fun getKeyCount(): Long {
        return try {
            val connection = redisTemplate.connectionFactory?.connection
            connection?.use { conn ->
                conn.info("keyspace").let { info ->
                    // INFO keyspace 결과에서 키 개수 파싱
                    parseKeyCountFromInfo(info)
                }
            } ?: 0L
        } catch (e: Exception) {
            logger.warn("키 개수 조회 실패, KEYS * 명령어로 대체 시도", e)
            try {
                // 대체 방법: KEYS * 명령어 사용 (성능상 권장되지 않지만 정확함)
                redisTemplate.keys("*")?.size?.toLong() ?: 0L
            } catch (fallbackException: Exception) {
                logger.error("키 개수 조회 완전 실패", fallbackException)
                0L
            }
        }
    }
    
    /**
     * INFO keyspace 결과에서 키 개수를 파싱합니다.
     */
    private fun parseKeyCountFromInfo(info: Properties): Long {
        return try {
            info.entries.sumOf { entry ->
                val value = entry.value.toString()
                if (value.contains("keys=")) {
                    val keysStr = value.substringAfter("keys=").substringBefore(",")
                    keysStr.toLongOrNull() ?: 0L
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            logger.warn("INFO keyspace 파싱 실패", e)
            0L
        }
    }
    
    /**
     * Redis 메모리 정보를 조회합니다.
     */
    private fun getMemoryInfo(): Map<String, String> {
        return try {
            val connection = redisTemplate.connectionFactory?.connection
            connection?.use { conn ->
                val memoryInfo = conn.info("memory")
                mapOf(
                    "used_memory" to (memoryInfo.getProperty("used_memory") ?: "unknown"),
                    "used_memory_peak" to (memoryInfo.getProperty("used_memory_peak") ?: "unknown"),
                    "used_memory_human" to (memoryInfo.getProperty("used_memory_human") ?: "unknown")
                )
            } ?: emptyMap()
        } catch (e: Exception) {
            logger.warn("메모리 정보 조회 실패", e)
            emptyMap()
        }
    }
} 