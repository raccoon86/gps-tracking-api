package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 트래커 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 사용자가 특정 참가자를 추적(트래킹)하는 관계를 관리합니다.
 * 한 사용자는 여러 참가자를 트래킹할 수 있고, 한 참가자는 여러 사용자에게 트래킹될 수 있습니다.
 * 
 * 주요 기능:
 * - 사용자와 참가자 간의 트래킹 관계 관리
 * - 트래킹 관계 생성/해제 시점 추적
 * - 실시간 위치 추적을 위한 관심 목록 관리
 * 
 * 관계 매핑:
 * - User: 다대일 관계 (여러 트래커가 하나의 사용자에 속함)
 * - EventParticipant: 다대일 관계 (여러 트래커가 하나의 참가자를 추적)
 * 
 * @see User 사용자 정보
 * @see EventParticipant 이벤트 참가자 정보
 */
@Entity
@Table(
    name = "trackers",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_trackers_user_event_event_detail_participant",
            columnNames = ["user_id", "event_id", "event_detail_id", "participant_id"]
        )
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Tracker(
    /**
     * 트래커 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    
    /**
     * 이벤트 ID (Foreign Key)
     * 트래킹 대상이 되는 이벤트의 고유 식별자
     */
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 이벤트 상세 ID (Foreign Key)
     * 트래킹 대상이 되는 이벤트 상세의 고유 식별자
     */
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    @Column(name = "event_detail_id", nullable = false)
    val eventDetailId: Long,

    /**
     * 사용자 ID (Foreign Key)
     * 트래킹을 수행하는 사용자의 고유 식별자
     */
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /**
     * 참가자 ID (Foreign Key)
     * 트래킹 대상이 되는 참가자의 고유 식별자
     */
    @field:NotNull(message = "참가자 ID는 필수입니다")
    @field:Positive(message = "참가자 ID는 양수여야 합니다")
    @Column(name = "participant_id", nullable = false)
    val participantId: Long,

    /**
     * 생성 일시
     * 트래킹 관계가 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 트래킹 관계가 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)