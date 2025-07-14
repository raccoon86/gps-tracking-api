package com.sponovation.runtrack.enums

import org.springframework.http.HttpStatus

enum class ErrorCode(val httpStatus: HttpStatus, val message: String) {
    //== 2xx ==//
    SUCCESS(HttpStatus.OK, "OK"),

    //== 4xx ==//
    // 공통
    API_BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 요청 값 입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원 하지 않는 method 입니다."),
    DUPLICATE_RECORD(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    // 인증
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "잘못된 로그인 정보 입니다."),
    TOKEN_NOT_FOUND(HttpStatus.BAD_REQUEST, "로그인 정보가 없습니다."),
    EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "만료된 로그인 정보 입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 사용자를 찾을 수 없습니다."),
    // 인가
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // 회원
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "정보가 없습니다.")
} 