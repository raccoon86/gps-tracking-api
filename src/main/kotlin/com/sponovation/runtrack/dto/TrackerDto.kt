package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 트래킹 추가 요청 DTO
 */
@Schema(description = "트래킹 추가 요청")
data class AddTrackerRequestDto(
    @Schema(description = "사용자 ID", example = "1", required = true)
    @JsonProperty("userId")
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long,

    @Schema(description = "이벤트 ID", example = "1", required = true)
    @JsonProperty("eventId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1", required = true)
    @JsonProperty("eventDetailId")
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    val eventDetailId: Long,

    @Schema(description = "참가자 ID", example = "1", required = true)
    @JsonProperty("participantId")
    @field:NotNull(message = "참가자 ID는 필수입니다")
    @field:Positive(message = "참가자 ID는 양수여야 합니다")
    val participantId: Long
)

/**
 * 트래킹 목록 항목 DTO
 */
@Schema(description = "트래킹 목록 항목")
data class TrackerItemDto(
    @Schema(description = "참가자 ID", example = "1")
    @JsonProperty("participantId")
    val participantId: Long,

    @Schema(description = "참가자 이름", example = "강감찬")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "참가자 닉네임", example = "러닝마스터")
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
 * 트래킹 목록 조회 응답 DTO
 */
@Schema(description = "트래킹 목록 조회 응답")
data class TrackerListResponseDto(
    @Schema(description = "트래킹 중인 참가자 목록")
    @JsonProperty("participants")
    val participants: List<TrackerItemDto>
)

/**
 * 트래킹 삭제 요청 DTO
 */
@Schema(description = "트래킹 삭제 요청")
data class RemoveTrackerRequestDto(
    @Schema(description = "사용자 ID", example = "1", required = true)
    @JsonProperty("userId")
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long,

    @Schema(description = "이벤트 ID", example = "1", required = true)
    @JsonProperty("eventId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1", required = true)
    @JsonProperty("eventDetailId")
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    val eventDetailId: Long,

    @Schema(description = "참가자 ID", example = "1", required = true)
    @JsonProperty("participantId")
    @field:NotNull(message = "참가자 ID는 필수입니다")
    @field:Positive(message = "참가자 ID는 양수여야 합니다")
    val participantId: Long
)