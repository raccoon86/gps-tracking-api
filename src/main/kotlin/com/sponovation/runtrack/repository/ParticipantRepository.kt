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
     * 이벤트 상세 ID와 사용자 ID로 참가자 조회
     */
    fun findByEventDetailIdAndUserId(eventDetailId: Long, userId: Long): Participant?

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
        @Param("eventDetailId") eventDetailId: Long?,
        @Param("search") search: String?,
        @Param("cursorBibNumber") cursorBibNumber: String?,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("limit") limit: Pageable
    ): List<Participant>
} 