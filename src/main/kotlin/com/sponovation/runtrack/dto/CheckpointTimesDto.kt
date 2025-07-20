package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

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