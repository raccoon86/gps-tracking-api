package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 이벤트 생성 요청 DTO
 */
@Schema(description = "이벤트 생성 요청")
data class CreateEventRequestDto(
    @Schema(description = "이벤트 이름", example = "2024 서울 마라톤 대회", required = true)
    @JsonProperty("name")
    @field:NotBlank(message = "이벤트 이름은 필수입니다")
    @field:Size(max = 255, message = "이벤트 이름은 255자를 초과할 수 없습니다")
    val name: String,

    @Schema(description = "스포츠 종목", example = "마라톤")
    @JsonProperty("sports")
    @field:Size(max = 30, message = "스포츠 종목은 30자를 초과할 수 없습니다")
    val sports: String? = null,

    @Schema(description = "시작 일시", example = "2024-12-01T09:00:00", required = true)
    @JsonProperty("startDateTime")
    @field:NotNull(message = "시작 일시는 필수입니다")
    @field:Future(message = "시작 일시는 현재 시간보다 미래여야 합니다")
    val startDateTime: LocalDateTime,

    @Schema(description = "종료 일시", example = "2024-12-01T17:00:00", required = true)
    @JsonProperty("endDateTime")
    @field:NotNull(message = "종료 일시는 필수입니다")
    val endDateTime: LocalDateTime,

    @Schema(description = "국가", example = "대한민국", required = true)
    @JsonProperty("country")
    @field:NotBlank(message = "국가는 필수입니다")
    @field:Size(max = 20, message = "국가는 20자를 초과할 수 없습니다")
    val country: String,

    @Schema(description = "도시", example = "서울", required = true)
    @JsonProperty("city")
    @field:NotBlank(message = "도시는 필수입니다")
    @field:Size(max = 255, message = "도시는 255자를 초과할 수 없습니다")
    val city: String,

    @Schema(description = "주소", example = "서울특별시 송파구 올림픽로 424")
    @JsonProperty("address")
    @field:Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
    val address: String? = null,

    @Schema(description = "장소명", example = "올림픽 공원")
    @JsonProperty("place")
    @field:Size(max = 255, message = "장소명은 255자를 초과할 수 없습니다")
    val place: String? = null,

    @Schema(description = "위도", example = "37.5175896")
    @JsonProperty("latitude")
    @field:DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다")
    @field:DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다")
    @field:Digits(integer = 3, fraction = 6, message = "위도는 정수 3자리, 소수점 6자리를 초과할 수 없습니다")
    val latitude: BigDecimal? = null,

    @Schema(description = "경도", example = "127.1230074")
    @JsonProperty("longitude")
    @field:DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다")
    @field:DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다")
    @field:Digits(integer = 3, fraction = 6, message = "경도는 정수 3자리, 소수점 6자리를 초과할 수 없습니다")
    val longitude: BigDecimal? = null,

    @Schema(description = "썸네일 이미지 URL", example = "https://example.com/thumbnail.jpg")
    @JsonProperty("thumbnail")
    @field:Size(max = 255, message = "썸네일 URL은 255자를 초과할 수 없습니다")
    @field:Pattern(
        regexp = "^(https?://.*\\.(jpg|jpeg|png|gif|bmp|webp)|/.*\\.(jpg|jpeg|png|gif|bmp|webp))$",
        message = "올바른 이미지 URL 형식이어야 합니다"
    )
    val thumbnail: String? = null
)

/**
 * 이벤트 수정 요청 DTO
 */
@Schema(description = "이벤트 수정 요청")
data class UpdateEventRequestDto(
    @Schema(description = "이벤트 이름", example = "2024 서울 마라톤 대회")
    @JsonProperty("name")
    @field:Size(max = 255, message = "이벤트 이름은 255자를 초과할 수 없습니다")
    val name: String? = null,

    @Schema(description = "스포츠 종목", example = "마라톤")
    @JsonProperty("sports")
    @field:Size(max = 30, message = "스포츠 종목은 30자를 초과할 수 없습니다")
    val sports: String? = null,

    @Schema(description = "시작 일시", example = "2024-12-01T09:00:00")
    @JsonProperty("startDateTime")
    val startDateTime: LocalDateTime? = null,

    @Schema(description = "종료 일시", example = "2024-12-01T17:00:00")
    @JsonProperty("endDateTime")
    val endDateTime: LocalDateTime? = null,

    @Schema(description = "국가", example = "대한민국")
    @JsonProperty("country")
    @field:Size(max = 20, message = "국가는 20자를 초과할 수 없습니다")
    val country: String? = null,

    @Schema(description = "도시", example = "서울")
    @JsonProperty("city")
    @field:Size(max = 255, message = "도시는 255자를 초과할 수 없습니다")
    val city: String? = null,

    @Schema(description = "주소", example = "서울특별시 송파구 올림픽로 424")
    @JsonProperty("address")
    @field:Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
    val address: String? = null,

    @Schema(description = "장소명", example = "올림픽 공원")
    @JsonProperty("place")
    @field:Size(max = 255, message = "장소명은 255자를 초과할 수 없습니다")
    val place: String? = null,

    @Schema(description = "위도", example = "37.5175896")
    @JsonProperty("latitude")
    @field:DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다")
    @field:DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다")
    @field:Digits(integer = 3, fraction = 6, message = "위도는 정수 3자리, 소수점 6자리를 초과할 수 없습니다")
    val latitude: BigDecimal? = null,

    @Schema(description = "경도", example = "127.1230074")
    @JsonProperty("longitude")
    @field:DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다")
    @field:DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다")
    @field:Digits(integer = 3, fraction = 6, message = "경도는 정수 3자리, 소수점 6자리를 초과할 수 없습니다")
    val longitude: BigDecimal? = null,

    @Schema(description = "썸네일 이미지 URL", example = "https://example.com/thumbnail.jpg")
    @JsonProperty("thumbnail")
    @field:Size(max = 255, message = "썸네일 URL은 255자를 초과할 수 없습니다")
    @field:Pattern(
        regexp = "^(https?://.*\\.(jpg|jpeg|png|gif|bmp|webp)|/.*\\.(jpg|jpeg|png|gif|bmp|webp))$",
        message = "올바른 이미지 URL 형식이어야 합니다"
    )
    val thumbnail: String? = null
)

/**
 * 이벤트 조회 응답 DTO
 */
@Schema(description = "이벤트 조회 응답")
data class EventResponseDto(
    @Schema(description = "이벤트 ID", example = "1")
    @JsonProperty("id")
    val id: Long,

    @Schema(description = "이벤트 이름", example = "2024 서울 마라톤 대회")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "스포츠 종목", example = "마라톤")
    @JsonProperty("sports")
    val sports: String?,

    @Schema(description = "시작 일시", example = "2024-12-01T09:00:00")
    @JsonProperty("startDateTime")
    val startDateTime: LocalDateTime,

    @Schema(description = "종료 일시", example = "2024-12-01T17:00:00")
    @JsonProperty("endDateTime")
    val endDateTime: LocalDateTime,

    @Schema(description = "국가", example = "대한민국")
    @JsonProperty("country")
    val country: String,

    @Schema(description = "도시", example = "서울")
    @JsonProperty("city")
    val city: String,

    @Schema(description = "주소", example = "서울특별시 송파구 올림픽로 424")
    @JsonProperty("address")
    val address: String?,

    @Schema(description = "장소명", example = "올림픽 공원")
    @JsonProperty("place")
    val place: String?,

    @Schema(description = "위도", example = "37.5175896")
    @JsonProperty("latitude")
    val latitude: BigDecimal?,

    @Schema(description = "경도", example = "127.1230074")
    @JsonProperty("longitude")
    val longitude: BigDecimal?,

    @Schema(description = "썸네일 이미지 URL", example = "https://example.com/thumbnail.jpg")
    @JsonProperty("thumbnail")
    val thumbnail: String?,

    @Schema(description = "생성 일시", example = "2024-11-01T10:00:00")
    @JsonProperty("createdAt")
    val createdAt: LocalDateTime,

    @Schema(description = "수정 일시", example = "2024-11-01T10:00:00")
    @JsonProperty("updatedAt")
    val updatedAt: LocalDateTime,

    @Schema(description = "이벤트 상태", example = "ONGOING")
    @JsonProperty("status")
    val status: EventStatusDto
)

/**
 * 이벤트 상태 DTO
 */
@Schema(description = "이벤트 상태 정보")
data class EventStatusDto(
    @Schema(description = "진행 중 여부", example = "true")
    @JsonProperty("isOngoing")
    val isOngoing: Boolean,

    @Schema(description = "완료 여부", example = "false")
    @JsonProperty("isFinished")
    val isFinished: Boolean,

    @Schema(description = "시작 전 여부", example = "false")
    @JsonProperty("isUpcoming")
    val isUpcoming: Boolean,

    @Schema(description = "GPS 좌표 설정 여부", example = "true")
    @JsonProperty("hasGpsCoordinates")
    val hasGpsCoordinates: Boolean
) 