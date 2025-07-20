package com.sponovation.runtrack.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 테스트용 EventDetail(Course) 생성 요청 DTO
 */
@Schema(description = "테스트용 EventDetail 생성 요청")
data class CreateTestCourseRequestDto(
    @Schema(description = "이벤트 ID", example = "1")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,
    
    @Schema(description = "코스 이름", example = "5km 코스")
    @field:NotBlank(message = "코스 이름은 필수입니다")
    @field:Size(max = 100, message = "코스 이름은 100자를 초과할 수 없습니다")
    val courseName: String,
    
    @Schema(description = "거리 (km)", example = "5")
    val distance: Double? = 5.0,
    
    @Schema(description = "GPX 파일 URL")
    val gpxFile: String? = null,
    
    @Schema(description = "시작 일시")
    val startDateTime: LocalDateTime? = null,
    
    @Schema(description = "종료 일시")
    val endDateTime: LocalDateTime? = null
)

/**
 * 테스트용 EventDetail(Course) 생성 응답 DTO
 */
@Schema(description = "테스트용 EventDetail 생성 응답")
data class CreateTestCourseResponseDto(
    @Schema(description = "이벤트 상세 ID")
    val eventDetailId: Long,
    
    @Schema(description = "이벤트 ID")
    val eventId: Long,
    
    @Schema(description = "거리 (km)")
    val distance: Double? = null,
    
    @Schema(description = "코스 이름")
    val course: String,
    
    @Schema(description = "GPX 파일 URL")
    val gpxFile: String?,
    
    @Schema(description = "시작 일시")
    val startDateTime: String?,
    
    @Schema(description = "종료 일시")
    val endDateTime: String?,
    
    @Schema(description = "생성 일시")
    val createdAt: String,
    
    @Schema(description = "메시지")
    val message: String,
    
    @Schema(description = "새로 생성되었는지 여부")
    val isNewlyCreated: Boolean
) 