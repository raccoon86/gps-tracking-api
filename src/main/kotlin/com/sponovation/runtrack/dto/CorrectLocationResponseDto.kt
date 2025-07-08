package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GPS 위치 보정 응답 DTO
 * 
 * 서버에서 GPS 위치 보정을 완료한 후 클라이언트에게 반환하는 결과 데이터입니다.
 * 기존 API와의 호환성을 위해 간단한 구조를 유지합니다.
 */
data class CorrectLocationResponseDto(
    @JsonProperty("data")
    val data: CorrectedLocationDataDto
)

/**
 * 보정된 위치 데이터
 * 
 * 칼만 필터 및 맵 매칭을 통해 보정된 GPS 좌표 정보입니다.
 */
data class CorrectedLocationDataDto(
    @JsonProperty("correctedLat")
    val correctedLat: Double,
    
    @JsonProperty("correctedLng") 
    val correctedLng: Double
) 