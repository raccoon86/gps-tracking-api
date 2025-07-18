package com.sponovation.runtrack.controller

import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.common.ErrorResponse
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.service.TrackerService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 트래커 관리 REST API 컨트롤러
 * 
 * 사용자가 특정 참가자들을 트래킹할 수 있는 기능을 제공합니다.
 * - 관심 있는 참가자를 트래킹 목록에 추가/삭제
 * - 사용자별 트래킹 목록 조회
 * - 트래킹 통계 정보 제공
 * 
 * 주요 기능:
 * 1. 트래킹 관계 생성/삭제 (User-Participant 간 N:M 관계)
 * 2. 실시간 참가자 위치 추적을 위한 기반 데이터 관리
 * 3. 사용자 맞춤형 참가자 모니터링 지원
 * 
 * 모든 API는 표준화된 ApiResponse<T> 형태로 응답하며,
 * 에러 발생 시 ErrorResponse 형태로 응답합니다.
 */
@Tag(name = "트래커 관리", description = "참가자 트래킹 관리 API")
@RestController
@RequestMapping("/api/v1/trackers")
class TrackerController(
    private val trackerService: TrackerService
) {
    
    private val logger = LoggerFactory.getLogger(TrackerController::class.java)

    /**
     * 사용자의 트래킹 목록에 새로운 참가자를 추가합니다.
     * 
     * @param request 트래킹 추가 요청 DTO (사용자 ID, 참가자 ID)
     * @return 추가 성공 메시지 또는 에러 응답
     * 
     * 비즈니스 로직:
     * - 사용자 존재 여부 확인 (테스트 사용자 ID >= 10000 제외)
     * - 참가자 존재 여부 확인
     * - 중복 트래킹 방지 (이미 트래킹 중인 경우 예외 발생)
     * - Tracker 엔티티 생성 및 저장
     * 
     * 사용 예시:
     * - 가족/친구 참가자 모니터링
     * - 관심 있는 엘리트 러너 추적
     * - 팀 멤버들의 경기 상황 파악
     */
    @Operation(
        summary = "트래킹 추가",
        description = "사용자가 특정 참가자를 트래킹 목록에 추가합니다."
    )
    @PostMapping
    fun addTracker(
        @Valid @RequestBody request: AddTrackerRequestDto
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 트래킹 관계 생성
            val message = trackerService.addTracker(request)
            
            // 추가 성공 로그 (사용자 ID와 참가자 ID 포함)
            logger.info("트래킹 추가 성공: userId=${request.userId}, participantId=${request.participantId}")
            
            // 201 Created로 성공 메시지 반환 (새로운 트래킹 관계 생성)
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse(data = mapOf("message" to message)))
        } catch (e: IllegalArgumentException) {
            // 잘못된 요청 처리 (존재하지 않는 사용자/참가자, 중복 트래킹 등)
            logger.warn("트래킹 추가 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 예상치 못한 서버 에러 처리 (DB 제약조건 위반 등)
            logger.error("트래킹 추가 실패 - 서버 오류", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 특정 사용자가 트래킹 중인 참가자 목록을 조회합니다.
     * 
     * @param userId 트래킹 목록을 조회할 사용자 ID
     * @return 트래킹 중인 참가자 목록과 통계 정보
     * 
     * 반환 데이터:
     * - 트래킹 중인 참가자 목록 (참가자 ID, 이름, 배번, 프로필 이미지, 국가)
     * - 총 트래킹 수
     * - 이벤트별 분류 정보
     * 
     * 특징:
     * - 참가자 정보는 JOIN을 통해 한 번에 조회하여 성능 최적화
     * - 검색 API와 동일한 형태의 참가자 정보 반환으로 일관성 보장
     * - 트래킹된 순서대로 정렬 (최근 추가된 것이 먼저)
     */
    @Operation(
        summary = "트래킹 목록 조회",
        description = "사용자가 트래킹 중인 참가자 목록을 조회합니다."
    )
    @GetMapping("/{userId}")
    fun getTrackerList(
        @Parameter(description = "사용자 ID")
        @PathVariable userId: Long
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 사용자의 트래킹 목록 조회
            val trackerList = trackerService.getTrackerList(userId)
            
            // 조회 성공 로그 (트래킹 수 포함)
            logger.info("트래킹 목록 조회 성공: userId=$userId, 트래킹 수=${trackerList.participants.size}")
            
            // 200 OK로 트래킹 목록 반환
            ResponseEntity.ok(ApiResponse(data = trackerList))
        } catch (e: IllegalArgumentException) {
            // 잘못된 사용자 ID 등
            logger.warn("트래킹 목록 조회 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("트래킹 목록 조회 실패 - 서버 오류: userId=$userId", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 사용자의 트래킹 목록에서 특정 참가자를 제거합니다.
     * 
     * @param request 트래킹 삭제 요청 DTO (사용자 ID, 참가자 ID)
     * @return 삭제 성공 메시지 또는 에러 응답
     * 
     * 비즈니스 로직:
     * - 트래킹 관계 존재 여부 확인
     * - Tracker 엔티티 삭제
     * - 물리적 삭제 수행 (트래킹 기록은 보관할 필요 없음)
     * 
     * 사용 예시:
     * - 관심 없어진 참가자 제거
     * - 트래킹 목록 정리
     * - 개인정보 보호를 위한 추적 중단
     */
    @Operation(
        summary = "트래킹 삭제",
        description = "사용자가 특정 참가자를 트래킹 목록에서 삭제합니다."
    )
    @DeleteMapping
    fun removeTracker(
        @Valid @RequestBody request: RemoveTrackerRequestDto
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 트래킹 관계 삭제
            val message = trackerService.removeTracker(request)
            
            // 삭제 성공 로그
            logger.info("트래킹 삭제 성공: userId=${request.userId}, participantId=${request.participantId}")
            
            // 200 OK로 삭제 성공 메시지 반환
            ResponseEntity.ok(ApiResponse(data = mapOf("message" to message)))
        } catch (e: IllegalArgumentException) {
            // 잘못된 요청 (존재하지 않는 트래킹 관계 등)
            logger.warn("트래킹 삭제 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("트래킹 삭제 실패 - 서버 오류", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 사용자의 트래킹 활동에 대한 통계 정보를 조회합니다.
     * 
     * @param userId 통계를 조회할 사용자 ID
     * @return 트래킹 통계 정보
     * 
     * 제공되는 통계:
     * - 총 트래킹 중인 참가자 수
     * - 이벤트별 트래킹 참가자 수 분포
     * - 최근 트래킹 활동 요약
     * 
     * 활용 용도:
     * - 사용자 대시보드의 요약 정보 제공
     * - 트래킹 사용 패턴 분석
     * - 사용자 경험 개선을 위한 데이터 수집
     * 
     * 성능 고려사항:
     * - 집계 쿼리 사용으로 효율적인 통계 계산
     * - 필요시 캐싱 적용 가능
     */
    @Operation(
        summary = "트래킹 통계 조회",
        description = "사용자의 트래킹 통계 정보를 조회합니다."
    )
    @GetMapping("/{userId}/stats")
    fun getTrackerStats(
        @Parameter(description = "사용자 ID")
        @PathVariable userId: Long
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 사용자의 트래킹 통계 조회
            val stats = trackerService.getTrackerStats(userId)
            
            // 통계 조회 성공 로그
            logger.info("트래킹 통계 조회 성공: userId=$userId")
            
            // 200 OK로 통계 정보 반환
            ResponseEntity.ok(ApiResponse(data = stats))
        } catch (e: IllegalArgumentException) {
            // 잘못된 사용자 ID 등
            logger.warn("트래킹 통계 조회 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("트래킹 통계 조회 실패 - 서버 오류: userId=$userId", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }
} 