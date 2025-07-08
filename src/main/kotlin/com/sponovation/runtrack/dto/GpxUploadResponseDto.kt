package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.sponovation.runtrack.domain.GpxRoute
import io.swagger.v3.oas.annotations.media.Schema

/**
 * GPX 파일 업로드 응답 DTO
 * 
 * 테스트용 GPX 파일 업로드가 완료된 후 반환되는 응답 데이터입니다.
 */
data class GpxUploadResponseDto(
    /** Redis에 저장된 코스 ID */
    @JsonProperty("courseId") 
    val courseId: String,
    
    /** 데이터베이스에 저장된 GPX 경로 ID */
    @JsonProperty("routeId") 
    val routeId: Long,
    
    /** 경로 이름 */
    @JsonProperty("routeName") 
    val routeName: String,
    
    /** 총 거리 (미터) */
    @JsonProperty("totalDistance") 
    val totalDistance: Double,
    
    /** 총 보간 포인트 수 */
    @JsonProperty("totalPoints") 
    val totalPoints: Int,
    
    /** 생성 시간 */
    @JsonProperty("createdAt") 
    val createdAt: String
)

/**
 * GPX 파일 업로드 및 코스 데이터 생성 통합 결과 DTO
 */
@Schema(description = "GPX 파일 업로드 및 코스 데이터 생성 통합 결과")
data class GpxUploadResult(
    @Schema(description = "저장된 GPX Route 엔티티", required = true)
    val gpxRoute: GpxRoute,
    
    @Schema(description = "생성된 코스 ID", required = true)
    val courseId: String,
    
    @Schema(description = "보간된 포인트 총 개수", required = true)
    val totalInterpolatedPoints: Int,
    
    @Schema(description = "생성 시간", required = true)
    val createdAt: String
) 