package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 리더보드 순위 정보 DTO
 */
@Schema(description = "리더보드 순위 정보")
data class LeaderboardRankingDto(
    @Schema(description = "순위 (1부터 시작)", example = "1")
    @JsonProperty("rank")
    val rank: Long,
    
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "점수", example = "1000001400.0")
    @JsonProperty("score")
    val score: Double,
    
    @Schema(description = "체크포인트 순서", example = "1")
    @JsonProperty("checkpointOrder")
    val checkpointOrder: Int,
    
    @Schema(description = "누적 소요 시간 (초)", example = "1400")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long,
    
    @Schema(description = "누적 소요 시간 (포맷된 문자열)", example = "23:20")
    @JsonProperty("cumulativeTimeFormatted")
    val cumulativeTimeFormatted: String
)

/**
 * 리더보드 전체 순위 조회 응답 DTO
 */
@Schema(description = "리더보드 전체 순위 조회 응답")
data class LeaderboardResponseDto(
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "순위 목록")
    @JsonProperty("rankings")
    val rankings: List<LeaderboardRankingDto>,
    
    @Schema(description = "전체 참가자 수", example = "150")
    @JsonProperty("totalParticipants")
    val totalParticipants: Long,
    
    @Schema(description = "조회된 순위 수", example = "10")
    @JsonProperty("resultCount")
    val resultCount: Int
)

/**
 * 사용자 순위 조회 응답 DTO
 */
@Schema(description = "사용자 순위 조회 응답")
data class UserRankingDto(
    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,
    
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "순위 (1부터 시작)", example = "5")
    @JsonProperty("rank")
    val rank: Long?,
    
    @Schema(description = "점수", example = "1000001400.0")
    @JsonProperty("score")
    val score: Double?,
    
    @Schema(description = "체크포인트 순서", example = "1")
    @JsonProperty("checkpointOrder")
    val checkpointOrder: Int?,
    
    @Schema(description = "누적 소요 시간 (초)", example = "1400")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long?,
    
    @Schema(description = "누적 소요 시간 (포맷된 문자열)", example = "23:20")
    @JsonProperty("cumulativeTimeFormatted")
    val cumulativeTimeFormatted: String?,
    
    @Schema(description = "리더보드 등록 여부", example = "true")
    @JsonProperty("isRegistered")
    val isRegistered: Boolean
)

/**
 * 리더보드 통계 DTO
 */
@Schema(description = "리더보드 통계")
data class LeaderboardStatsDto(
    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: String,
    
    @Schema(description = "전체 참가자 수", example = "150")
    @JsonProperty("totalParticipants")
    val totalParticipants: Long,
    
    @Schema(description = "완주자 수 (모든 체크포인트 통과)", example = "45")
    @JsonProperty("finishers")
    val finishers: Long,
    
    @Schema(description = "완주율 (%)", example = "30.0")
    @JsonProperty("finishRate")
    val finishRate: Double,
    
    @Schema(description = "1위 기록")
    @JsonProperty("firstPlace")
    val firstPlace: LeaderboardRankingDto?,
    
    @Schema(description = "평균 완주 시간 (초)", example = "5400")
    @JsonProperty("averageFinishTime")
    val averageFinishTime: Long?,
    
    @Schema(description = "평균 완주 시간 (포맷된 문자열)", example = "1:30:00")
    @JsonProperty("averageFinishTimeFormatted")
    val averageFinishTimeFormatted: String?
)

/**
 * 리더보드 업데이트 응답 DTO
 */
@Schema(description = "리더보드 업데이트 응답")
data class LeaderboardUpdateResponseDto(
    @Schema(description = "성공 여부", example = "true")
    @JsonProperty("success")
    val success: Boolean,
    
    @Schema(description = "메시지", example = "리더보드가 성공적으로 업데이트되었습니다")
    @JsonProperty("message")
    val message: String,
    
    @Schema(description = "업데이트된 사용자 순위 정보")
    @JsonProperty("userRanking")
    val userRanking: UserRankingDto?
) 