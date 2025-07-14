package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.Valid
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotEmpty

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
     * GPS 데이터 리스트
     * 보정할 GPS 좌표들의 리스트 (시간 순서대로 정렬 권장)
     * 여러 포인트를 한 번에 처리하여 배치 보정 성능 향상
     */
    @field:NotNull 
    @field:Valid
    @field:NotEmpty(message = "gpsData는 비어있을 수 없습니다.")
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
 * 기존 API와의 호환성을 위해 간단한 구조를 유지합니다.
 */
data class CorrectLocationResponseDto(
    @JsonProperty("data")
    val data: CorrectedLocationDataDto,
    
    @JsonProperty("checkpointReaches")
    val checkpointReaches: List<CheckpointReachDto>? = null,
    
    /** 가장 가까운 GPX 포인트 정보 */
    @JsonProperty("nearestGpxPoint")
    val nearestGpxPoint: NearestGpxPointDto? = null,
    
    /** 매칭 품질 정보 */
    @JsonProperty("matchingQuality")
    val matchingQuality: MatchingQualityDto? = null
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

/**
 * 보정된 위치 데이터 DTO
 */
data class CorrectedLocationDataDto(
    @JsonProperty("correctedLat")
    val correctedLat: Double,
    
    @JsonProperty("correctedLng")
    val correctedLng: Double,
    
    /** 보정된 고도 (미터) */
    @JsonProperty("correctedAltitude")
    val correctedAltitude: Double? = null
)

/**
 * 가장 가까운 GPX 포인트 정보 DTO
 * 
 * 현재 GPS 위치에서 가장 가까운 GPX 경로 포인트의 상세 정보를 제공합니다.
 */
data class NearestGpxPointDto(
    /** 가장 가까운 GPX 포인트의 위도 */
    @JsonProperty("latitude")
    val latitude: Double,
    
    /** 가장 가까운 GPX 포인트의 경도 */
    @JsonProperty("longitude")
    val longitude: Double,
    
    /** 가장 가까운 GPX 포인트의 고도 */
    @JsonProperty("elevation")
    val elevation: Double? = null,
    
    /** 현재 GPS 위치에서 가장 가까운 GPX 포인트까지의 거리 (미터) */
    @JsonProperty("distanceToPoint")
    val distanceToPoint: Double,
    
    /** 시작점으로부터 가장 가까운 GPX 포인트까지의 거리 (미터) */
    @JsonProperty("distanceFromStart")
    val distanceFromStart: Double,
    
    /** 경로 진행률 (0.0 ~ 1.0) */
    @JsonProperty("routeProgress")
    val routeProgress: Double,
    
    /** 해당 포인트에서의 경로 방향 (북쪽 기준 시계방향, 0-360도) */
    @JsonProperty("routeBearing")
    val routeBearing: Double? = null
)

/**
 * 매칭 품질 정보 DTO
 */
data class MatchingQualityDto(
    /** 매칭 성공 여부 */
    @JsonProperty("isMatched")
    val isMatched: Boolean,
    
    /** 매칭 점수 (0에 가까울수록 좋음) */
    @JsonProperty("matchScore")
    val matchScore: Double,
    
    /** 현재 이동 방향 (도) */
    @JsonProperty("currentBearing")
    val currentBearing: Double? = null,
    
    /** 경로 방향 (도) */
    @JsonProperty("routeBearing")
    val routeBearing: Double? = null,
    
    /** 방향 차이 (도) */
    @JsonProperty("bearingDifference")
    val bearingDifference: Double? = null,
    
    /** 매칭된 세그먼트 인덱스 */
    @JsonProperty("segmentIndex")
    val segmentIndex: Int? = null,
    
    /** GPS 신뢰도 (0.0 ~ 1.0) */
    @JsonProperty("gpsConfidence")
    val gpsConfidence: Double? = null,
    
    /** 칼만 필터 불확실성 (미터) */
    @JsonProperty("kalmanUncertainty")
    val kalmanUncertainty: CorrectionUncertaintyDto? = null,
    
    /** 보정 강도 (0.0 ~ 1.0, 높을수록 많이 보정됨) */
    @JsonProperty("correctionStrength")
    val correctionStrength: Double? = null,
    
    /** 보정 품질 등급 */
    @JsonProperty("qualityGrade")
    val qualityGrade: String? = null
)

/**
 * 보정 불확실성 정보 DTO
 */
data class CorrectionUncertaintyDto(
    /** 위도 불확실성 (미터) */
    @JsonProperty("latitudeUncertainty")
    val latitudeUncertainty: Double,
    
    /** 경도 불확실성 (미터) */
    @JsonProperty("longitudeUncertainty")
    val longitudeUncertainty: Double,
    
    /** 고도 불확실성 (미터) */
    @JsonProperty("altitudeUncertainty")
    val altitudeUncertainty: Double? = null
)

 