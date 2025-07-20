package com.sponovation.runtrack.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 이벤트 및 코스 정보 전체 응답 DTO
 */
@Schema(description = "이벤트 및 코스 정보 응답")
data class EventInfoResponseDto(
    @Schema(description = "이벤트 정보")
    val event: EventInfoDto,
    
    @Schema(description = "이벤트 상세 정보 목록 (코스 목록)")
    val eventDetails: List<EventDetailInfoDto>
) 