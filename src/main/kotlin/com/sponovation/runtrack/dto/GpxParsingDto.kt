package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GPX 파싱 포인트 데이터 DTO
 * 
 * Redis에 저장되는 GPX 파싱 데이터의 구조를 정의합니다.
 * 키 형태: gpx:{eventId}:{eventDetailId}
 */
data class GpxParsingPointDto(
    @JsonProperty("latitude")
    val latitude: Double,
    
    @JsonProperty("longitude")
    val longitude: Double,
    
    @JsonProperty("altitude")
    val altitude: Double,
    
    @JsonProperty("sequence")
    val sequence: Int,
    
    @JsonProperty("type")
    val type: String, // start, interpolated, checkpoint, finish
    
    @JsonProperty("cpId")
    val cpId: String? = null, // START, CP1, CP2, FINISH 등
    
    @JsonProperty("cpIndex")
    val cpIndex: Int? = null // 체크포인트 순서 (0부터 시작)
)

/**
 * GPX 파싱 데이터 응답 DTO
 */
data class GpxParsingResponseDto(
    @JsonProperty("success")
    val success: Boolean,
    
    @JsonProperty("message")
    val message: String,
    
    @JsonProperty("eventId")
    val eventId: Long,
    
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,
    
    @JsonProperty("totalPoints")
    val totalPoints: Int,
    
    @JsonProperty("checkpointCount")
    val checkpointCount: Int,
    
    @JsonProperty("interpolatedPointCount")
    val interpolatedPointCount: Int,
    
    @JsonProperty("totalDistance")
    val totalDistance: Double,
    
    @JsonProperty("redisKey")
    val redisKey: String,
    
    @JsonProperty("createdAt")
    val createdAt: String
)

/**
 * GPX 파싱 데이터 조회 응답 DTO
 */
data class GpxParsingDataResponseDto(
    @JsonProperty("success")
    val success: Boolean,
    
    @JsonProperty("message")
    val message: String,
    
    @JsonProperty("eventId")
    val eventId: Long,
    
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,
    
    @JsonProperty("points")
    val points: List<GpxParsingPointDto>
) 