package com.sponovation.runtrack.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 이벤트 정보 응답 DTO
 */
@Schema(description = "이벤트 정보")
data class EventInfoDto(
    @Schema(description = "이벤트 ID", example = "1")
    val id: Long,
    
    @Schema(description = "이벤트 이름", example = "서울 마라톤 2025")
    val name: String,
    
    @Schema(description = "스포츠 종목", example = "마라톤")
    val sports: String? = null,
    
    @Schema(description = "시작 일시")
    val startDateTime: LocalDateTime? = null,
    
    @Schema(description = "종료 일시")
    val endDateTime: LocalDateTime? = null,

    @Schema(description = "개최 국가", example = "한국")
    val country: String,
    
    @Schema(description = "개최 도시", example = "서울")
    val city: String,
    
    @Schema(description = "상세 주소", example = "서울특별시 중구 세종대로 110")
    val address: String? = null,

    @Schema(description = "개최 장소", example = "광화문광장")
    val place: String? = null,
    
    @Schema(description = "위도", example = "37.5666805")
    val latitude: Double? = null,
    
    @Schema(description = "경도", example = "126.9784147")
    val longitude: Double? = null,
    
    @Schema(description = "썸네일 이미지 URL")
    val thumbnail: String? = null,
    
    @Schema(description = "생성일시")
    val createdAt: LocalDateTime,
    
    @Schema(description = "수정일시")
    val updatedAt: LocalDateTime
) 