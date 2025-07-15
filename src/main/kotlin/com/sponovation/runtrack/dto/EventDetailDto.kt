package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 이벤트 상세 생성 요청 DTO
 */
@Schema(description = "이벤트 상세 생성 요청")
data class EventDetailRequestDto(
    @Schema(description = "이벤트 ID", example = "1", required = true)
    @JsonProperty("eventId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,

    @Schema(description = "이벤트 상세ID", example = "100", required = true)
    @JsonProperty("eventDetailId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventDetailId: Long,
)

/**
 * 이벤트 상세 수정 요청 DTO
 */
@Schema(description = "이벤트 상세 수정 요청")
data class UpdateEventDetailRequestDto(
    @Schema(description = "코스 거리", example = "10")
    @JsonProperty("distance")
    @field:Positive(message = "거리는 양수여야 합니다")
    @field:Max(value = 1000, message = "거리는 1000000를 초과할 수 없습니다")
    val distance: Int? = null,

    @Schema(description = "코스명", example = "10km")
    @JsonProperty("course")
    @field:Size(max = 30, message = "코스명은 30자를 초과할 수 없습니다")
    val course: String? = null,

    @Schema(description = "GPX 파일 URL", example = "https://example.com/course.gpx")
    @JsonProperty("gpxFile")
    @field:Size(max = 255, message = "GPX 파일 URL은 255자를 초과할 수 없습니다")
    val gpxFile: String? = null
)

/**
 * 대회 상세 조회 응답 DTO
 */
@Schema(description = "대회 상세 조회 응답")
data class EventDetailResponseDto(
    @Schema(description = "이벤트 ID", example = "1")
    @JsonProperty("eventId")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "100")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,

    @Schema(description = "대회 이름", example = "서울 마라톤 2025")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "코스 카테고리 목록")
    @JsonProperty("courseCategory")
    val courseCategory: List<CourseCategoryDto>,

    @Schema(description = "참가자 위치 데이터 (1~3위 유저 + 트래커 목록 유저)")
    @JsonProperty("participantsLocations")
    val participantsLocations: List<EventParticipantLocationDto>,

    @Schema(description = "상위 랭커 정보 (맵 하단 표시용)")
    @JsonProperty("topRankers")
    val topRankers: List<TopRankerDto>
)

/**
 * 코스 카테고리 DTO
 */
@Schema(description = "코스 카테고리 정보")
data class CourseCategoryDto(
    @Schema(description = "코스 거리", example = "10.0")
    @JsonProperty("eventDetail")
    val course: Double?,

    @Schema(description = "이벤트 상세 ID", example = "1")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long
)

/**
 * 이벤트 참가자 위치 DTO
 */
@Schema(description = "이벤트 참가자 위치 정보")
data class EventParticipantLocationDto(
    @Schema(description = "사용자 ID", example = "userXYZ")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "보정된 위도", example = "37.5668")
    @JsonProperty("correctedLatitude")
    val correctedLatitude: Double,

    @Schema(description = "보정된 경도", example = "126.9786")
    @JsonProperty("correctedLongitude")
    val correctedLongitude: Double,

    @Schema(description = "보정된 고도", example = "50.5")
    @JsonProperty("correctedAltitude")
    val correctedAltitude: Double? = null,

    @Schema(description = "진행 방향", example = "45.2")
    @JsonProperty("heading")
    val heading: Double? = null,

    @Schema(description = "주행 거리 (미터)", example = "1200.5")
    @JsonProperty("distanceCovered")
    val distanceCovered: Double? = null,

    @Schema(description = "누적 시간 (포맷)", example = "00:25:30")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: String? = null
)

/**
 * 상위 랭커 DTO
 */
@Schema(description = "상위 랭커 정보")
data class TopRankerDto(
    @Schema(description = "순위", example = "1")
    @JsonProperty("rank")
    val rank: Int,

    @Schema(description = "사용자 ID", example = "userOfficialFinished")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "참가자 이름", example = "공식완주자1")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "참가자 번호", example = "F001")
    @JsonProperty("bibNumber")
    val bibNumber: String,

    @Schema(description = "프로필 이미지 URL", example = "http://example.com/profile/official1.jpg")
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,

    @Schema(description = "가장 최근 체크포인트 ID", example = "FINISH")
    @JsonProperty("farthestCpId")
    val farthestCpId: String? = null,

    @Schema(description = "가장 최근 체크포인트 인덱스", example = "5")
    @JsonProperty("farthestCpIndex")
    val farthestCpIndex: Int? = null,

    @Schema(description = "가장 최근 체크포인트까지의 누적 시간", example = "00:40:15")
    @JsonProperty("cumulativeTimeAtFarthestCp")
    val cumulativeTimeAtFarthestCp: String? = null,

    @Schema(description = "누적 거리 (미터)", example = "10000.0")
    @JsonProperty("cumulativeDistance")
    val cumulativeDistance: Double? = null,

    @Schema(description = "평균 속도 (m/min)", example = "248.8")
    @JsonProperty("averageSpeed")
    val averageSpeed: Double? = null,

    @Schema(description = "완주 여부", example = "true")
    @JsonProperty("isFinished")
    val isFinished: Boolean = false
)