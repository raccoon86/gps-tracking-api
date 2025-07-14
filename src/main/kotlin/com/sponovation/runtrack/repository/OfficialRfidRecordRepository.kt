package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.OfficialRfidRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface OfficialRfidRecordRepository : JpaRepository<OfficialRfidRecord, Long> {
    
    /**
     * 특정 참가자의 모든 기록을 통과 시간순으로 조회
     */
    fun findByEventParticipantIdOrderByPassTime(@Param("participantId") participantId: Long): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 모든 기록을 누적 시간순으로 조회
     */
    fun findByEventParticipantIdOrderByCumulativeDuration(@Param("participantId") participantId: Long): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 체크포인트별 기록 조회
     */
    fun findByEventParticipantIdAndCpIdOrderByPassTime(
        @Param("participantId") participantId: Long,
        @Param("cpId") cpId: String
    ): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 최신 기록 조회
     */
    fun findLatestByEventParticipantId(@Param("participantId") participantId: Long): OfficialRfidRecord?
    
    /**
     * 특정 참가자의 최종 기록 조회
     */
    fun findFinalRecordByEventParticipantId(@Param("participantId") participantId: Long): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 기록 수 조회
     */
    fun countByEventParticipantId(@Param("participantId") participantId: Long): Long
    
    /**
     * 특정 체크포인트의 모든 기록 조회 (시간순)
     */
    fun findByCpIdOrderByPassTime(@Param("cpId") cpId: String): List<OfficialRfidRecord>
    
    /**
     * 특정 체크포인트의 모든 기록 조회 (누적 시간순)
     */
    fun findByCpIdOrderByCumulativeDuration(@Param("cpId") cpId: String): List<OfficialRfidRecord>
    
    /**
     * 특정 시간 범위의 모든 기록 조회
     */
    fun findByPassTimeBetweenOrderByPassTime(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<OfficialRfidRecord>
    
    /**
     * 최종 기록만 조회
     */
    fun findByIsFinalRecordTrueOrderByCumulativeDuration(): List<OfficialRfidRecord>
    
    /**
     * 완주 기록만 조회
     */
    fun findByFinishTimeIsNotNullOrderByFinishTime(): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 완주 여부 확인
     */
    fun existsByEventParticipantIdAndIsFinalRecordTrue(@Param("participantId") participantId: Long): Boolean
    
    /**
     * 특정 참가자의 특정 체크포인트 통과 여부 확인
     */
    fun existsByEventParticipantIdAndCpId(
        @Param("participantId") participantId: Long,
        @Param("cpId") cpId: String
    ): Boolean
    
    /**
     * 특정 체크포인트의 총 통과자 수 조회
     */
    fun countByCpId(@Param("cpId") cpId: String): Long
    
    /**
     * 특정 체크포인트의 완주자 수 조회
     */
    fun countByCpIdAndIsFinalRecordTrue(@Param("cpId") cpId: String): Long
    
    /**
     * 특정 시간 이후의 기록 조회
     */
    fun findByPassTimeAfterOrderByPassTime(@Param("time") time: LocalDateTime): List<OfficialRfidRecord>
    
    /**
     * 특정 시간 이전의 기록 조회
     */
    fun findByPassTimeBeforeOrderByPassTime(@Param("time") time: LocalDateTime): List<OfficialRfidRecord>
    
    /**
     * 특정 누적 시간 이상의 기록 조회
     */
    fun findByCumulativeDurationGreaterThanEqualOrderByCumulativeDuration(
        @Param("duration") duration: Int
    ): List<OfficialRfidRecord>
    
    /**
     * 특정 누적 시간 이하의 기록 조회
     */
    fun findByCumulativeDurationLessThanEqualOrderByCumulativeDuration(
        @Param("duration") duration: Int
    ): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 체크포인트 진행 상황 조회
     */
    @Query("""
        SELECT r FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId 
        ORDER BY r.passTime ASC
    """)
    fun findProgressByParticipantId(@Param("participantId") participantId: Long): List<OfficialRfidRecord>
    
    /**
     * 특정 참가자의 최고 기록 조회
     */
    @Query("""
        SELECT r FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId 
        AND r.cumulativeDuration = (
            SELECT MIN(r2.cumulativeDuration) 
            FROM OfficialRfidRecord r2 
            WHERE r2.eventParticipantId = :participantId
        )
    """)
    fun findBestTimeByParticipantId(@Param("participantId") participantId: Long): OfficialRfidRecord?
    
    /**
     * 특정 체크포인트의 최고 기록 조회
     */
    @Query("""
        SELECT r FROM OfficialRfidRecord r 
        WHERE r.cpId = :cpId 
        AND r.cumulativeDuration = (
            SELECT MIN(r2.cumulativeDuration) 
            FROM OfficialRfidRecord r2 
            WHERE r2.cpId = :cpId
        )
    """)
    fun findBestTimeByCpId(@Param("cpId") cpId: String): OfficialRfidRecord?
    
    /**
     * 특정 참가자의 평균 기록 조회
     */
    @Query("""
        SELECT AVG(r.cumulativeDuration) 
        FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId
    """)
    fun findAverageTimeByParticipantId(@Param("participantId") participantId: Long): Double?
    
    /**
     * 특정 체크포인트의 평균 기록 조회
     */
    @Query("""
        SELECT AVG(r.cumulativeDuration) 
        FROM OfficialRfidRecord r 
        WHERE r.cpId = :cpId
    """)
    fun findAverageTimeByCpId(@Param("cpId") cpId: String): Double?
    
    /**
     * 특정 참가자의 기록 삭제
     */
    fun deleteByEventParticipantId(@Param("participantId") participantId: Long): Int
    
    /**
     * 특정 체크포인트의 모든 기록 삭제
     */
    fun deleteByCpId(@Param("cpId") cpId: String): Int
    
    /**
     * 특정 시간 이전의 기록 삭제
     */
    fun deleteByPassTimeBefore(@Param("time") time: LocalDateTime): Int
    
    /**
     * 특정 참가자의 중복 기록 제거
     */
    @Query("""
        DELETE FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId 
        AND r.cpId = :cpId 
        AND r.passTime < :keepTime
    """)
    fun removeDuplicateRecords(
        @Param("participantId") participantId: Long,
        @Param("cpId") cpId: String,
        @Param("keepTime") keepTime: LocalDateTime
    ): Int
    
    /**
     * 특정 참가자의 기록 통계 조회
     */
    @Query("""
        SELECT 
            COUNT(r) as recordCount,
            MIN(r.cumulativeDuration) as bestTime,
            MAX(r.cumulativeDuration) as worstTime,
            AVG(r.cumulativeDuration) as averageTime
        FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId
    """)
    fun getRecordStatisticsByParticipantId(@Param("participantId") participantId: Long): List<Array<Any>>
    
    /**
     * 특정 체크포인트의 순위 조회
     */
    @Query("""
        SELECT r, 
               ROW_NUMBER() OVER (ORDER BY r.cumulativeDuration ASC) as rank
        FROM OfficialRfidRecord r 
        WHERE r.cpId = :cpId
        ORDER BY r.cumulativeDuration ASC
    """)
    fun getRankingByCpId(@Param("cpId") cpId: String): List<Array<Any>>
    
    /**
     * 특정 참가자의 체크포인트별 순위 조회
     */
    @Query("""
        SELECT r.cpId, 
               r.cumulativeDuration,
               ROW_NUMBER() OVER (PARTITION BY r.cpId ORDER BY r.cumulativeDuration ASC) as rank
        FROM OfficialRfidRecord r 
        WHERE r.eventParticipantId = :participantId
        ORDER BY r.passTime ASC
    """)
    fun getParticipantRankingsByCheckpoint(@Param("participantId") participantId: Long): List<Array<Any>>
} 