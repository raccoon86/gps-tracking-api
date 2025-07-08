package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.Valid
import com.fasterxml.jackson.annotation.JsonProperty

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