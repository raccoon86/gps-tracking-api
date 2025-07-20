package com.sponovation.runtrack.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 이벤트 상세 정보 응답 DTO
 */
@Schema(description = "이벤트 상세 정보 (코스 정보)")
data class EventDetailInfoDto(
    @Schema(description = "이벤트 상세 ID", example = "1")
    val id: Long,
    
    @Schema(description = "이벤트 ID", example = "1")
    val eventId: Long,
    
    @Schema(description = "코스 거리", example = "42.195")
    val distance: Double?,
    
    @Schema(description = "코스 이름", example = "풀코스")
    val course: String?,
    
    @Schema(description = "GPX 파일 S3 URL")
    val gpxFile: String?,
    
    @Schema(description = "생성일시")
    val createdAt: LocalDateTime,
    
    @Schema(description = "수정일시")
    val updatedAt: LocalDateTime
) 