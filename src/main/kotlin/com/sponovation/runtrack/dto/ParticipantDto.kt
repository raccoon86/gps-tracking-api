package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.time.LocalDateTime

/**
 * 참가자 생성 요청 DTO
 */
@Schema(description = "참가자 생성 요청")
data class ParticipantRequestDto(
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

    @Schema(description = "사용자 ID", example = "1", required = true)
    @JsonProperty("userId")
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    val userId: Long,

    @Schema(description = "이름", example = "홍길동", required = true)
    @JsonProperty("name")
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    val name: String,

    @Schema(description = "닉네임", example = "러너")
    @JsonProperty("nickname")
    @field:Size(max = 30, message = "닉네임은 30자를 초과할 수 없습니다")
    val nickname: String,

    @Schema(description = "국가명 (영어)", example = "South Korea")
    @JsonProperty("country")
    @field:Size(max = 50, message = "국가명은 50자를 초과할 수 없습니다")
    val country: String,

    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
    @JsonProperty("profileImageUrl")
    @field:Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다")
    val profileImageUrl: String,

    @Schema(description = "성별 (M: 남성, F: 여성)", example = "M")
    @JsonProperty("gender")
    val gender: String? = "M",

    @Schema(description = "생년월일", example = "1990-01-01")
    @JsonProperty("birthday")
    @field:NotNull(message = "생년월일은 필수입니다")
    @field:Past(message = "생년월일은 과거여야 합니다")
    val birthday: LocalDateTime,

    @Schema(description = "참가 상태", example = "REGISTERED", required = true)
    @JsonProperty("status")
    @field:NotBlank(message = "상태는 필수입니다")
    @field:Size(max = 30, message = "상태는 30자를 초과할 수 없습니다")
    val status: String,

    @Schema(description = "배번", example = "A001")
    @JsonProperty("bibNumber")
    @field:Size(max = 10, message = "배번은 10자를 초과할 수 없습니다")
    val bibNumber: String? = null,

    @Schema(description = "태그명", example = "VIP")
    @JsonProperty("tagName")
    @field:Size(max = 50, message = "태그명은 50자를 초과할 수 없습니다")
    val tagName: String? = null,

    @Schema(description = "관리자 메모", example = "특별 관리 대상")
    @JsonProperty("adminMemo")
    @field:Size(max = 255, message = "관리자 메모는 255자를 초과할 수 없습니다")
    val adminMemo: String? = null,

    @Schema(description = "사용자 메모", example = "첫 마라톤 참가")
    @JsonProperty("userMemo")
    @field:Size(max = 255, message = "사용자 메모는 255자를 초과할 수 없습니다")
    val userMemo: String? = null,

    @Schema(description = "경기 상태", example = "READY")
    @JsonProperty("raceStatus")
    @field:Size(max = 20, message = "경기 상태는 20자를 초과할 수 없습니다")
    val raceStatus: String
)

/**
 * 참가자 수정 요청 DTO
 */
@Schema(description = "참가자 수정 요청")
data class UpdateParticipantRequestDto(
    @Schema(description = "이름", example = "홍길동")
    @JsonProperty("name")
    @field:Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    val name: String? = null,

    @Schema(description = "닉네임", example = "러너")
    @JsonProperty("nickname")
    @field:Size(max = 30, message = "닉네임은 30자를 초과할 수 없습니다")
    val nickname: String? = null,

    @Schema(description = "국가명 (영어)", example = "South Korea")
    @JsonProperty("country")
    @field:Size(max = 50, message = "국가명은 50자를 초과할 수 없습니다")
    val country: String? = null,

    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
    @JsonProperty("profileImageUrl")
    @field:Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다")
    val profileImageUrl: String? = null,

    @Schema(description = "나이", example = "25")
    @JsonProperty("age")
    @field:Min(value = 1, message = "나이는 1세 이상이어야 합니다")
    @field:Max(value = 150, message = "나이는 150세 이하여야 합니다")
    val age: Int? = null,

    @Schema(description = "성별 (M: 남성, F: 여성)", example = "M")
    @JsonProperty("gender")
    val gender: String? = "M",

    @Schema(description = "생년월일", example = "1990-01-01")
    @JsonProperty("birthday")
    @field:NotNull(message = "생년월일은 필수입니다")
    @field:Past(message = "생년월일은 과거여야 합니다")
    val birthday: LocalDateTime? = null,

    @Schema(description = "참가 상태", example = "APPROVED")
    @JsonProperty("status")
    @field:Size(max = 30, message = "상태는 30자를 초과할 수 없습니다")
    val status: String? = null,

    @Schema(description = "배번", example = "A001")
    @JsonProperty("bibNumber")
    @field:Size(max = 10, message = "배번은 10자를 초과할 수 없습니다")
    val bibNumber: String? = null,

    @Schema(description = "태그명", example = "VIP")
    @JsonProperty("tagName")
    @field:Size(max = 50, message = "태그명은 50자를 초과할 수 없습니다")
    val tagName: String? = null,

    @Schema(description = "관리자 메모", example = "특별 관리 대상")
    @JsonProperty("adminMemo")
    @field:Size(max = 255, message = "관리자 메모는 255자를 초과할 수 없습니다")
    val adminMemo: String? = null,

    @Schema(description = "사용자 메모", example = "첫 마라톤 참가")
    @JsonProperty("userMemo")
    @field:Size(max = 255, message = "사용자 메모는 255자를 초과할 수 없습니다")
    val userMemo: String? = null,

    @Schema(description = "경기 상태", example = "RACING")
    @JsonProperty("raceStatus")
    @field:Size(max = 20, message = "경기 상태는 20자를 초과할 수 없습니다")
    val raceStatus: String? = null
)

/**
 * 참가자 조회 응답 DTO
 */
@Schema(description = "참가자 조회 응답")
data class ParticipantResponseDto(
    @Schema(description = "참가자 ID", example = "1")
    @JsonProperty("id")
    val id: Long,

    @Schema(description = "이벤트 ID", example = "1")
    @JsonProperty("eventId")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,

    @Schema(description = "사용자 ID", example = "1")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "이름", example = "홍길동")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "닉네임", example = "러너")
    @JsonProperty("nickname")
    val nickname: String,

    @Schema(description = "국가명 (영어)", example = "South Korea")
    @JsonProperty("country")
    val country: String,

    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String,

    @Schema(description = "성별", example = "M")
    @JsonProperty("gender")
    val gender: String? = "M",

    @Schema(description = "생년월일", example = "1990-01-01")
    @JsonProperty("birthday")
    val birthday: LocalDateTime?,

    @Schema(description = "참가 상태", example = "APPROVED")
    @JsonProperty("status")
    val status: String,

    @Schema(description = "등록 일시", example = "2024-11-01T10:00:00")
    @JsonProperty("registeredAt")
    val registeredAt: LocalDateTime?,

    @Schema(description = "배번", example = "A001")
    @JsonProperty("bibNumber")
    val bibNumber: String?,

    @Schema(description = "태그명", example = "VIP")
    @JsonProperty("tagName")
    val tagName: String?,

    @Schema(description = "관리자 메모", example = "특별 관리 대상")
    @JsonProperty("adminMemo")
    val adminMemo: String?,

    @Schema(description = "사용자 메모", example = "첫 마라톤 참가")
    @JsonProperty("userMemo")
    val userMemo: String?,

    @Schema(description = "경기 상태", example = "RACING")
    @JsonProperty("raceStatus")
    val raceStatus: String,

    @Schema(description = "생성 일시", example = "2024-11-01T10:00:00")
    @JsonProperty("createdAt")
    val createdAt: LocalDateTime,

    @Schema(description = "수정 일시", example = "2024-11-01T10:00:00")
    @JsonProperty("updatedAt")
    val updatedAt: LocalDateTime,
)

/**
 * 테스트 참가자 생성 요청 DTO
 */
@Schema(description = "테스트 참가자 생성 요청")
data class CreateTestParticipantsRequestDto(
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

    @Schema(description = "생성할 참가자 수", example = "100", required = true)
    @JsonProperty("count")
    @field:NotNull(message = "생성할 참가자 수는 필수입니다")
    @field:Min(value = 1, message = "최소 1명 이상 생성해야 합니다")
    @field:Max(value = 1000, message = "최대 1000명까지 생성 가능합니다")
    val count: Int
)

/**
 * 테스트 참가자 생성 응답 DTO
 */
@Schema(description = "테스트 참가자 생성 응답")
data class CreateTestParticipantsResponseDto(
    @Schema(description = "생성된 참가자 수", example = "100")
    @JsonProperty("createdCount")
    val createdCount: Int,

    @Schema(description = "이벤트 ID", example = "1")
    @JsonProperty("eventId")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,

    @Schema(description = "생성된 참가자 ID 목록")
    @JsonProperty("participantIds")
    val participantIds: List<Long>,

    @Schema(description = "생성 완료 시간", example = "2024-11-01T10:00:00")
    @JsonProperty("createdAt")
    val createdAt: LocalDateTime
)