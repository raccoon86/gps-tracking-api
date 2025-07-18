package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 참가자 검색 요청 DTO
 */
@Schema(description = "참가자 검색 요청")
data class ParticipantSearchRequestDto(
    @Schema(description = "이벤트 ID", example = "1", required = true)
    @JsonProperty("eventId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1", required = true)
    @JsonProperty("eventDetailId")
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    val eventDetailId: Long?,

    @Schema(description = "참가자 이름 또는 배번으로 검색", example = "강감찬")
    @JsonProperty("search")
    @field:Size(max = 100, message = "검색어는 100자를 초과할 수 없습니다")
    val search: String? = null,

    @Schema(description = "페이지 크기", example = "20")
    @JsonProperty("size")
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    val size: Int = 20,

    @Schema(description = "다음 페이지 커서", example = "F004_20250709120500")
    @JsonProperty("cursor")
    val cursor: String? = null,

    @Schema(description = "현재 사용자 ID (트래킹 목록 제외용)", example = "1")
    @JsonProperty("userId")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long? = null
)

/**
 * 참가자 검색 결과 DTO
 */
@Schema(description = "참가자 검색 결과")
data class ParticipantSearchItemDto(
    @Schema(description = "참가자 ID", example = "1")
    @JsonProperty("participantId")
    val participantId: Long,

    @Schema(description = "참가자 이름", example = "강감찬")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "닉네임", example = "러너")
    @JsonProperty("nickname")
    val nickname: String?,

    @Schema(description = "배번", example = "M002")
    @JsonProperty("bibNumber")
    val bibNumber: String?,

    @Schema(description = "프로필 이미지 URL", example = "http://example.com/profile/kang.jpg")
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String?,

    @Schema(description = "국가명", example = "South Korea")
    @JsonProperty("country")
    val country: String?
)

/**
 * 참가자 검색 응답 DTO
 */
@Schema(description = "참가자 검색 응답")
data class ParticipantSearchResponseDto(
    @Schema(description = "참가자 목록")
    @JsonProperty("participants")
    val participants: List<ParticipantSearchItemDto>,

    @Schema(description = "다음 페이지 커서", example = "F004_20250709120500")
    @JsonProperty("nextCursor")
    val nextCursor: String?
) 