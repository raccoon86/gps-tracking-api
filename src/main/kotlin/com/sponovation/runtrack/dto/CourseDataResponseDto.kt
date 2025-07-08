package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.sponovation.runtrack.service.InterpolatedPoint

/**
 * 코스 데이터 조회 응답 DTO
 * 
 * Redis에 저장된 코스 데이터를 조회할 때 사용되는 응답 DTO입니다.
 */
data class CourseDataResponseDto(
    /** 코스 고유 ID */
    @JsonProperty("courseId") 
    val courseId: String,
    
    /** 이벤트 ID */
    @JsonProperty("eventId") 
    val eventId: Long,
    
    /** GPX 파일명 */
    @JsonProperty("fileName") 
    val fileName: String,
    
    /** 총 거리 (킬로미터) */
    @JsonProperty("totalDistance") 
    val totalDistance: Double,
    
    /** 총 보간 포인트 수 */
    @JsonProperty("totalPoints") 
    val totalPoints: Int,
    
    /** 100미터 간격 보간 포인트 리스트 */
    @JsonProperty("interpolatedPoints") 
    val interpolatedPoints: List<InterpolatedPointDto>,
    
    /** 생성 시간 */
    @JsonProperty("createdAt") 
    val createdAt: String
)

/**
 * 보간 포인트 DTO
 */
data class InterpolatedPointDto(
    /** 위도 */
    @JsonProperty("latitude") 
    val latitude: Double,
    
    /** 경도 */
    @JsonProperty("longitude") 
    val longitude: Double,
    
    /** 고도 */
    @JsonProperty("elevation") 
    val elevation: Double,
    
    /** 시작점으로부터의 거리 (미터) */
    @JsonProperty("distanceFromStart") 
    val distanceFromStart: Double
)

/**
 * 코스 데이터 목록 응답 DTO
 */
data class CourseDataListResponseDto(
    /** 코스 데이터 목록 */
    @JsonProperty("courses") 
    val courses: List<CourseDataSummaryDto>,
    
    /** 총 개수 */
    @JsonProperty("totalCount") 
    val totalCount: Int
)

/**
 * 코스 데이터 요약 DTO
 */
data class CourseDataSummaryDto(
    /** 코스 고유 ID */
    @JsonProperty("courseId") 
    val courseId: String,
    
    /** 이벤트 ID */
    @JsonProperty("eventId") 
    val eventId: Long,
    
    /** GPX 파일명 */
    @JsonProperty("fileName") 
    val fileName: String,
    
    /** 총 거리 (킬로미터) */
    @JsonProperty("totalDistance") 
    val totalDistance: Double,
    
    /** 총 보간 포인트 수 */
    @JsonProperty("totalPoints") 
    val totalPoints: Int,
    
    /** 생성 시간 */
    @JsonProperty("createdAt") 
    val createdAt: String
) 