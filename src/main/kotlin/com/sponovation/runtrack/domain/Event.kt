package com.sponovation.runtrack.domain

import com.sponovation.runtrack.enums.EventStatus
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 이벤트 정보를 저장하는 엔티티
 * 
 * 마라톤 대회, 러닝 이벤트, 스포츠 경기 등 각종 이벤트의 
 * 기본 정보와 참가 신청 관련 정보를 관리합니다.
 * 
 * 이벤트 생명주기:
 * - DRAFT: 초안 상태 (아직 공개되지 않음)
 * - SCHEDULED: 예정된 이벤트 (참가 신청 가능)
 * - COMPLETED: 완료된 이벤트
 * - UNCONFIRMED: 확정되지 않은 이벤트
 * - HOLD: 보류 상태 (일시적 중단)
 * - CANCELED: 취소된 이벤트
 */
@Entity
@Table(name = "events")
data class Event(
    /** 
     * 이벤트 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 이벤트 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 
     * 이벤트 이름
     * 사용자에게 표시되는 이벤트의 공식 명칭
     * 예: "2024 서울 마라톤 대회", "한강 러닝 페스티벌"
     */
    @Column(nullable = false, length = 200)
    val eventName: String,

    /** 
     * 이벤트 개최 날짜
     * 이벤트가 실제로 진행되는 날짜
     */
    @Column(nullable = false)
    val eventDate: LocalDate,

    /** 
     * 이벤트 상태
     * 현재 이벤트의 진행 상태를 나타내는 열거형 값
     * 
     * @see EventStatus 상태 열거형 정의
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventStatus: EventStatus,

    /** 
     * 이벤트 설명
     * 이벤트에 대한 상세 설명, 규칙, 주의사항 등을 포함
     */
    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    /** 
     * 참가 신청 시작일
     * 참가자들이 이벤트에 신청할 수 있는 시작 날짜
     */
    @Column
    val registrationStartDate: LocalDate? = null,

    /** 
     * 참가 신청 마감일
     * 참가자들이 이벤트에 신청할 수 있는 마지막 날짜
     */
    @Column
    val registrationEndDate: LocalDate? = null,

    /** 
     * 생성 일시
     * 이벤트가 시스템에 등록된 시간
     */
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 
     * 마지막 업데이트 일시
     * 이벤트 정보가 마지막으로 수정된 시간
     */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 현재 시간을 기준으로 이벤트가 진행 중인지 확인
     */
    fun isOngoing(): Boolean {
        val now = LocalDate.now()
        return eventDate.isEqual(now) && eventStatus == EventStatus.SCHEDULED
    }
    
    /**
     * 현재 시간을 기준으로 이벤트가 완료되었는지 확인
     */
    fun isFinished(): Boolean {
        val now = LocalDate.now()
        return eventDate.isBefore(now) || eventStatus == EventStatus.COMPLETED
    }
    
    /**
     * 현재 시간을 기준으로 참가 신청이 가능한지 확인
     */
    fun isRegistrationOpen(): Boolean {
        val now = LocalDate.now()
        return registrationStartDate != null && 
               registrationEndDate != null &&
               !now.isBefore(registrationStartDate) &&
               !now.isAfter(registrationEndDate) &&
               eventStatus == EventStatus.SCHEDULED
    }
    
    /**
     * 엔티티 업데이트 시 자동으로 updatedAt 필드를 현재 시간으로 설정
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
} 