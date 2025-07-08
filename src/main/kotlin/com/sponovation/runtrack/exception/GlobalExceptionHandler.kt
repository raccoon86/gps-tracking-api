package com.sponovation.runtrack.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.util.NoSuchElementException
import com.sponovation.runtrack.service.CourseDataException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)



    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.warn("잘못된 상태: ${e.message}")
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "INVALID_STATE",
                message = e.message ?: "잘못된 상태입니다",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "필수 파라미터 누락"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "필수 파라미터 누락")))
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        logger.warn("파일 크기 초과: ${e.message}")
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "FILE_SIZE_EXCEEDED",
                message = "업로드 파일 크기가 너무 큽니다",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @ExceptionHandler(GpxParsingException::class)
    fun handleGpxParsingException(e: GpxParsingException): ResponseEntity<ErrorResponse> {
        logger.error("GPX 파싱 오류: ${e.message}")
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "GPX_PARSING_ERROR",
                message = e.message ?: "GPX 파일 파싱에 실패했습니다",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @ExceptionHandler(TrackingException::class)
    fun handleTrackingException(e: TrackingException): ResponseEntity<ErrorResponse> {
        logger.error("트래킹 오류: ${e.message}")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                code = "TRACKING_ERROR",
                message = e.message ?: "트래킹 처리 중 오류가 발생했습니다",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("예상치 못한 오류", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                code = "INTERNAL_SERVER_ERROR",
                message = "서버 내부 오류가 발생했습니다",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFoundException(ex: NoSuchElementException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (ex.message ?: "대회 정보 또는 GPX 파일 없음")))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (ex.message ?: "보정 로직 오류")))
    }

    @ExceptionHandler(CourseDataException::class)
    fun handleCourseDataException(ex: CourseDataException): ResponseEntity<Map<String, String>> {
        logger.error("코스 데이터 처리 오류: ${ex.message}", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (ex.message ?: "코스 데이터 처리 중 오류가 발생했습니다")))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: List<String>? = null,
    val timestamp: Long
)

// 커스텀 예외 클래스들
class GpxParsingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class TrackingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) 
