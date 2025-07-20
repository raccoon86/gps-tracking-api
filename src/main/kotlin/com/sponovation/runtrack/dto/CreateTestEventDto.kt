package com.sponovation.runtrack.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 테스트용 Event 생성 요청 DTO
 */
@Schema(description = "테스트용 Event 생성 요청")
data class CreateTestEventRequestDto(
    @Schema(description = "이벤트 이름", example = "서울 마라톤 2024")
    @field:NotBlank(message = "이벤트 이름은 필수입니다")
    @field:Size(max = 100, message = "이벤트 이름은 100자를 초과할 수 없습니다")
    val name: String,
    
    @Schema(description = "스포츠 종목", example = "마라톤")
    val sports: String = "마라톤",
    
    @Schema(description = "시작 일시")
    val startDateTime: LocalDateTime? = null,
    
    @Schema(description = "종료 일시")
    val endDateTime: LocalDateTime? = null,
    
    @Schema(description = "국가", example = "대한민국")
    val country: String = "대한민국",
    
    @Schema(description = "도시", example = "서울")
    val city: String = "서울",
    
    @Schema(description = "주소", example = "테스트 주소")
    val address: String = "테스트 주소",
    
    @Schema(description = "장소", example = "테스트 장소")
    val place: String = "테스트 장소",
    
    @Schema(description = "위도", example = "37.5413553485092")
    val latitude: Double = 37.5413553485092,
    
    @Schema(description = "경도", example = "127.115719020367")
    val longitude: Double = 127.115719020367,
    
    @Schema(description = "썸네일 URL")
    val thumbnail: String? = null
)

/**
 * 테스트용 Event 생성 응답 DTO
 */
@Schema(description = "테스트용 Event 생성 응답")
data class CreateTestEventResponseDto(
    @Schema(description = "이벤트 ID")
    val eventId: Long,
    
    @Schema(description = "이벤트 이름")
    val eventName: String,
    
    @Schema(description = "스포츠 종목")
    val sports: String,
    
    @Schema(description = "시작 일시")
    val startDateTime: String,
    
    @Schema(description = "종료 일시")
    val endDateTime: String,
    
    @Schema(description = "국가")
    val country: String,
    
    @Schema(description = "도시")
    val city: String,
    
    @Schema(description = "생성 일시")
    val createdAt: String,
    
    @Schema(description = "메시지")
    val message: String,
    
    @Schema(description = "새로 생성되었는지 여부")
    val isNewlyCreated: Boolean
) 