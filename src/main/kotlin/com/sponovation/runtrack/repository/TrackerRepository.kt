package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Participant
import com.sponovation.runtrack.domain.Tracker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 트래킹 중인 참가자 정보 Projection
 */
interface TrackedParticipantProjection {
    fun getParticipantId(): Long
    fun getName(): String?
    fun getNickname(): String?
    fun getBibNumber(): String?
    fun getCountry(): String?
    fun getProfileImage(): String?
    fun getTrackedAt(): LocalDateTime
}

/**
 * 트래커 엔티티에 대한 데이터 접근 계층
 * 
 * 사용자와 참가자 간의 트래킹 관계를 관리합니다.
 */
@Repository
interface TrackerRepository : JpaRepository<Tracker, Long> {

    /**
     * 사용자 ID, 이벤트 ID, 이벤트 상세 ID, 참가자 ID로 트래커 존재 여부 확인
     */
    fun existsByUserIdAndEventIdAndEventDetailIdAndParticipantId(
        userId: Long, 
        eventId: Long, 
        eventDetailId: Long, 
        participantId: Long
    ): Boolean

    /**
     * 사용자가 트래킹하는 모든 참가자 정보 조회 (Projection 사용)
     */
    @Query("""
        SELECT 
            p.userId as participantId,
            p.name as name,
            p.nickname as nickname,
            p.bibNumber as bibNumber,
            p.country as country,
            p.profileImageUrl as profileImage,
            t.createdAt as trackedAt
        FROM Tracker t 
        JOIN Participant p ON t.participantId = p.userId
        WHERE t.userId = :userId
        ORDER BY t.createdAt DESC
    """)
    fun findTrackedParticipantsByUserId(@Param("userId") userId: Long): List<TrackedParticipantProjection>

    /**
     * 특정 이벤트에서 사용자가 트래킹하는 참가자 정보 조회 (Projection 사용)
     */
    @Query("""
        SELECT 
            p.id as participantId,
            p.name as name,
            p.nickname as nickname,
            p.bibNumber as bibNumber,
            p.country as country,
            p.profileImageUrl as profileImage,
            t.createdAt as trackedAt
        FROM Tracker t 
        JOIN Participant p ON t.participantId = p.userId
        WHERE t.userId = :userId 
        AND t.eventId = :eventId 
        AND t.eventDetailId = :eventDetailId
        ORDER BY t.createdAt DESC
    """)
    fun findTrackedParticipantsByUserIdAndEvent(
        @Param("userId") userId: Long,
        @Param("eventId") eventId: Long,
        @Param("eventDetailId") eventDetailId: Long
    ): List<TrackedParticipantProjection>


    /**
     * 사용자 ID, 이벤트 ID, 이벤트 상세 ID, 참가자 ID로 트래커 삭제
     */
    @Modifying
    fun deleteByUserIdAndEventIdAndEventDetailIdAndParticipantId(
        userId: Long,
        eventId: Long,
        eventDetailId: Long,
        participantId: Long
    ): Int
} 