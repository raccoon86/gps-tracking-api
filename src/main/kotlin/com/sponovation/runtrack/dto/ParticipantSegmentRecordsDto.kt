package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 구간별 기록 정보 DTO
 */
@Schema(description = "구간별 기록 정보")
data class SegmentRecordDto(
    @Schema(description = "체크포인트 ID", example = "CP1")
    @JsonProperty("checkpointId")
    val checkpointId: String,
    
    @Schema(description = "구간 소요 시간 (초)", example = "1530")
    @JsonProperty("segmentDuration")
    val segmentDuration: Long,
    
    @Schema(description = "누적 소요 시간 (초)", example = "1530")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long,
    
    @Schema(description = "구간 소요 시간 (포맷된 문자열)", example = "25:30")
    @JsonProperty("segmentDurationFormatted")
    val segmentDurationFormatted: String,
    
    @Schema(description = "누적 소요 시간 (포맷된 문자열)", example = "25:30")
    @JsonProperty("cumulativeTimeFormatted")
    val cumulativeTimeFormatted: String
)

/**
 * 참가자 전체 구간별 기록 응답 DTO
 */
@Schema(description = "참가자 전체 구간별 기록 응답")
data class ParticipantSegmentRecordsDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "detail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "구간별 기록 목록")
    @JsonProperty("segmentRecords")
    val segmentRecords: List<SegmentRecordDto>,
    
    @Schema(description = "총 구간 수", example = "5")
    @JsonProperty("totalSegments")
    val totalSegments: Int,
    
    @Schema(description = "총 소요 시간 (초)", example = "7200")
    @JsonProperty("totalTime")
    val totalTime: Long,
    
    @Schema(description = "총 소요 시간 (포맷된 문자열)", example = "2:00:00")
    @JsonProperty("totalTimeFormatted")
    val totalTimeFormatted: String
)

/**
 * 구간별 기록 통계 DTO
 */
@Schema(description = "구간별 기록 통계")
data class SegmentRecordStatsDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "detail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "총 체크포인트 수", example = "5")
    @JsonProperty("totalCheckpoints")
    val totalCheckpoints: Int,
    
    @Schema(description = "총 소요 시간 (초)", example = "7200")
    @JsonProperty("totalTime")
    val totalTime: Long,
    
    @Schema(description = "총 소요 시간 (포맷된 문자열)", example = "2:00:00")
    @JsonProperty("totalTimeFormatted")
    val totalTimeFormatted: String,
    
    @Schema(description = "가장 빠른 구간 시간 (초)", example = "1200")
    @JsonProperty("fastestSegment")
    val fastestSegment: Long?,
    
    @Schema(description = "가장 빠른 구간 시간 (포맷된 문자열)", example = "20:00")
    @JsonProperty("fastestSegmentFormatted")
    val fastestSegmentFormatted: String?,
    
    @Schema(description = "가장 느린 구간 시간 (초)", example = "1800")
    @JsonProperty("slowestSegment")
    val slowestSegment: Long?,
    
    @Schema(description = "가장 느린 구간 시간 (포맷된 문자열)", example = "30:00")
    @JsonProperty("slowestSegmentFormatted")
    val slowestSegmentFormatted: String?,
    
    @Schema(description = "평균 구간 시간 (초)", example = "1440.0")
    @JsonProperty("averageSegmentTime")
    val averageSegmentTime: Double,
    
    @Schema(description = "평균 구간 시간 (포맷된 문자열)", example = "24:00")
    @JsonProperty("averageSegmentTimeFormatted")
    val averageSegmentTimeFormatted: String
)

/**
 * 구간별 기록 업데이트 요청 DTO
 */
@Schema(description = "구간별 기록 업데이트 요청")
data class SegmentRecordUpdateRequestDto(
    @Schema(description = "사용자 ID", example = "user123", required = true)
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA", required = true)
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "detail456", required = true)
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "시작 시간 (Unix Timestamp)", example = "1678885000", required = true)
    @JsonProperty("startTime")
    val startTime: Long
)

/**
 * 구간별 기록 업데이트 응답 DTO
 */
@Schema(description = "구간별 기록 업데이트 응답")
data class SegmentRecordUpdateResponseDto(
    @Schema(description = "성공 여부", example = "true")
    @JsonProperty("success")
    val success: Boolean,
    
    @Schema(description = "메시지", example = "구간별 기록이 성공적으로 업데이트되었습니다")
    @JsonProperty("message")
    val message: String,
    
    @Schema(description = "업데이트된 구간 수", example = "5")
    @JsonProperty("updatedSegments")
    val updatedSegments: Int
)

/**
 * 단일 구간 기록 조회 응답 DTO
 */
@Schema(description = "단일 구간 기록 조회 응답")
data class SingleSegmentRecordDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "detail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "체크포인트 ID", example = "CP1")
    @JsonProperty("checkpointId")
    val checkpointId: String,
    
    @Schema(description = "구간 소요 시간 (초)", example = "1530")
    @JsonProperty("segmentDuration")
    val segmentDuration: Long?,
    
    @Schema(description = "누적 소요 시간 (초)", example = "1530")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long?,
    
    @Schema(description = "구간 소요 시간 (포맷된 문자열)", example = "25:30")
    @JsonProperty("segmentDurationFormatted")
    val segmentDurationFormatted: String?,
    
    @Schema(description = "누적 소요 시간 (포맷된 문자열)", example = "25:30")
    @JsonProperty("cumulativeTimeFormatted")
    val cumulativeTimeFormatted: String?,
    
    @Schema(description = "기록 존재 여부", example = "true")
    @JsonProperty("hasRecord")
    val hasRecord: Boolean
) 