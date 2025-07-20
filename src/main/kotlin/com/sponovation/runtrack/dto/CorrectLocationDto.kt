package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.Valid
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GPS 위치 보정 요청 DTO
 * 
 * 실시간 GPS 데이터를 서버로 전송하여 경로 매칭 및 위치 보정을 요청할 때 사용되는 데이터 전송 객체입니다.
 * 단일 GPS 포인트를 전송하여 실시간 위치 보정을 처리합니다.
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
     * 대회 ID
     * 위치 보정에 사용할 GPX 경로를 식별하는 대회 ID
     */
    @field:NotNull(message = "eventId는 필수입니다.")
    @field:Positive(message = "eventId는 1 이상이어야 합니다.")
    @JsonProperty("eventId")
    val eventId: Long,

    /** 
     * 대회 상세 ID
     * 위치 보정에 사용할 GPX 경로를 식별하는 대회 상세 정보 ID
     */
    @field:NotNull(message = "eventDetailId는 필수입니다.")
    @field:Positive(message = "eventDetailId는 1 이상이어야 합니다.")
    @JsonProperty("eventDetailId") 
    val eventDetailId: Long,
    
    /** 
     * GPS 데이터
     * 보정할 GPS 좌표 정보 리스트 (위도, 경도, 고도, 정확도, 속도, 방향 등)
     * 여러 GPS 포인트를 배치로 처리 가능
     */
    @field:NotNull(message = "gpsData는 필수입니다.")
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
        @JsonProperty("heading")
        val heading: Float? = null,
        
        /** GPS 수신 시간 (ISO 8601 형식 또는 Unix timestamp) */
        @field:NotNull 
        @JsonProperty("timestamp") 
        val timestamp: String
    )
}

/**
 * GPS 위치 보정 응답 DTO
 * 
 * 서버에서 GPS 위치 보정을 완료한 후 클라이언트에게 반환하는 결과 데이터입니다.
 * 간단하고 필수적인 정보만 포함합니다.
 */
data class CorrectLocationResponseDto(
    @JsonProperty("userId")
    val userId: Long,
    
    @JsonProperty("eventId")
    val eventId: Long,
    
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,
    
    @JsonProperty("latitude")
    val latitude: Double,
    
    @JsonProperty("longitude")
    val longitude: Double,
    
    @JsonProperty("altitude")
    val altitude: Double? = null,
    
    @JsonProperty("speed")
    val speed: Float? = null,
    
    @JsonProperty("timestamp")
    val timestamp: String
)

/**
 * 체크포인트 도달 정보 DTO
 */
data class CheckpointReachDto(
    @JsonProperty("checkpointId")
    val checkpointId: String,
    
    /** 체크포인트 ID (GPX 파싱 데이터의 cpId) */
    @JsonProperty("cpId")
    val cpId: String? = null,
    
    /** 체크포인트 인덱스 (GPX 파싱 데이터의 cpIndex) */
    @JsonProperty("cpIndex")
    val cpIndex: Int? = null,
    
    /** 통과 시각 (Unix Timestamp) */
    @JsonProperty("passTime")
    val passTime: Long,
    
    /** 이전 CP부터 현재 CP까지의 구간 소요 시간 (초) */
    @JsonProperty("segmentDuration")
    val segmentDuration: Long? = null,
    
    /** 시작점부터 현재 CP까지의 누적 소요 시간 (초) */
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long? = null,
    
    @JsonProperty("reachedAt")
    val reachedAt: Long? = null
) 