package com.sponovation.runtrack.dto

/**
 * API 에러 응답 표준 DTO
 * @param status HTTP 상태 (예: "BAD_REQUEST")
 * @param code 에러 코드 (예: "EXPIRED_TOKEN")
 * @param message 사용자 메시지 (예: "만료된 로그인 정보 입니다.")
 * @param detailMessage 상세 메시지 (선택, 기본값 "")
 */
data class ErrorResponseDto(
    val status: String,           // ex) "BAD_REQUEST"
    val code: String,             // ex) "EXPIRED_TOKEN"
    val message: String,          // ex) "만료된 로그인 정보 입니다."
    val detailMessage: String = "" // ex) 상세 메시지(선택)
) 