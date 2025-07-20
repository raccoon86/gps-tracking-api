package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 이벤트 웨이포인트 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 GPX 생성을 위한 웨이포인트 설정을 관리합니다.
 * 각 이벤트와 이벤트 상세코스별로 다양한 지점들(체크포인트, 급수소, 의료소 등)의 
 * 위치 정보와 상세 정보를 저장합니다.
 * 
 * 주요 기능:
 * - 이벤트별 웨이포인트 위치 관리 (위도, 경도, 고도)
 * - 거리 및 순서 정보 관리
 * - 지점 유형별 분류 (체크포인트, 급수소, 의료소 등)
 * - GPX 파일 생성을 위한 기본 데이터 제공
 * 
 * 관계 매핑:
 * - Event: 다대일 관계 (여러 웨이포인트가 하나의 이벤트에 속함)
 * - EventDetail: 다대일 관계 (여러 웨이포인트가 하나의 이벤트 상세에 속함)
 * - User: 다대일 관계 (등록자/수정자)
 * 
 * @see Event 이벤트 정보
 * @see EventDetail 이벤트 상세 정보
 * @see User 사용자 정보
 */
@Entity
@Table(name = "event_waypoints")
@EntityListeners(AuditingEntityListener::class)
data class EventWaypoint(
    /**
     * 웨이포인트 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 웨이포인트가 속하는 이벤트의 고유 식별자
     */
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 이벤트 상세 ID (Foreign Key)
     * 이 웨이포인트가 속하는 이벤트 상세코스의 고유 식별자
     */
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    @Column(name = "event_detail_id", nullable = false)
    val eventDetailId: Long,

    /**
     * 체크포인트 ID
     * 웨이포인트와 연결된 체크포인트의 식별자
     */
    @field:NotBlank(message = "체크포인트 ID는 필수입니다")
    @field:Size(max = 50, message = "체크포인트 ID는 50자를 초과할 수 없습니다")
    @Column(name = "checkpoint_id", length = 50, nullable = false)
    val checkpointId: String,

    /**
     * 지점 이름
     * 웨이포인트의 표시명 (예: 반환점, 급수소1, 의료소)
     */
    @field:NotBlank(message = "지점 이름은 필수입니다")
    @field:Size(max = 50, message = "지점 이름은 50자를 초과할 수 없습니다")
    @Column(name = "name", length = 50, nullable = false)
    val name: String,

    /**
     * 위도
     * 웨이포인트의 위도 좌표 (소수점 7자리까지)
     */
    @field:NotNull(message = "위도는 필수입니다")
    @field:DecimalMin(value = "-90.0", message = "위도는 -90도 이상이어야 합니다")
    @field:DecimalMax(value = "90.0", message = "위도는 90도 이하여야 합니다")
    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    val latitude: BigDecimal,

    /**
     * 경도
     * 웨이포인트의 경도 좌표 (소수점 7자리까지)
     */
    @field:NotNull(message = "경도는 필수입니다")
    @field:DecimalMin(value = "-180.0", message = "경도는 -180도 이상이어야 합니다")
    @field:DecimalMax(value = "180.0", message = "경도는 180도 이하여야 합니다")
    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    val longitude: BigDecimal,

    /**
     * 고도
     * 웨이포인트의 해발 고도 (단위: 미터, 소수점 2자리까지)
     */
    @field:DecimalMin(value = "-500.0", message = "고도는 -500미터 이상이어야 합니다")
    @field:DecimalMax(value = "9000.0", message = "고도는 9000미터 이하여야 합니다")
    @Column(name = "altitude", precision = 6, scale = 2)
    val altitude: BigDecimal? = null,

    /**
     * 시작점으로부터 거리
     * 코스 시작점에서 이 웨이포인트까지의 거리 (단위: 미터)
     */
    @field:NotNull(message = "시작점으로부터 거리는 필수입니다")
    @field:DecimalMin(value = "0.0", message = "거리는 0 이상이어야 합니다")
    @Column(name = "distance_from_start", precision = 8, scale = 2, nullable = false)
    val distanceFromStart: BigDecimal,

    /**
     * 지점 순서
     * GPX 상의 웨이포인트 순서 (1부터 시작)
     */
    @field:NotNull(message = "지점 순서는 필수입니다")
    @field:Positive(message = "지점 순서는 양수여야 합니다")
    @Column(name = "sequence", nullable = false)
    val sequence: Int,

    /**
     * 지점 유형
     * 웨이포인트의 타입 (CHECKPOINT, WATER, MEDICAL, RETURN, ETC)
     */
    @field:NotBlank(message = "지점 유형은 필수입니다")
    @field:Size(max = 30, message = "지점 유형은 30자를 초과할 수 없습니다")
    @Column(name = "type", length = 30, nullable = false)
    val type: String,

    /**
     * 지점 설명
     * 웨이포인트에 대한 상세 설명
     */
    @field:Size(max = 255, message = "지점 설명은 255자를 초과할 수 없습니다")
    @Column(name = "description", length = 255)
    val description: String? = null,

    /**
     * 등록자 유저 ID
     * 이 웨이포인트를 등록한 사용자의 ID
     */
    @field:NotNull(message = "등록자 ID는 필수입니다")
    @field:Positive(message = "등록자 ID는 양수여야 합니다")
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    /**
     * 수정자 유저 ID
     * 이 웨이포인트를 마지막으로 수정한 사용자의 ID
     */
    @field:Positive(message = "수정자 ID는 양수여야 합니다")
    @Column(name = "updated_by")
    val updatedBy: Long? = null,

    /**
     * 생성 일시
     * 웨이포인트가 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 웨이포인트 정보가 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) 