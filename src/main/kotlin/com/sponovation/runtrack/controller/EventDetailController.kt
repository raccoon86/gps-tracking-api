package com.sponovation.runtrack.controller

import com.sponovation.runtrack.dto.EventDetailErrorResponseDto
import com.sponovation.runtrack.dto.EventDetailResponseDto
import com.sponovation.runtrack.dto.EventDetailRequestDto
import com.sponovation.runtrack.service.EventDetailService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.sponovation.runtrack.repository.EventRepository
import com.sponovation.runtrack.repository.CourseRepository
import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.common.ErrorResponse
import com.sponovation.runtrack.dto.ErrorResponseDto

/**
 * 대회 상세 조회 API 컨트롤러
 * 
 * 특정 대회 코스에 참여하는 참가자들의 실시간 위치 데이터와 
 * 대회 관련 정보를 제공하는 API를 관리합니다.
 * 
 * 새로운 응답 구조:
 * - 평면화된 대회 정보
 * - 코스 카테고리 목록
 * - 참가자 위치 데이터 (1~3위 + 트래커 목록)
 * - 상위 랭커 정보 (상세 정보 포함)
 */
@RestController
@RequestMapping("/api/v1/event-detail")
@Tag(name = "대회 상세 조회", description = "대회 상세 정보 및 실시간 참가자 위치 조회 API")
@Validated
class EventDetailController(
    private val eventDetailService: EventDetailService,
    private val eventRepository: EventRepository,
    private val courseRepository: CourseRepository
) {
    
    private val logger = LoggerFactory.getLogger(EventDetailController::class.java)
    
    /**
     * 대회 상세 정보 조회
     * 
     * 특정 대회 코스에 참여하는 참가자들의 실시간 보정 위치 데이터를 조회하여 
     * 지도에 표시할 수 있는 형태로 제공합니다.
     * 
     * 응답 구조:
     * - eventId: 이벤트 ID
     * - eventDetailId: 이벤트 상세 ID
     * - competitionName: 대회 이름
     * - courseCategory: 코스 카테고리 목록
     * - gpxFile: GPX 파일 S3 URL
     * - participantsLocations: 참가자 위치 데이터 (1~3위 + 트래커 목록)
     * - topRankers: 상위 랭커 정보 (맵 하단 표시용)
     * 
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID (대회 코스 ID)
     * @param currentUserId 현재 로그인한 사용자 ID (선택사항)
     * @return 대회 상세 정보 응답
     */
    @GetMapping("/{eventId}/{eventDetailId}")
    @Operation(
        summary = "대회 상세 정보 조회",
        description = """
            특정 대회 코스의 상세 정보와 실시간 참가자 위치 데이터를 조회합니다.
            
            **응답 구조:**
            - eventId: 이벤트 ID (예: 1)
            - eventDetailId: 이벤트 상세 ID (예: 100)
            - competitionName: 대회 이름 (예: "서울 마라톤 2025")
            - courseCategory: 코스 카테고리 목록 (거리별 분류)
            - gpxFile: GPX 파일 S3 URL
            - participantsLocations: 지도 마커로 표시될 참가자 위치 데이터
              * 1~3위 유저의 위치 데이터
              * 현재 로그인 사용자 위치 데이터 (중복 제거)
              * 트래커 목록 유저 위치 데이터 (향후 구현)
            - topRankers: 맵 하단에 표시될 상위 랭커 정보
              * 순위, 이름, 참가자 번호, 프로필 이미지 등 상세 정보
              * 완주 여부, 평균 속도, 누적 거리 등 통계 정보
            
            **지도 표시 용도:**
            - 초기 지도 로드 시 participantsLocations를 마커로 표시
            - 실시간 맵 표시는 distanceCovered (거리)를 기준으로 진행
            - topRankers 정보는 맵 하단 랭킹 표시에 활용
            
            **유효성 검증:**
            - 대회 및 코스 존재 여부 확인
            - 대회 진행 상태 확인 (진행 중인 대회만 조회 가능)
        """
    )
    fun getEventDetail(
        @Parameter(description = "이벤트 ID", required = true, example = "1")
        @PathVariable 
        @NotNull(message = "이벤트 ID는 필수입니다")
        @Positive(message = "이벤트 ID는 양수여야 합니다")
        eventId: Long,
        
        @Parameter(description = "이벤트 상세 ID (대회 코스 ID)", required = true, example = "1")
        @PathVariable
        @NotNull(message = "이벤트 상세 ID는 필수입니다")
        @Positive(message = "이벤트 상세 ID는 양수여야 합니다")
        eventDetailId: Long,
        
        @Parameter(description = "현재 로그인한 사용자 ID (선택사항)", example = "user123")
        @RequestParam(required = false)
        currentUserId: Long?
    ): ResponseEntity<Any> {
        
        logger.info("대회 상세 조회 API 요청: eventId=$eventId, eventDetailId=$eventDetailId, currentUserId=$currentUserId")
        
        return try {
            // 대회 상세 정보 조회
            val eventDetail = eventDetailService.getEventDetail(
                eventId = eventId,
                eventDetailId = eventDetailId,
                currentUserId = currentUserId
            )
            
            logger.info("대회 상세 조회 성공: eventId=$eventId, eventDetailId=$eventDetailId, " +
                "참가자 위치 수=${eventDetail.participantsLocations.size}, 상위 랭커 수=${eventDetail.topRankers.size}")
            
            ResponseEntity.ok(ApiResponse(data = eventDetail))
            
        } catch (e: IllegalArgumentException) {
            logger.warn("대회 상세 조회 실패 - 잘못된 요청: eventId=$eventId, eventDetailId=$eventDetailId, " +
                "error=${e.message}")
            val (errorCode, status) = when {
                e.message?.contains("찾을 수 없습니다") == true -> ErrorCode.RESOURCE_NOT_FOUND to HttpStatus.NOT_FOUND
                else -> ErrorCode.INVALID_INPUT_VALUE to HttpStatus.BAD_REQUEST
            }
            val errorResponse = ErrorResponse.create(status, errorCode, e.message ?: errorCode.message)
            ResponseEntity.status(status).body(errorResponse)
        } catch (e: Exception) {
            logger.error("대회 상세 조회 실패 - 서버 오류: eventId=$eventId, eventDetailId=$eventDetailId", e)
            val errorResponse = ErrorResponse.create(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.API_BAD_REQUEST, "서버 내부 오류가 발생했습니다")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }
    
    /**
     * 대회 상세 정보 조회 (POST 방식)
     * 
     * 복잡한 요청 파라미터나 인증 정보가 필요한 경우 사용합니다.
     * 
     * @param request 대회 상세 조회 요청 DTO
     * @return 대회 상세 정보 응답
     */
    @PostMapping("/venue")
    @Operation(
        summary = "대회 상세 정보 조회 (POST)",
        description = """
            POST 방식으로 대회 상세 정보를 조회합니다.
            복잡한 요청 파라미터나 인증 정보가 필요한 경우 사용합니다.
            
            **GET 방식과의 차이점:**
            - 요청 본문에 JSON 형태로 파라미터 전달
            - 향후 추가 필터링 옵션 확장 가능
            - 인증 토큰 등 민감한 정보 처리 가능
            
            **응답 구조는 GET 방식과 동일합니다.**
        """
    )
    fun getEventDetailPost(
        @Parameter(description = "대회 상세 조회 요청 데이터", required = true)
        @RequestBody
        @Validated
        request: EventDetailRequestDto
    ): ResponseEntity<Any> {
        
        logger.info("대회 상세 조회 API 요청 (POST): eventId= {request.eventId}, " +
            "eventDetailId= {request.eventDetailId}, currentUserId= {request.currentUserId}")
        
        return try {
            // 대회 상세 정보 조회
            val eventDetail = eventDetailService.getEventDetail(
                eventId = request.eventId,
                eventDetailId = request.eventDetailId,
                currentUserId = request.currentUserId
            )

            logger.info("대회 상세 조회 성공 (POST): eventId= {request.eventId}, " +
                "eventDetailId= {request.eventDetailId}, 참가자 위치 수= {eventDetail.participantsLocations.size}, " +
                "상위 랭커 수= {eventDetail.topRankers.size}")

            ResponseEntity.ok(ApiResponse(data = eventDetail))
            
        } catch (e: IllegalArgumentException) {
            logger.warn("대회 상세 조회 실패 (POST) - 잘못된 요청: eventId= {request.eventId}, " +
                "eventDetailId= {request.eventDetailId}, error= {e.message}")
            val (errorCode, status) = when {
                e.message?.contains("찾을 수 없습니다") == true -> ErrorCode.RESOURCE_NOT_FOUND to HttpStatus.NOT_FOUND
                else -> ErrorCode.INVALID_INPUT_VALUE to HttpStatus.BAD_REQUEST
            }
            val errorResponse = ErrorResponse.create(status, errorCode, e.message ?: errorCode.message)
            ResponseEntity.status(status).body(errorResponse)
        } catch (e: Exception) {
            logger.error("대회 상세 조회 실패 (POST) - 서버 오류: eventId= {request.eventId}, " +
                "eventDetailId= {request.eventDetailId}", e)
            val errorResponse = ErrorResponse.create(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.API_BAD_REQUEST, "서버 내부 오류가 발생했습니다")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    @GetMapping("/event-info/{eventId}")
    @Operation(
        summary = "이벤트 및 코스 정보 조회",
        description = "eventId로 현재 DB에 존재하는 이벤트 정보와 코스 정보를 조회합니다."
    )
    fun getEventAndCourseInfo(
        @Parameter(description = "이벤트 ID", required = true)
        @PathVariable eventId: Long
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val eventOpt = eventRepository.findById(eventId)
            if (eventOpt.isEmpty) {
                return ResponseEntity.notFound().build()
            }
            val event = eventOpt.get()
            val courses = courseRepository.findByEventIdOrderByDistanceKmAsc(eventId)
            val eventData = mapOf(
                "id" to event.id,
                "eventName" to event.eventName,
                "eventDate" to event.eventDate,
                "eventStatus" to event.eventStatus,
                "description" to event.description,
                "registrationStartDate" to event.registrationStartDate,
                "registrationEndDate" to event.registrationEndDate,
                "createdAt" to event.createdAt,
                "updatedAt" to event.updatedAt
            )
            val courseList = courses.map { course ->
                mapOf(
                    "courseId" to course.courseId,
                    "courseName" to course.courseName,
                    "eventId" to course.eventId,
                    "eventDetailId" to course.eventDetailId,
                    "distanceKm" to course.distanceKm,
                    "categoryName" to course.categoryName,
                    "gpxFileUrl" to course.gpxFileUrl,
                    "gpxDataHash" to course.gpxDataHash,
                    "startPointLat" to course.startPointLat,
                    "startPointLng" to course.startPointLng,
                    "endPointLat" to course.endPointLat,
                    "endPointLng" to course.endPointLng,
                    "createdAt" to course.createdAt,
                    "updatedAt" to course.updatedAt
                )
            }
            val response = mapOf(
                "data" to mapOf(
                    "event" to eventData,
                    "courses" to courseList
                )
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("이벤트 및 코스 정보 조회 실패: eventId=$eventId", e)
            ResponseEntity.internalServerError().build()
        }
    }
} 