package com.sponovation.runtrack.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 이벤트 정보를 저장하는 엔티티
 * 
 * 마라톤 대회, 러닝 이벤트, 스포츠 경기 등 각종 이벤트의 
 * 기본 정보와 장소, 일정 정보를 관리합니다.
 */
@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener::class)
data class Event(
    /** 
     * 이벤트 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 이벤트 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /** 
     * 이벤트 이름
     * 사용자에게 표시되는 이벤트의 공식 명칭
     * 예: "2024 서울 마라톤 대회", "한강 러닝 페스티벌"
     */
    @field:NotBlank(message = "이벤트 이름은 필수입니다")
    @field:Size(max = 255, message = "이벤트 이름은 255자를 초과할 수 없습니다")
    @Column(name = "name", length = 255)
    val name: String? = null,

    /** 
     * 스포츠 종목
     * 해당 이벤트의 스포츠 종목 (예: 마라톤, 축구, 농구 등)
     */
    @field:Size(max = 30, message = "스포츠 종목은 30자를 초과할 수 없습니다")
    @Column(name = "sports", length = 30)
    val sports: String? = null,

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
     * 국가
     * 이벤트가 개최되는 국가 (ISO 국가 코드 또는 국가명)
     */
    @field:NotBlank(message = "국가는 필수입니다")
    @field:Size(max = 20, message = "국가는 20자를 초과할 수 없습니다")
    @Column(name = "country", length = 20)
    val country: String? = null,

    /** 
     * 도시
     * 이벤트가 개최되는 도시명
     */
    @field:NotBlank(message = "도시는 필수입니다")
    @field:Size(max = 255, message = "도시는 255자를 초과할 수 없습니다")
    @Column(name = "city", length = 255, nullable = false)
    val city: String,

    /** 
     * 주소
     * 이벤트 개최지의 상세 주소
     */
    @field:Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
    @Column(name = "address", length = 200)
    val address: String? = null,

    /** 
     * 장소명
     * 이벤트가 개최되는 구체적인 장소명 (예: 올림픽 공원, 잠실 주경기장)
     */
    @field:Size(max = 255, message = "장소명은 255자를 초과할 수 없습니다")
    @Column(name = "place", length = 255)
    val place: String? = null,

    /** 
     * 위도
     * 이벤트 개최지의 GPS 위도 좌표 (소수점 6자리)
     */
    @field:DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다")
    @field:DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다")
    @Column(name = "latitude", precision = 9, scale = 6)
    val latitude: BigDecimal? = null,

    /** 
     * 경도
     * 이벤트 개최지의 GPS 경도 좌표 (소수점 6자리)
     */
    @field:DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다")
    @field:DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다")
    @Column(name = "longitude", precision = 9, scale = 6)
    val longitude: BigDecimal? = null,

    /** 
     * 썸네일 이미지 URL
     * 이벤트를 대표하는 썸네일 이미지의 URL 또는 파일 경로
     */
    @Column(name = "thumbnail", length = 255)
    val thumbnail: String? = null,

    /** 
     * 생성 일시
     * 이벤트가 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 
     * 마지막 업데이트 일시
     * 이벤트 정보가 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 현재 시간을 기준으로 이벤트가 진행 중인지 확인
     */
    fun isOngoing(): Boolean {
        val now = LocalDateTime.now()
        return now.isAfter(startDateTime) && now.isBefore(endDateTime)
    }
    
    /**
     * 현재 시간을 기준으로 이벤트가 완료되었는지 확인
     */
    fun isFinished(): Boolean {
        val now = LocalDateTime.now()
        return now.isAfter(endDateTime)
    }
    
    /**
     * 현재 시간을 기준으로 이벤트가 시작 전인지 확인
     */
    fun isUpcoming(): Boolean {
        val now = LocalDateTime.now()
        return now.isBefore(startDateTime)
    }

    /**
     * 이벤트 기간이 유효한지 확인 (시작일시가 종료일시보다 빠른지)
     */
    fun hasValidTimeRange(): Boolean {
        return startDateTime!!.isBefore(endDateTime)
    }

    /**
     * 엔티티 생성/수정 시 시간 범위 유효성 검증
     */
    @PrePersist
    @PreUpdate
    fun validateTimeRange() {
        if (!hasValidTimeRange()) {
            throw IllegalArgumentException("시작 일시는 종료 일시보다 빨라야 합니다")
        }
    }
} 