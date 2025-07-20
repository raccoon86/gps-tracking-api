package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 이벤트 상세 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 이벤트에 속한 각 코스의 상세 정보를 관리합니다.
 * 하나의 이벤트는 여러 이벤트 상세를 가질 수 있습니다 (예: 10km, 하프 마라톤, 풀 마라톤).
 * 
 * 주요 기능:
 * - 코스별 거리 및 코스명 관리
 * - GPX 파일 URL 관리
 * - 이벤트와의 연관 관계 관리
 * 
 * 관계 매핑:
 * - Event: 다대일 관계 (여러 이벤트 상세가 하나의 이벤트에 속함)
 * 
 * @see Event 이벤트 정보
 */
@Entity
@Table(name = "events_detail")
@EntityListeners(AuditingEntityListener::class)
data class EventDetail(
    /**
     * 이벤트 상세 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 이벤트 상세가 속한 이벤트의 고유 식별자
     */
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 이벤트 시작 일시
     * 이벤트가 시작되는 정확한 날짜와 시간
     */
    @field:NotNull(message = "시작 일시는 필수입니다")
    @Column(name = "start_date_time")
    val startDateTime: LocalDateTime? = null,

    /**
     * 이벤트 종료 일시
     * 이벤트가 종료되는 정확한 날짜와 시간
     */
    @field:NotNull(message = "종료 일시는 필수입니다")
    @Column(name = "end_date_time")
    val endDateTime: LocalDateTime? = null,

    /**
     * 코스 거리 (킬로미터)
     * 예: 5, 10, 21, 42.12 등
     */
    @field:Max(value = 1000, message = "거리는 1000km를 초과할 수 없습니다")
    @Column(name = "distance")
    val distance: Double? = null,

    /**
     * 코스명
     * 코스의 구체적인 명칭이나 카테고리
     * 예: "10km 일반부", "하프마라톤", "풀마라톤", "워킹 5km"
     */
    @field:Size(max = 30, message = "코스명은 30자를 초과할 수 없습니다")
    @Column(name = "course", length = 30)
    val course: String? = null,

    /**
     * GPX 파일 URL
     * 코스 경로 데이터가 포함된 GPX 파일의 URL 또는 파일 경로
     * 실시간 위치 보정 및 코스 데이터 조회에 사용
     */
    @field:Size(max = 255, message = "GPX 파일 URL은 255자를 초과할 수 없습니다")
    @Column(name = "gpx_file", length = 255, nullable = false)
    val gpxFile: String,

    /**
     * 생성 일시
     * 이벤트 상세가 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 이벤트 상세 정보가 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)