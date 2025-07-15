package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Tracker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 트래커 엔티티에 대한 데이터 접근 계층
 * 
 * 사용자와 참가자 간의 트래킹 관계를 관리합니다.
 */
@Repository
interface TrackerRepository : JpaRepository<Tracker, Long> {

    /**
     * 사용자 ID와 참가자 ID로 트래커 존재 여부 확인
     */
    fun existsByUserIdAndParticipantId(userId: Long, participantId: Long): Boolean

    /**
     * 사용자 ID와 참가자 ID로 트래커 조회
     */
    fun findByUserIdAndParticipantId(userId: Long, participantId: Long): Tracker?

    /**
     * 사용자가 트래킹하는 모든 참가자 정보 조회
     */
    @Query("""
        SELECT p 
        FROM Tracker t 
        JOIN Participant p ON t.participantId = p.id
        WHERE t.userId = :userId
        ORDER BY t.createdAt DESC
    """)
    fun findParticipantsByUserId(@Param("userId") userId: Long): List<com.sponovation.runtrack.domain.Participant>

    /**
     * 사용자 ID로 트래커 목록 조회
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Tracker>

    /**
     * 참가자 ID로 트래커 목록 조회 (해당 참가자를 트래킹하는 모든 사용자)
     */
    fun findByParticipantId(participantId: Long): List<Tracker>

    /**
     * 사용자 ID와 참가자 ID로 트래커 삭제
     */
    @Modifying
    fun deleteByUserIdAndParticipantId(userId: Long, participantId: Long): Int

    /**
     * 사용자별 트래킹 수 조회
     */
    fun countByUserId(userId: Long): Long

    /**
     * 참가자별 트래킹 수 조회
     */
    fun countByParticipantId(participantId: Long): Long
} 