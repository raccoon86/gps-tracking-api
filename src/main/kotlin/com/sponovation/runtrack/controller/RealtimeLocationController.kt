package com.sponovation.runtrack.controller

import com.sponovation.runtrack.dto.RealtimeLocationResponseDto
import com.sponovation.runtrack.service.RealtimeLocationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@Tag(name = "실시간 위치 추적", description = "대회 참가자들의 실시간 위치 조회 API")
class RealtimeLocationController(
    private val realtimeLocationService: RealtimeLocationService
) {
    
    private val logger = LoggerFactory.getLogger(RealtimeLocationController::class.java)
    
    @GetMapping("/realtime-locations")
    @Operation(
        summary = "대회 실시간 위치 조회",
        description = "특정 대회의 모든 참가자들의 실시간 위치와 상위 3명의 순위를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (eventDetailId 누락)"),
            ApiResponse(responseCode = "404", description = "해당 대회를 찾을 수 없음"),
            ApiResponse(responseCode = "500", description = "서버 내부 오류")
        ]
    )
    fun getRealtimeLocations(
        @Parameter(description = "대회 상세 ID", required = true, example = "456")
        @RequestParam eventDetailId: Long?,
        
        @Parameter(description = "지도 줌 레벨 (1-20)", required = false, example = "14")
        @RequestParam(required = false) zoomLevel: Int?
    ): ResponseEntity<RealtimeLocationResponseDto> {
        
        logger.info("실시간 위치 조회 요청: eventDetailId=$eventDetailId, zoomLevel=$zoomLevel")
        
        // 필수 파라미터 검증
        if (eventDetailId == null) {
            logger.warn("eventDetailId가 누락되었습니다")
            return ResponseEntity.badRequest().build()
        }
        
        // 줌 레벨 유효성 검증
        if (zoomLevel != null && (zoomLevel < 1 || zoomLevel > 20)) {
            logger.warn("잘못된 줌 레벨: $zoomLevel")
            return ResponseEntity.badRequest().build()
        }
        
        return try {
            val response = realtimeLocationService.getParticipantsLocations(eventDetailId, zoomLevel)
            
            logger.info("실시간 위치 조회 성공: eventDetailId=$eventDetailId, 참가자수=${response.data.participants.size}")
            
            ResponseEntity.ok(response)
            
        } catch (e: IllegalArgumentException) {
            logger.warn("실시간 위치 조회 실패 - 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().build()
        } catch (e: NoSuchElementException) {
            logger.warn("실시간 위치 조회 실패 - 대회를 찾을 수 없음: eventDetailId=$eventDetailId")
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            logger.error("실시간 위치 조회 실패 - 서버 오류: eventDetailId=$eventDetailId", e)
            ResponseEntity.internalServerError().build()
        }
    }
} 