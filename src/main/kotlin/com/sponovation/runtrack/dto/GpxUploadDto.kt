package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.Valid
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * GPX 파일 업로드 요청 DTO
 * 
 * 테스트 환경에서 GPX 파일을 업로드할 때 사용됩니다.
 * GPX 파일이 업로드되면 자동으로 100미터 간격으로 보간 포인트를 생성하여 Redis에 저장합니다.
 */
data class GpxUploadRequestDto(
    /** 
     * 사용자 ID
     * GPX 파일을 업로드하는 사용자의 고유 식별자
     */
    @field:NotNull 
    @JsonProperty("userId") 
    val userId: Long,
    
    /** 
     * 대회 상세 ID
     * GPX 경로를 저장할 대회 상세 정보 ID
     */
    @field:NotNull 
    @JsonProperty("eventDetailId") 
    val eventDetailId: Long,
    
    /** 
     * 경로 이름
     * 업로드되는 GPX 파일의 경로명
     */
    @field:NotNull 
    @JsonProperty("routeName") 
    val routeName: String,
    
    /** 
     * 경로 설명
     * 업로드되는 GPX 파일의 설명 (선택사항)
     */
    @JsonProperty("description") 
    val description: String = ""
)

/**
 * GPX 파일 업로드 응답 DTO
 * 
 * 테스트용 GPX 파일 업로드가 완료된 후 반환되는 응답 데이터입니다.
 */
data class GpxUploadResponseDto(
    /** Redis에 저장된 코스 ID */
    @JsonProperty("courseId") 
    val courseId: String,
    
    /** 데이터베이스에 저장된 GPX 경로 ID (현재는 사용 안함) */
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
 * GPX 엔티티가 삭제되어 Redis 코스 데이터만 지원
 */
@Schema(description = "GPX 파일 업로드 및 코스 데이터 생성 통합 결과")
data class GpxUploadResult(
    @Schema(description = "생성된 코스 ID", required = true)
    val courseId: String,
    
    @Schema(description = "보간된 포인트 총 개수", required = true)
    val totalInterpolatedPoints: Int,
    
    @Schema(description = "생성 시간", required = true)
    val createdAt: String,
    
    @Schema(description = "GPX 경로 엔티티 (현재는 사용 안함)")
    val gpxRoute: Any? = null
) 