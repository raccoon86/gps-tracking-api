package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Gender
import com.sponovation.runtrack.domain.Participant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 참가자 엔티티에 대한 데이터 접근 계층
 * 
 * 참가자의 생성, 조회, 수정, 삭제 등의 데이터베이스 작업을 담당합니다.
 * 이벤트별, 사용자별, 상태별 참가자 조회와 통계 기능을 제공합니다.
 */
@Repository
interface ParticipantRepository : JpaRepository<Participant, Long> {

    fun existsByEventIdAndEventDetailIdAndUserId(eventId: Long, eventDetailId: Long, userId: Long): Boolean
    
    /**
     * 이벤트 ID로 참가자 목록 조회 (생성일시 내림차순, 페이징)
     */
    fun findByEventIdOrderByCreatedAtDesc(eventId: Long, pageable: Pageable): Page<Participant>
    
    /**
     * 이벤트 ID로 참가자 목록 조회
     */
    fun findByEventId(eventId: Long): List<Participant>
    
    /**
     * 이벤트 상세 ID로 참가자 목록 조회
     */
    fun findByEventDetailId(eventDetailId: Long): List<Participant>
    
    /**
     * 사용자 ID로 참가자 목록 조회
     */
    fun findByUserId(userId: Long): List<Participant>
    
    /**
     * 이벤트 ID와 사용자 ID로 참가자 조회
     */
    fun findByEventIdAndUserId(eventId: Long, userId: Long): List<Participant>
    
    /**
     * 이벤트 상세 ID와 사용자 ID로 참가자 조회
     */
    fun findByEventDetailIdAndUserId(eventDetailId: Long, userId: Long): Participant?
    
    /**
     * 참가 상태로 참가자 목록 조회
     */
    fun findByStatus(status: String): List<Participant>
    
    /**
     * 경기 상태로 참가자 목록 조회
     */
    fun findByRaceStatus(raceStatus: String): List<Participant>
    
    /**
     * 성별로 참가자 목록 조회
     */
    fun findByGender(gender: Gender): List<Participant>
    
    /**
     * 배번으로 참가자 조회
     */
    fun findByBibNumber(bibNumber: String): Participant?
    
    /**
     * 배번이 있는 참가자 목록 조회
     */
    fun findByBibNumberIsNotNull(): List<Participant>
    
    /**
     * 태그명으로 참가자 목록 조회
     */
    fun findByTagName(tagName: String): List<Participant>
    
    /**
     * 이벤트 ID별 참가자 수 조회
     */
    fun countByEventId(eventId: Long): Long
    
    /**
     * 이벤트 상세 ID별 참가자 수 조회
     */
    fun countByEventDetailId(eventDetailId: Long): Long
    
    /**
     * 참가 상태별 참가자 수 조회
     */
    fun countByStatus(status: String): Long
    
    /**
     * 경기 상태별 참가자 수 조회
     */
    fun countByRaceStatus(raceStatus: String): Long
    
    /**
     * 성별별 참가자 수 조회
     */
    fun countByGender(gender: Gender): Long
    
    /**
     * 나이 범위별 참가자 조회
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.age >= :minAge AND p.age <= :maxAge
    """)
    fun findByAgeBetween(@Param("minAge") minAge: Int, @Param("maxAge") maxAge: Int): List<Participant>
    
    /**
     * 이벤트별 성별 통계 조회
     */
    @Query("""
        SELECT p.gender, COUNT(p) 
        FROM Participant p 
        WHERE p.eventId = :eventId 
        GROUP BY p.gender
    """)
    fun getGenderStatsByEventId(@Param("eventId") eventId: Long): List<Array<Any>>
    
    /**
     * 이벤트별 나이 그룹 통계 조회
     */
    @Query("""
        SELECT 
            CASE 
                WHEN p.age <= 19 THEN 'YOUTH'
                WHEN p.age BETWEEN 20 AND 64 THEN 'ADULT' 
                WHEN p.age >= 65 THEN 'SENIOR'
                ELSE 'UNKNOWN'
            END as ageGroup,
            COUNT(p)
        FROM Participant p 
        WHERE p.eventId = :eventId AND p.age IS NOT NULL
        GROUP BY 
            CASE 
                WHEN p.age <= 19 THEN 'YOUTH'
                WHEN p.age BETWEEN 20 AND 64 THEN 'ADULT' 
                WHEN p.age >= 65 THEN 'SENIOR'
                ELSE 'UNKNOWN'
            END
    """)
    fun getAgeGroupStatsByEventId(@Param("eventId") eventId: Long): List<Array<Any>>
    
    /**
     * 이벤트별 참가 상태 통계 조회
     */
    @Query("""
        SELECT p.status, COUNT(p) 
        FROM Participant p 
        WHERE p.eventId = :eventId 
        GROUP BY p.status
    """)
    fun getStatusStatsByEventId(@Param("eventId") eventId: Long): List<Array<Any>>
    
    /**
     * 최근 등록된 참가자 조회 (등록 시간 기준)
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.registeredAt > :cutoffDate
        ORDER BY p.registeredAt DESC
    """)
    fun findRecentlyRegistered(
        @Param("cutoffDate") cutoffDate: LocalDateTime = LocalDateTime.now().minusDays(7)
    ): List<Participant>
    
    /**
     * 승인된 참가자 목록 조회 (이벤트별)
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.eventId = :eventId 
        AND (p.status = 'APPROVED' OR p.status = 'CONFIRMED')
        ORDER BY p.bibNumber ASC
    """)
    fun findApprovedParticipantsByEventId(@Param("eventId") eventId: Long): List<Participant>
    
    /**
     * 경기 진행 중인 참가자 목록 조회
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.raceStatus IN ('RACING', 'IN_PROGRESS')
        ORDER BY p.bibNumber ASC
    """)
    fun findRacingParticipants(): List<Participant>
    
    /**
     * 경기 완주한 참가자 목록 조회 (이벤트별)
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.eventId = :eventId 
        AND p.raceStatus IN ('FINISHED', 'COMPLETED')
        ORDER BY p.bibNumber ASC
    """)
    fun findFinishedParticipantsByEventId(@Param("eventId") eventId: Long): List<Participant>
    
    /**
     * 트래커가 가장 많은 인기 참가자 조회 (이벤트별)
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        LEFT JOIN p.trackers t
        WHERE p.eventId = :eventId 
        GROUP BY p.id
        ORDER BY COUNT(t) DESC
    """)
    fun findPopularParticipantsByEventId(@Param("eventId") eventId: Long): List<Participant>
    
    /**
     * 이벤트와 사용자 조합의 참가 여부 확인
     */
    fun existsByEventIdAndUserId(eventId: Long, userId: Long): Boolean
    
    /**
     * 이벤트 상세와 사용자 조합의 참가 여부 확인
     */
    fun existsByEventDetailIdAndUserId(eventDetailId: Long, userId: Long): Boolean
    
    /**
     * 배번 중복 확인 (이벤트별)
     */
    @Query("""
        SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END 
        FROM Participant p 
        WHERE p.eventId = :eventId AND p.bibNumber = :bibNumber
    """)
    fun existsBibNumberInEvent(@Param("eventId") eventId: Long, @Param("bibNumber") bibNumber: String): Boolean
    
    /**
     * 이벤트 ID로 모든 참가자 삭제
     */
    @Modifying
    @Query("DELETE FROM Participant p WHERE p.eventId = :eventId")
    fun deleteByEventId(@Param("eventId") eventId: Long): Int
    
    /**
     * 커서 기반 참가자 검색 (이름 또는 배번으로 검색)
     */
    @Query("""
        SELECT p 
        FROM Participant p 
        WHERE p.eventId = :eventId 
        AND (
            :search IS NULL 
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bibNumber) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        AND (
            :cursorBibNumber IS NULL 
            OR :cursorCreatedAt IS NULL
            OR (p.bibNumber > :cursorBibNumber)
            OR (p.bibNumber = :cursorBibNumber AND p.createdAt > :cursorCreatedAt)
        )
        ORDER BY p.bibNumber ASC, p.createdAt ASC
    """)
    fun searchParticipantsWithCursor(
        @Param("eventId") eventId: Long,
        @Param("search") search: String?,
        @Param("cursorBibNumber") cursorBibNumber: String?,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("limit") limit: Pageable
    ): List<Participant>
} 