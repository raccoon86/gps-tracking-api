package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

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
)

/**
 * 대회 현장 상세 조회 응답 DTO
 */
@Schema(description = "대회 상세 조회 응답")
data class EventVenueDetailResponseDto(
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
 * 
 * 참가자의 실시간 GPS 위치 정보를 담는 데이터 전송 객체입니다.
 * 원본 GPS 데이터와 보정된 위치 정보를 모두 포함하여 
 * 클라이언트에서 다양한 용도로 활용할 수 있도록 합니다.
 * 
 * **포함 정보:**
 * 1. 원본 GPS 데이터: rawAltitude, rawSpeed (센서 직접 수신값)
 * 2. 보정된 위치: correctedLatitude, correctedLongitude, correctedAltitude
 * 3. 계산된 정보: distanceCovered, cumulativeTime, heading
 */
@Schema(description = "이벤트 참가자 위치 정보")
data class EventParticipantLocationDto(
    @Schema(description = "사용자 ID", example = "1")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "참가자 이름", example = "김러너")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
    @JsonProperty("profileUrl")
    val profileUrl: String? = null,

    @Schema(description = "배번", example = "A001")
    @JsonProperty("bibNumber")
    val bibNumber: String? = null,

    @Schema(description = "위도 (보정된 좌표)", example = "35.964090207988626")
    @JsonProperty("latitude")
    val latitude: Double,

    @Schema(description = "경도 (보정된 좌표)", example = "126.74784739855649")
    @JsonProperty("longitude")
    val longitude: Double,

    @Schema(description = "고도 (보정된 값)", example = "10")
    @JsonProperty("altitude")
    val altitude: Double? = null,

    @Schema(description = "속도 (m/s)", example = "2.8367636")
    @JsonProperty("speed")
    val speed: Float? = null,
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
)