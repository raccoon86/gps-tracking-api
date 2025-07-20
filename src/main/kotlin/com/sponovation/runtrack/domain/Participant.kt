package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 참가자 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 이벤트에 참가하는 사용자의 상세 정보를 관리합니다.
 * 하나의 사용자는 여러 이벤트에 참가할 수 있고, 각 이벤트에서 다른 상세 정보를 가질 수 있습니다.
 * 
 * 주요 기능:
 * - 이벤트별 참가자 정보 관리 (나이, 성별, 배번 등)
 * - 참가 상태 및 경기 상태 추적
 * - 관리자/사용자 메모 관리
 * - 트래커와의 다대다 관계 (여러 사용자가 한 참가자를 트래킹 가능)
 * 
 * 관계 매핑:
 * - Event: 다대일 관계 (여러 참가자가 하나의 이벤트에 속함)
 * - EventDetail: 다대일 관계 (여러 참가자가 하나의 이벤트 상세에 속함)
 * - User: 다대일 관계 (여러 참가자가 하나의 사용자에 속함)
 * - Tracker: 일대다 관계 (한 참가자를 여러 사용자가 트래킹)
 * 
 * @see Event 이벤트 정보
 * @see EventDetail 이벤트 상세 정보
 * @see User 사용자 정보
 * @see Tracker 트래커 정보
 */
@Entity
@Table(name = "participants")
@EntityListeners(AuditingEntityListener::class)
data class Participant(
    /**
     * 참가자 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 참가자가 참여하는 이벤트의 고유 식별자
     */
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 이벤트 상세 ID (Foreign Key)
     * 이 참가자가 참여하는 이벤트 상세(코스)의 고유 식별자
     */
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    @Column(name = "event_detail_id", nullable = false)
    val eventDetailId: Long,

    /**
     * 사용자 ID (Foreign Key)
     * 참가자에 해당하는 사용자의 고유 식별자
     */
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /**
     * 이름
     * 참가자의 실명
     */
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    @Column(name = "name", length = 50, nullable = false)
    val name: String,

    /**
     * 닉네임
     * 참가자의 별명 또는 표시명
     */
    @field:Size(max = 20, message = "닉네임은 20자를 초과할 수 없습니다")
    @Column(name = "nickname", length = 20, nullable = false)
    val nickname: String,

    /**
     * 프로필 이미지 URL
     * 참가자의 프로필 사진 URL
     */
    @field:Size(max = 255, message = "프로필 이미지 URL은 255자를 초과할 수 없습니다")
    @Column(name = "profile_image_url", length = 255, nullable = false)
    val profileImageUrl: String,

    /**
     * 배번(BIB Number)
     * 경기 중 참가자를 식별하는 번호
     */
    @field:Size(max = 10, message = "배번은 10자를 초과할 수 없습니다")
    @Column(name = "bib_number", length = 10)
    val bibNumber: String? = null,

    /**
     * 태그명
     * 참가자를 구분하거나 그룹핑하기 위한 태그
     */
    @field:Size(max = 50, message = "태그명은 50자를 초과할 수 없습니다")
    @Column(name = "tag_name", length = 50)
    val tagName: String? = null,

    /**
     * 경기 상태
     * 경기 진행 중, 완주, 기권 등의 실시간 상태
     */
    @field:Size(max = 20, message = "경기 상태는 20자를 초과할 수 없습니다")
    @Column(name = "race_status", length = 20, nullable = false)
    val raceStatus: String,

    /**
     * 국가
     * 참가자의 국적 (영어 국가명)
     */
    @field:Size(max = 50, message = "국가명은 50자를 초과할 수 없습니다")
    @Column(name = "country", length = 50, nullable = false)
    val country: String,

    /**
     * 생년월일
     * 참가자의 생년월일
     */
    @field:NotNull(message = "생년월일은 필수입니다")
    @field:Past(message = "생년월일은 과거여야 합니다")
    @Column(name = "birthday")
    val birthday: LocalDateTime? = null,

    /**
     * 성별
     * M: 남성, F: 여성
     */
    @Column(name = "gender", length = 10)
    val gender: String? = "M",

    /**
     * 참가 상태
     * 참가 신청, 승인, 거부, 취소 등의 상태
     */
    @field:NotBlank(message = "상태는 필수입니다")
    @field:Size(max = 30, message = "상태는 30자를 초과할 수 없습니다")
    @Column(name = "status", length = 30, nullable = false)
    val status: String,

    /**
     * 등록 일시
     * 참가 신청을 한 날짜와 시간
     */
    @Column(name = "registered_at")
    val registeredAt: LocalDateTime? = LocalDateTime.now(),

    /**
     * 관리자 메모
     * 관리자가 참가자에 대해 남기는 메모
     */
    @field:Size(max = 255, message = "관리자 메모는 255자를 초과할 수 없습니다")
    @Column(name = "admin_memo", length = 255)
    val adminMemo: String? = null,

    /**
     * 사용자 메모
     * 참가자(사용자)가 본인에 대해 남기는 메모
     */
    @field:Size(max = 255, message = "사용자 메모는 255자를 초과할 수 없습니다")
    @Column(name = "user_memo", length = 255)
    val userMemo: String? = null,

    /**
     * 비상 연락처
     * 참가자의 비상 연락처
     */
    @field:Size(max = 20, message = "비상 연락처는 20자를 초과할 수 없습니다")
    @Column(name = "emergency_contact", length = 20)
    val emergencyContact: String? = null,

    /**
     * 생성 일시
     * 참가자 정보가 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 참가자 정보가 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)