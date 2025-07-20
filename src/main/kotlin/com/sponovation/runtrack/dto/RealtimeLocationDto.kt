package com.sponovation.runtrack.dto

/**
 * Redis 캐시용 참가자 위치 데이터
 *
 * 
 * 확장 정보:
 * - 원본 GPS 좌표와 보정된 좌표 분리
 * - 진행률 계산을 위한 추가 메트릭
 * - 캐시 효율성을 위한 플랫 구조
 */
data class ParticipantLocationCache(
    /** 참가자 사용자 ID */
    val userId: Long,

    /** 대회 ID */
    val eventId: Long,
    
    /** 대회 상세 ID */
    val eventDetailId: Long,

    /** 보정된 GPS 위도 */
    val latitude: Double,
    
    /** 보정된 GPS 경도 */
    val longitude: Double,
    
    /** 고도 정보 (선택적) */
    val altitude: Double? = null,

    /** 이동 속도 (선택적) */
    val speed: Float? = null,

    /** 이동 방향 (선택적) */
    val heading: Double? = null,
    
    /** 위치 갱신 시간 (ISO-8601 문자열) */
    val created: String,
)