package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

/**
 * 체크포인트 통과 시간 정보 DTO
 */
@Schema(description = "체크포인트 통과 시간 정보")
data class CheckpointPassTimeDto(
    @Schema(description = "체크포인트 ID", example = "CP1")
    @JsonProperty("checkpointId")
    val checkpointId: String,
    
    @Schema(description = "통과 시간 (Unix Timestamp)", example = "1678886400")
    @JsonProperty("passTime")
    val passTime: Long,
    
    @Schema(description = "통과 시간 (포맷된 문자열)", example = "2023-03-15 14:20:00")
    @JsonProperty("passTimeFormatted")
    val passTimeFormatted: String
)

/**
 * 체크포인트 통과 시간 기록 요청 DTO
 */
@Schema(description = "체크포인트 통과 시간 기록 요청")
data class CheckpointTimesRequestDto(
    @Schema(description = "사용자 ID", example = "user123", required = true)
    @JsonProperty("userId")
    @field:NotBlank(message = "사용자 ID는 필수입니다")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA", required = true)
    @JsonProperty("eventId")
    @field:NotBlank(message = "이벤트 ID는 필수입니다")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456", required = true)
    @JsonProperty("eventDetailId")
    @field:NotBlank(message = "이벤트 상세 ID는 필수입니다")
    val eventDetailId: String,
    
    @Schema(description = "체크포인트 ID", example = "CP1", required = true)
    @JsonProperty("checkpointId")
    @field:NotBlank(message = "체크포인트 ID는 필수입니다")
    val checkpointId: String,
    
    @Schema(description = "통과 시간 (Unix Timestamp). 미제공시 현재 시간 사용", example = "1678886400")
    @JsonProperty("passTime")
    val passTime: Long? = null
)

/**
 * 체크포인트 통과 시간 기록 응답 DTO
 */
@Schema(description = "체크포인트 통과 시간 기록 응답")
data class CheckpointTimesResponseDto(
    @Schema(description = "성공 여부", example = "true")
    @JsonProperty("success")
    val success: Boolean,
    
    @Schema(description = "메시지", example = "체크포인트 통과 시간이 성공적으로 기록되었습니다")
    @JsonProperty("message")
    val message: String,
    
    @Schema(description = "기록된 체크포인트 통과 시간 정보")
    @JsonProperty("data")
    val data: CheckpointPassTimeDto?
)

/**
 * 모든 체크포인트 통과 시간 조회 응답 DTO
 */
@Schema(description = "모든 체크포인트 통과 시간 조회 응답")
data class AllCheckpointTimesResponseDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "체크포인트 통과 시간 목록")
    @JsonProperty("checkpointTimes")
    val checkpointTimes: List<CheckpointPassTimeDto>,
    
    @Schema(description = "통과한 체크포인트 총 개수", example = "3")
    @JsonProperty("totalCount")
    val totalCount: Int
)

/**
 * 체크포인트 통과 여부 확인 응답 DTO
 */
@Schema(description = "체크포인트 통과 여부 확인 응답")
data class CheckpointPassStatusDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "체크포인트 ID", example = "CP1")
    @JsonProperty("checkpointId")
    val checkpointId: String,
    
    @Schema(description = "통과 여부", example = "true")
    @JsonProperty("hasPassed")
    val hasPassed: Boolean,
    
    @Schema(description = "통과 시간 (Unix Timestamp, 통과한 경우만)", example = "1678886400")
    @JsonProperty("passTime")
    val passTime: Long?,
    
    @Schema(description = "통과 시간 (포맷된 문자열, 통과한 경우만)", example = "2023-03-15 14:20:00")
    @JsonProperty("passTimeFormatted")
    val passTimeFormatted: String?
)

/**
 * 체크포인트 통과 통계 응답 DTO
 */
@Schema(description = "체크포인트 통과 통계 응답")
data class CheckpointStatsDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "통과한 체크포인트 총 개수", example = "3")
    @JsonProperty("passedCount")
    val passedCount: Long,
    
    @Schema(description = "최초 체크포인트 통과 시간")
    @JsonProperty("firstPassTime")
    val firstPassTime: CheckpointPassTimeDto?,
    
    @Schema(description = "최근 체크포인트 통과 시간")
    @JsonProperty("lastPassTime")
    val lastPassTime: CheckpointPassTimeDto?
) 