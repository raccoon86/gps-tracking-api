package com.sponovation.runtrack.common

import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.dto.ErrorResponseDto
import org.springframework.http.HttpStatus

object ErrorResponse {
    fun create(
        status: HttpStatus,
        code: ErrorCode,
        message: String = code.message,
        detailMessage: String = ""
    ): ErrorResponseDto {
        return ErrorResponseDto(
            status = status.name,
            code = code.name,
            message = message,
            detailMessage = detailMessage
        )
    }

    // ValidationResult 데이터 클래스 정의 (내부)
    data class ValidationResult(
        val isValid: Boolean,
        val errorType: String? = null,
        val errorMessage: String? = null,
        val detailMessage: String? = null
    )

    // ValidationResult를 받아서 errorMessage만 message로 사용하는 오버로드 함수
    fun create(
        status: HttpStatus,
        code: ErrorCode,
        validationResult: ValidationResult,
        detailMessage: String = ""
    ): ErrorResponseDto {
        return create(
            status = status,
            code = code,
            message = validationResult.errorMessage ?: code.message,
            detailMessage = detailMessage
        )
    }
} 