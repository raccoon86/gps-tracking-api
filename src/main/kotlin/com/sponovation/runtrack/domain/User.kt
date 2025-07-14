package com.sponovation.runtrack.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 사용자 정보를 저장하는 엔티티
 * 
 * 시스템에 등록된 사용자들의 기본 정보를 관리합니다.
 * 이벤트 참가자들의 신원 정보와 프로필 정보를 제공합니다.
 * 
 * 주요 기능:
 * - 사용자 기본 정보 (이름, 이메일, 전화번호 등)
 * - 프로필 이미지 관리
 * - 계정 상태 관리
 * - 개인정보 보호 설정
 * 
 * 관계 매핑:
 * - EventParticipant: 일대다 관계 (한 사용자가 여러 이벤트에 참가 가능)
 * 
 * @see com.sponovation.runtrack.domain.EventParticipant 이벤트 참가자 정보
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_email", columnList = "email"),
        Index(name = "idx_users_phone", columnList = "phoneNumber"),
        Index(name = "idx_users_nickname", columnList = "nickname"),
        Index(name = "idx_users_status", columnList = "status"),
        Index(name = "idx_users_created_at", columnList = "createdAt")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"]),
        UniqueConstraint(name = "uk_users_phone", columnNames = ["phoneNumber"])
    ]
)
data class User(
    /**
     * 사용자 고유 식별자 (Primary Key)
     * Long을 사용하여 자동 증가 식별자 보장
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    val userId: Long = 0L,

    /**
     * 사용자 이름 (실명)
     * 공식 기록 및 대회 참가자 표시에 사용
     * topRankers에서 name 필드로 사용
     */
    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    /**
     * 사용자 닉네임 (별명)
     * 일반적인 시스템 표시용 이름
     * 실명 대신 사용하여 개인정보 보호 가능
     */
    @Column(name = "nickname", nullable = false, length = 100)
    val nickname: String,

    /**
     * 이메일 주소
     * 로그인 및 계정 식별에 사용
     * 유니크 제약조건 적용
     */
    @Column(name = "email", nullable = false, length = 255)
    val email: String,

    /**
     * 전화번호
     * 긴급 연락 및 본인 확인에 사용
     * 유니크 제약조건 적용
     */
    @Column(name = "phone_number", nullable = true, length = 20)
    val phoneNumber: String? = null,

    /**
     * 프로필 이미지 URL
     * topRankers에서 profileImageUrl 필드로 사용
     * S3 또는 CDN URL을 저장
     */
    @Column(name = "profile_image_url", nullable = true, columnDefinition = "TEXT")
    val profileImageUrl: String? = null,

    /**
     * 생년월일
     * 연령대별 순위 분류 등에 활용
     */
    @Column(name = "birth_date", nullable = true)
    val birthDate: java.time.LocalDate? = null,

    /**
     * 성별
     * 성별별 순위 분류 등에 활용
     */
    @Column(name = "gender", nullable = true, length = 10)
    val gender: String? = null,

    /**
     * 계정 상태
     * ACTIVE: 활성화된 계정
     * INACTIVE: 비활성화된 계정 (휴면 계정)
     * SUSPENDED: 정지된 계정
     * DELETED: 삭제된 계정 (논리적 삭제)
     */
    @Column(name = "status", nullable = false, length = 20)
    val status: String = "ACTIVE",

    /**
     * 개인정보 공개 설정
     * PUBLIC: 모든 정보 공개
     * PRIVATE: 최소 정보만 공개
     * FRIENDS: 친구에게만 공개
     */
    @Column(name = "privacy_setting", nullable = false, length = 20)
    val privacySetting: String = "PUBLIC",

    /**
     * 마지막 로그인 시간
     * 계정 활성도 추적용
     */
    @Column(name = "last_login_at", nullable = true)
    val lastLoginAt: LocalDateTime? = null,

    /**
     * 생성 일시
     * 계정이 생성된 시간
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 사용자 정보가 마지막으로 수정된 시간
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 계정이 활성화되어 있는지 확인
     */
    fun isActive(): Boolean {
        return status == "ACTIVE"
    }

    /**
     * 프로필 이미지가 설정되어 있는지 확인
     */
    fun hasProfileImage(): Boolean {
        return !profileImageUrl.isNullOrBlank()
    }

    /**
     * 개인정보 공개 여부 확인
     */
    fun isPublicProfile(): Boolean {
        return privacySetting == "PUBLIC"
    }

    /**
     * 표시용 이름 반환
     * 개인정보 보호 설정에 따라 실명 또는 닉네임 반환
     */
    fun getDisplayName(): String {
        return if (isPublicProfile()) name else nickname
    }

    /**
     * 연령 계산
     */
    fun getAge(): Int? {
        return birthDate?.let { 
            val today = java.time.LocalDate.now()
            today.year - it.year - if (today.dayOfYear < it.dayOfYear) 1 else 0
        }
    }

    /**
     * 성별 표시용 문자열 반환
     */
    fun getGenderDisplay(): String {
        return when (gender?.uppercase()) {
            "M", "MALE" -> "남성"
            "F", "FEMALE" -> "여성"
            else -> "미설정"
        }
    }

    /**
     * 계정 상태 표시용 문자열 반환
     */
    fun getStatusDisplay(): String {
        return when (status.uppercase()) {
            "ACTIVE" -> "활성"
            "INACTIVE" -> "비활성"
            "SUSPENDED" -> "정지"
            "DELETED" -> "삭제"
            else -> "알 수 없음"
        }
    }

    /**
     * 최근 로그인 여부 확인 (30일 이내)
     */
    fun isRecentlyActive(): Boolean {
        return lastLoginAt?.let { 
            it.isAfter(LocalDateTime.now().minusDays(30))
        } ?: false
    }

    /**
     * 엔티티 업데이트 시 자동으로 updatedAt 필드를 현재 시간으로 설정
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
} 