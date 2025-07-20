package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 이벤트 공지사항 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 특정 이벤트와 관련된 공지사항을 관리합니다.
 * 이벤트 주최자가 참가자들에게 전달해야 할 중요한 정보, 변경사항, 
 * 긴급 공지 등을 체계적으로 관리하고 노출할 수 있습니다.
 * 
 * 주요 기능:
 * - 이벤트별 공지사항 관리 (일반, 긴급, 시스템 공지)
 * - 상단 고정 및 노출 여부 제어
 * - HTML 포함 가능한 리치 컨텐츠 지원
 * - 소프트 삭제를 통한 데이터 보존
 * - 작성자/수정자 추적
 * 
 * 공지 유형:
 * - GENERAL: 일반 공지사항
 * - URGENT: 긴급 공지사항 (우선 노출)
 * - SYSTEM: 시스템 관련 공지사항
 * 
 * 관계 매핑:
 * - Event: 다대일 관계 (여러 공지사항이 하나의 이벤트에 속함)
 * - User: 다대일 관계 (작성자/수정자)
 * 
 * @see Event 이벤트 정보
 * @see User 사용자 정보
 */
@Entity
@Table(name = "event_notices")
@EntityListeners(AuditingEntityListener::class)
data class EventNotice(
    /**
     * 공지사항 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 공지사항이 속하는 이벤트의 고유 식별자
     */
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 공지 유형
     * 공지사항의 분류 타입
     * - GENERAL: 일반 공지사항
     * - URGENT: 긴급 공지사항 (우선 노출)
     * - SYSTEM: 시스템 관련 공지사항
     */
    @field:NotBlank(message = "공지 유형은 필수입니다")
    @field:Size(max = 20, message = "공지 유형은 20자를 초과할 수 없습니다")
    @field:Pattern(
        regexp = "^(GENERAL|URGENT|SYSTEM)$",
        message = "공지 유형은 GENERAL, URGENT, SYSTEM 중 하나여야 합니다"
    )
    @Column(name = "type", length = 20, nullable = false)
    val type: String,

    /**
     * 공지 제목
     * 공지사항의 제목
     */
    @field:NotBlank(message = "공지 제목은 필수입니다")
    @field:Size(max = 100, message = "공지 제목은 100자를 초과할 수 없습니다")
    @Column(name = "title", length = 100, nullable = false)
    val title: String,

    /**
     * 공지 내용
     * 공지사항의 상세 내용 (HTML 포함 가능)
     */
    @field:NotBlank(message = "공지 내용은 필수입니다")
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    /**
     * 상단 고정 여부
     * true: 공지사항 목록 상단에 고정
     * false: 일반적인 정렬 순서대로 노출
     */
    @Column(name = "pinned", nullable = false)
    val pinned: Boolean = false,

    /**
     * 참가자에게 노출 여부
     * true: 참가자에게 노출
     * false: 관리자만 볼 수 있음 (임시저장 등)
     */
    @Column(name = "visible", nullable = false)
    val visible: Boolean = true,

    /**
     * 작성자 유저 ID
     * 이 공지사항을 작성한 사용자의 ID
     */
    @field:NotNull(message = "작성자 ID는 필수입니다")
    @field:Positive(message = "작성자 ID는 양수여야 합니다")
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    /**
     * 수정자 유저 ID
     * 이 공지사항을 마지막으로 수정한 사용자의 ID
     */
    @field:Positive(message = "수정자 ID는 양수여야 합니다")
    @Column(name = "updated_by")
    val updatedBy: Long? = null,

    /**
     * 작성 일시
     * 공지사항이 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 공지사항이 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 소프트 삭제 여부
     * true: 논리적으로 삭제된 상태 (실제 데이터는 보존)
     * false: 활성 상태
     */
    @Column(name = "deleted", nullable = false)
    val deleted: Boolean = false
) 