package com.sponovation.runtrack.domain

import com.sponovation.runtrack.enums.EventParticipationStatus
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 대회 참가자 정보를 저장하는 엔티티
 * 
 * 특정 사용자가 특정 이벤트에 참가할 때의 정보를 관리합니다.
 * 참가 등록 정보, 배번, 참가 상태 등을 포함합니다.
 * 
 * 주요 기능:
 * - 사용자와 이벤트 간의 참가 관계 관리
 * - 대회 참가자 번호 (배번) 관리
 * - 참가 상태 및 기록 관리
 * - 참가 등록 정보 관리
 * 
 * 관계 매핑:
 * - User: 다대일 관계 (여러 참가 기록이 하나의 사용자에 속함)
 * - Event: 다대일 관계 (여러 참가자가 하나의 이벤트에 참가)
 * 
 * @see User 사용자 정보
 * @see Event 이벤트 정보
 */
@Entity
@Table(
    name = "event_participants",
    indexes = [
        Index(name = "idx_event_participants_user", columnList = "userId"),
        Index(name = "idx_event_participants_event", columnList = "eventId"),
        Index(name = "idx_event_participants_status", columnList = "participationStatus"),
        Index(name = "idx_event_participants_bib", columnList = "bibNumber"),
        Index(name = "idx_event_participants_registered_at", columnList = "registeredAt"),
        Index(name = "idx_event_participants_user_event", columnList = "userId,eventId")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_participants_user_event", columnNames = ["userId", "eventId"]),
        UniqueConstraint(name = "uk_event_participants_bib_event", columnNames = ["bibNumber", "eventId"])
    ]
)
data class EventParticipant(
    /**
     * 대회 참가자 고유 식별자 (Primary Key)
     * Long을 사용하여 자동 증가 식별자 보장
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_participant_id")
    val eventParticipantId: Long = 0L,

    /**
     * 사용자 ID (Foreign Key)
     * 참가자의 사용자 정보를 참조
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /**
     * 이벤트 ID (Foreign Key)
     * 참가한 이벤트 정보를 참조
     */
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 참가자 번호 (배번)
     * topRankers에서 bibNumber 필드로 사용
     * 각 이벤트별로 고유한 번호 부여
     */
    @Column(name = "bib_number", nullable = false, length = 20)
    val bibNumber: String,

    /**
     * 참가 상태
     * REGISTERED: 등록 완료
     * IN_PROGRESS: 진행 중
     * FINISHED: 완주
     * DNF: 완주하지 못함
     * DNS: 출발하지 않음
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "participation_status", nullable = false)
    val participationStatus: EventParticipationStatus = EventParticipationStatus.REGISTERED,

    /**
     * 참가 카테고리
     * 예: "마라톤", "하프마라톤", "10km", "5km" 등
     */
    @Column(name = "category", nullable = true, length = 50)
    val category: String? = null,

    /**
     * 팀 이름 (팀 참가인 경우)
     * 개인 참가인 경우 null
     */
    @Column(name = "team_name", nullable = true, length = 100)
    val teamName: String? = null,

    /**
     * 예상 완주 시간 (분 단위)
     * 참가자가 신청할 때 예상하는 완주 시간
     */
    @Column(name = "expected_finish_time", nullable = true)
    val expectedFinishTimeMinutes: Int? = null,

    /**
     * 실제 완주 시간 (초 단위)
     * 실제로 완주한 시간, 완주하지 못한 경우 null
     */
    @Column(name = "actual_finish_time", nullable = true)
    val actualFinishTimeSeconds: Int? = null,

    /**
     * 시작 시간
     * 해당 참가자의 공식 시작 시간
     */
    @Column(name = "start_time", nullable = true)
    val startTime: LocalDateTime? = null,

    /**
     * 완주 시간
     * 해당 참가자의 공식 완주 시간
     */
    @Column(name = "finish_time", nullable = true)
    val finishTime: LocalDateTime? = null,

    /**
     * 순위
     * 해당 카테고리에서의 최종 순위
     */
    @Column(name = "final_rank", nullable = true)
    val finalRank: Int? = null,

    /**
     * 성별별 순위
     * 같은 성별 내에서의 순위
     */
    @Column(name = "gender_rank", nullable = true)
    val genderRank: Int? = null,

    /**
     * 연령대별 순위
     * 같은 연령대 내에서의 순위
     */
    @Column(name = "age_group_rank", nullable = true)
    val ageGroupRank: Int? = null,

    /**
     * 특별 필요사항
     * 예: 휠체어 사용, 시각 장애인 가이드 필요 등
     */
    @Column(name = "special_requirements", nullable = true, columnDefinition = "TEXT")
    val specialRequirements: String? = null,

    /**
     * 비상 연락처
     * 참가자의 비상 연락처 정보
     */
    @Column(name = "emergency_contact", nullable = true, length = 200)
    val emergencyContact: String? = null,

    /**
     * 참가 등록 시간
     * 참가자가 이벤트에 등록한 시간
     */
    @Column(name = "registered_at", nullable = false)
    val registeredAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 생성 일시
     * 참가자 정보가 시스템에 등록된 시간
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 참가자 정보가 마지막으로 수정된 시간
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 참가자가 완주했는지 확인
     */
    fun isFinished(): Boolean {
        return participationStatus == EventParticipationStatus.FINISHED
    }

    /**
     * 참가자가 현재 진행 중인지 확인
     */
    fun isInProgress(): Boolean {
        return participationStatus == EventParticipationStatus.IN_PROGRESS
    }

    /**
     * 참가자가 출발했는지 확인
     */
    fun hasStarted(): Boolean {
        return participationStatus != EventParticipationStatus.DNS &&
               participationStatus != EventParticipationStatus.REGISTERED
    }

    /**
     * 팀 참가 여부 확인
     */
    fun isTeamParticipant(): Boolean {
        return !teamName.isNullOrBlank()
    }

    /**
     * 특별 필요사항이 있는지 확인
     */
    fun hasSpecialRequirements(): Boolean {
        return !specialRequirements.isNullOrBlank()
    }

    /**
     * 실제 완주 시간을 포맷된 문자열로 반환
     */
    fun getFormattedFinishTime(): String? {
        return actualFinishTimeSeconds?.let {
            val hours = it / 3600
            val minutes = (it % 3600) / 60
            val seconds = it % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    /**
     * 예상 완주 시간을 포맷된 문자열로 반환
     */
    fun getFormattedExpectedTime(): String? {
        return expectedFinishTimeMinutes?.let {
            val hours = it / 60
            val minutes = it % 60
            String.format("%02d:%02d", hours, minutes)
        }
    }

    /**
     * 참가 상태를 한국어로 반환
     */
    fun getStatusDisplay(): String {
        return when (participationStatus) {
            EventParticipationStatus.REGISTERED -> "등록 완료"
            EventParticipationStatus.IN_PROGRESS -> "진행 중"
            EventParticipationStatus.FINISHED -> "완주"
            EventParticipationStatus.DNF -> "완주하지 못함"
            EventParticipationStatus.DNS -> "출발하지 않음"
        }
    }

    /**
     * 페이스 계산 (분/km)
     */
    fun getPacePerKm(distanceKm: Double): Double? {
        return actualFinishTimeSeconds?.let { finishTimeSeconds ->
            if (distanceKm > 0) {
                (finishTimeSeconds / 60.0) / distanceKm
            } else null
        }
    }

    /**
     * 평균 속도 계산 (km/h)
     */
    fun getAverageSpeed(distanceKm: Double): Double? {
        return actualFinishTimeSeconds?.let { finishTimeSeconds ->
            if (finishTimeSeconds > 0) {
                (distanceKm * 3600) / finishTimeSeconds
            } else null
        }
    }

    /**
     * 참가자 정보 요약
     */
    fun getSummary(): String {
        return "참가자 $bibNumber ($category) - ${getStatusDisplay()}"
    }

    /**
     * 엔티티 업데이트 시 자동으로 updatedAt 필드를 현재 시간으로 설정
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
} 