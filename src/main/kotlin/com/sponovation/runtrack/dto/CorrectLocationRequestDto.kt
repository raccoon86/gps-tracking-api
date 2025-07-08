package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.Valid
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GPS 위치 보정 요청 DTO
 * 
 * 실시간 GPS 데이터를 서버로 전송하여 경로 매칭 및 위치 보정을 요청할 때 사용되는 데이터 전송 객체입니다.
 * 여러 GPS 포인트를 한 번에 전송하여 배치 처리를 통한 성능 최적화를 지원합니다.
 * 
 * 사용 시나리오:
 * - 마라톤 대회 중 참가자의 실시간 위치 보정
 * - 훈련 세션 중 경로 추적 및 보정
 * - GPS 신호 불량 지역에서의 위치 복원
 */
data class CorrectLocationRequestDto(
    /** 
     * 사용자 ID
     * 위치 보정을 요청하는 사용자의 고유 식별자
     */
    @field:NotNull 
    @JsonProperty("userId") 
    val userId: Long,
    
    /** 
     * 대회 상세 ID
     * 위치 보정에 사용할 GPX 경로를 식별하는 대회 상세 정보 ID
     */
    @field:NotNull 
    @JsonProperty("eventDetailId") 
    val eventDetailId: Long,
    
    /** 
     * GPS 데이터 리스트
     * 보정할 GPS 좌표들의 리스트 (시간 순서대로 정렬 권장)
     * 여러 포인트를 한 번에 처리하여 배치 보정 성능 향상
     */
    @field:NotNull 
    @field:Valid
    @JsonProperty("gpsData") 
    val gpsData: List<GpsLocationData>
) {
    /**
     * GPS 위치 데이터
     * 
     * 위치 보정에 필요한 기본 GPS 정보를 포함합니다.
     * 간소화된 구조로 실시간 전송에 최적화되어 있습니다.
     */
    data class GpsLocationData(
        /** 위도 (WGS84 좌표계) */
        @field:NotNull 
        @JsonProperty("lat") 
        val lat: Double,
        
        /** 경도 (WGS84 좌표계) */
        @field:NotNull 
        @JsonProperty("lng") 
        val lng: Double,
        
        /** 고도 (해수면 기준 미터) */
        @JsonProperty("altitude") 
        val altitude: Double? = null,
        
        /** GPS 정확도 (미터) */
        @JsonProperty("accuracy") 
        val accuracy: Float? = null,
        
        /** 이동 속도 (m/s) */
        @JsonProperty("speed") 
        val speed: Float? = null,
        
        /** 이동 방향 (0-360도) */
        @JsonProperty("bearing") 
        val bearing: Float? = null,
        
        /** GPS 수신 시간 (ISO 8601 형식) */
        @field:NotNull 
        @JsonProperty("timestamp") 
        val timestamp: String
    )
}

 