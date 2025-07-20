package com.sponovation.runtrack.dto

/**
 * Redis 리셋 응답 DTO
 *
 * Redis 키 삭제 작업의 결과를 담은 응답 데이터 클래스입니다.
 *
 * @property success 작업 성공 여부
 * @property deletedKeyCount 삭제된 키의 개수
 * @property message 결과 메시지
 * @property resetType 리셋 유형 ("ALL_KEYS" 또는 "PATTERN")
 * @property timestamp 작업 수행 시각 (Unix timestamp)
 * @property pattern 패턴 삭제 시 사용된 패턴 (선택적)
 */
data class RedisResetResponseDto(
    val success: Boolean,
    val deletedKeyCount: Long,
    val message: String,
    val resetType: String,
    val timestamp: Long,
    val pattern: String? = null
)