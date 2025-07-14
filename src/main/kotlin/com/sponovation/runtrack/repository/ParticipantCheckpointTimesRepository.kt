package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.ParticipantCheckpointTimes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ParticipantCheckpointTimesRepository : JpaRepository<ParticipantCheckpointTimes, Long> {
    
    /**
     * 특정 참가자의 모든 체크포인트 기록을 누적 시간순으로 조회
     */
    fun findByParticipantIdOrderByCumulativeTime(@Param("participantId") participantId: Long): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 참가자의 모든 체크포인트 기록을 통과 시간순으로 조회
     */
    fun findByParticipantIdOrderByPassTime(@Param("participantId") participantId: Long): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 참가자의 특정 체크포인트 기록 조회
     */
    fun findByParticipantIdAndCheckpointId(
        @Param("participantId") participantId: Long,
        @Param("checkpointId") checkpointId: String
    ): ParticipantCheckpointTimes?
    
    /**
     * 특정 참가자의 최신 체크포인트 기록 조회
     */
    fun findLatestByParticipantId(@Param("participantId") participantId: Long): ParticipantCheckpointTimes?
    
    /**
     * 특정 참가자의 체크포인트 기록 수 조회
     */
    fun countByParticipantId(@Param("participantId") participantId: Long): Long
    
    /**
     * 특정 체크포인트의 모든 참가자 기록 조회 (누적 시간순)
     */
    fun findByCheckpointIdOrderByCumulativeTime(@Param("checkpointId") checkpointId: String): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 체크포인트의 모든 참가자 기록 조회 (통과 시간순)
     */
    fun findByCheckpointIdOrderByPassTime(@Param("checkpointId") checkpointId: String): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 체크포인트를 통과한 참가자 수 조회
     */
    fun countByCheckpointId(@Param("checkpointId") checkpointId: String): Long
    
    /**
     * 특정 시간 범위의 체크포인트 통과 기록 조회
     */
    fun findByPassTimeBetweenOrderByPassTime(
        @Param("startTime") startTime: Long,
        @Param("endTime") endTime: Long
    ): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 누적 시간 범위의 기록 조회
     */
    fun findByCumulativeTimeBetweenOrderByCumulativeTime(
        @Param("minTime") minTime: Int,
        @Param("maxTime") maxTime: Int
    ): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 참가자의 체크포인트 특정 시간 이후 기록 조회
     */
    fun findByParticipantIdAndPassTimeAfterOrderByPassTime(
        @Param("participantId") participantId: Long,
        @Param("afterTime") afterTime: Long
    ): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 참가자의 체크포인트 특정 시간 이전 기록 조회
     */
    fun findByParticipantIdAndPassTimeBeforeOrderByPassTime(
        @Param("participantId") participantId: Long,
        @Param("beforeTime") beforeTime: Long
    ): List<ParticipantCheckpointTimes>
    
    /**
     * 특정 참가자의 체크포인트 통과 여부 확인
     */
    fun existsByParticipantIdAndCheckpointId(
        @Param("participantId") participantId: Long,
        @Param("checkpointId") checkpointId: String
    ): Boolean
    
    /**
     * 특정 체크포인트의 최고 기록 조회
     */
    fun findFirstByCheckpointIdOrderByCumulativeTime(@Param("checkpointId") checkpointId: String): ParticipantCheckpointTimes?
    
    /**
     * 특정 체크포인트의 평균 누적 시간 조회
     */
    @Query("SELECT AVG(pct.cumulativeTime) FROM ParticipantCheckpointTimes pct WHERE pct.checkpointId = :checkpointId")
    fun findAverageCumulativeTimeByCheckpointId(@Param("checkpointId") checkpointId: String): Double?
    
    /**
     * 특정 체크포인트의 평균 구간 시간 조회
     */
    @Query("SELECT AVG(pct.segmentDuration) FROM ParticipantCheckpointTimes pct WHERE pct.checkpointId = :checkpointId")
    fun findAverageSegmentDurationByCheckpointId(@Param("checkpointId") checkpointId: String): Double?
    
    /**
     * 특정 참가자의 전체 진행 상황 조회
     */
    @Query("""
        SELECT pct.checkpointId, pct.passTime, pct.segmentDuration, pct.cumulativeTime
        FROM ParticipantCheckpointTimes pct
        WHERE pct.participantId = :participantId
        ORDER BY pct.passTime ASC
    """)
    fun findProgressByParticipantId(@Param("participantId") participantId: Long): List<Array<Any>>
    
    /**
     * 특정 체크포인트의 순위 조회
     */
    @Query("""
        SELECT pct, ROW_NUMBER() OVER (ORDER BY pct.cumulativeTime ASC) as rank
        FROM ParticipantCheckpointTimes pct
        WHERE pct.checkpointId = :checkpointId
        ORDER BY pct.cumulativeTime ASC
    """)
    fun getRankingByCheckpointId(@Param("checkpointId") checkpointId: String): List<Array<Any>>
    
    /**
     * 특정 참가자의 체크포인트별 순위 조회
     */
    @Query("""
        SELECT pct.checkpointId, pct.cumulativeTime,
               ROW_NUMBER() OVER (PARTITION BY pct.checkpointId ORDER BY pct.cumulativeTime ASC) as rank
        FROM ParticipantCheckpointTimes pct
        WHERE pct.participantId = :participantId
        ORDER BY pct.passTime ASC
    """)
    fun getParticipantRankingsByCheckpoint(@Param("participantId") participantId: Long): List<Array<Any>>
    
    /**
     * 특정 참가자의 성능 분석 데이터 조회
     */
    @Query("""
        SELECT pct FROM ParticipantCheckpointTimes pct
        WHERE pct.participantId = :participantId
        ORDER BY pct.passTime ASC
    """)
    fun findPerformanceAnalysisByParticipantId(@Param("participantId") participantId: Long): List<ParticipantCheckpointTimes>
    
    /**
     * 체크포인트별 통계 조회
     */
    @Query("""
        SELECT pct.checkpointId,
               COUNT(pct) as totalRecords,
               AVG(pct.cumulativeTime) as avgCumulativeTime,
               MIN(pct.cumulativeTime) as bestTime,
               MAX(pct.cumulativeTime) as worstTime,
               AVG(pct.segmentDuration) as avgSegmentDuration
        FROM ParticipantCheckpointTimes pct
        GROUP BY pct.checkpointId
        ORDER BY avgCumulativeTime ASC
    """)
    fun findCheckpointStatistics(): List<Array<Any>>
    
    /**
     * 참가자별 통계 조회
     */
    @Query("""
        SELECT pct.participantId,
               COUNT(pct) as totalCheckpoints,
               MAX(pct.cumulativeTime) as totalTime,
               AVG(pct.segmentDuration) as avgSegmentDuration
        FROM ParticipantCheckpointTimes pct
        GROUP BY pct.participantId
        ORDER BY totalTime ASC
    """)
    fun findParticipantStatistics(): List<Array<Any>>
    
    /**
     * 특정 참가자의 기록 삭제
     */
    fun deleteByParticipantId(@Param("participantId") participantId: Long): Int
    
    /**
     * 특정 체크포인트의 모든 기록 삭제
     */
    fun deleteByCheckpointId(@Param("checkpointId") checkpointId: String): Int
    
    /**
     * 특정 시간 이전의 기록 삭제
     */
    fun deleteByPassTimeBefore(@Param("beforeTime") beforeTime: Long): Int
} 