package com.sponovation.runtrack.controller

import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.common.ErrorResponse
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.service.ParticipantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 참가자 관리 REST API 컨트롤러
 * 
 * 마라톤/러닝 이벤트의 참가자에 대한 CRUD 작업을 처리합니다.
 * - 참가자 생성, 조회, 수정, 삭제
 * - 이벤트별 참가자 목록 조회
 * - 참가자 검색 (이름/배번 기반, 커서 페이지네이션)
 * - 테스트 데이터 생성/삭제 (개발용)
 * 
 * 모든 API는 표준화된 ApiResponse<T> 형태로 응답하며,
 * 에러 발생 시 ErrorResponse 형태로 응답합니다.
 */
@Tag(name = "참가자 관리", description = "참가자 CRUD 및 테스트 데이터 생성 API")
@RestController
@RequestMapping("/api/participants")
class ParticipantController(
    private val participantService: ParticipantService
) {
    
    private val logger = LoggerFactory.getLogger(ParticipantController::class.java)

    /**
     * 새로운 참가자를 생성합니다.
     * 
     * @param request 참가자 생성 요청 DTO (이벤트ID, 이름, 닉네임, 국가, 프로필 이미지 등)
     * @return 생성된 참가자 정보 또는 에러 응답
     * 
     * 비즈니스 로직:
     * - 입력 데이터 유효성 검증 (@Valid 어노테이션을 통한 자동 검증)
     * - 이벤트 존재 여부 확인
     * - 참가자 엔티티 생성 및 저장
     * - 성공 시 201 Created 상태 코드 반환
     */
    @Operation(
        summary = "참가자 생성",
        description = "새로운 참가자를 생성합니다."
    )
    @PostMapping
    fun createParticipant(
        @Valid @RequestBody request: ParticipantRequestDto
    ): ResponseEntity<Any> {
        return try {
            // 서비스 레이어에서 참가자 생성 비즈니스 로직 수행
            val participant = participantService.createParticipant(request)
            
            // 성공 로그 기록 (참가자 ID와 이벤트 ID 포함)
            logger.info("참가자 생성 성공: participantId=${participant.id}, eventId=${request.eventId}")
            
            // 201 Created 상태로 응답 (새 리소스 생성 성공)
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse(data = participant))
        } catch (e: IllegalArgumentException) {
            // 잘못된 입력값에 대한 에러 처리 (400 Bad Request)
            logger.warn("참가자 생성 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 예상치 못한 서버 에러 처리 (500 Internal Server Error)
            logger.error("참가자 생성 실패 - 서버 오류", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * ID로 특정 참가자의 상세 정보를 조회합니다.
     * 
     * @param id 조회할 참가자의 고유 식별자
     * @return 참가자 상세 정보 또는 에러 응답 (404 Not Found)
     * 
     * 비즈니스 로직:
     * - 참가자 ID 존재 여부 확인
     * - 참가자 엔티티 조회
     * - DTO 변환 후 응답
     */
    @Operation(
        summary = "참가자 조회",
        description = "ID로 참가자를 조회합니다."
    )
    @GetMapping("/{id}")
    fun getParticipant(
        @Parameter(description = "참가자 ID")
        @PathVariable id: Long
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 참가자 조회
            val participant = participantService.getParticipant(id)
            
            // 조회 성공 로그
            logger.info("참가자 조회 성공: participantId=$id")
            
            // 200 OK로 참가자 정보 반환
            ResponseEntity.ok(ApiResponse(data = participant))
        } catch (e: IllegalArgumentException) {
            // 존재하지 않는 참가자 ID에 대한 404 에러
            logger.warn("참가자 조회 실패 - 존재하지 않는 참가자: $id")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.NOT_FOUND,
                code = ErrorCode.RESOURCE_NOT_FOUND,
                message = e.message ?: "참가자를 찾을 수 없습니다"
            )
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("참가자 조회 실패 - 서버 오류: participantId=$id", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 기존 참가자의 정보를 수정합니다.
     * 
     * @param id 수정할 참가자의 ID
     * @param request 수정할 데이터가 담긴 DTO (이름, 닉네임, 국가, 프로필 이미지 등)
     * @return 수정된 참가자 정보 또는 에러 응답
     * 
     * 비즈니스 로직:
     * - 참가자 존재 여부 확인
     * - 수정 가능한 필드들만 업데이트 (부분 업데이트 지원)
     * - 변경된 엔티티 저장
     */
    @Operation(
        summary = "참가자 수정",
        description = "참가자 정보를 수정합니다."
    )
    @PutMapping("/{id}")
    fun updateParticipant(
        @Parameter(description = "참가자 ID")
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateParticipantRequestDto
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 참가자 정보 업데이트
            val participant = participantService.updateParticipant(id, request)
            
            // 수정 성공 로그
            logger.info("참가자 수정 성공: participantId=$id")
            
            // 200 OK로 수정된 정보 반환
            ResponseEntity.ok(ApiResponse(data = participant))
        } catch (e: IllegalArgumentException) {
            // 잘못된 입력 또는 존재하지 않는 참가자
            logger.warn("참가자 수정 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("참가자 수정 실패 - 서버 오류: participantId=$id", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 참가자를 시스템에서 완전히 삭제합니다.
     * 
     * @param id 삭제할 참가자의 ID
     * @return 삭제 성공 메시지 또는 에러 응답
     * 
     * 주의사항:
     * - 물리적 삭제를 수행하므로 신중하게 사용
     * - 연관된 데이터(트래킹, 기록 등)도 함께 삭제될 수 있음
     * - 실제 운영에서는 논리적 삭제(soft delete) 고려 필요
     */
    @Operation(
        summary = "참가자 삭제",
        description = "참가자를 삭제합니다."
    )
    @DeleteMapping("/{id}")
    fun deleteParticipant(
        @Parameter(description = "참가자 ID")
        @PathVariable id: Long
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 참가자 삭제 수행
            participantService.deleteParticipant(id)
            
            // 삭제 성공 로그
            logger.info("참가자 삭제 성공: participantId=$id")
            
            // 200 OK로 삭제 성공 메시지 반환
            ResponseEntity.ok(ApiResponse(data = "참가자가 성공적으로 삭제되었습니다"))
        } catch (e: IllegalArgumentException) {
            // 존재하지 않는 참가자에 대한 삭제 시도
            logger.warn("참가자 삭제 실패 - 존재하지 않는 참가자: $id")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.NOT_FOUND,
                code = ErrorCode.RESOURCE_NOT_FOUND,
                message = e.message ?: "참가자를 찾을 수 없습니다"
            )
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류 (FK 제약조건 위반 등)
            logger.error("참가자 삭제 실패 - 서버 오류: participantId=$id", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 특정 이벤트에 참가한 모든 참가자 목록을 페이지 단위로 조회합니다.
     * 
     * @param eventId 조회할 이벤트의 ID
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지당 항목 수
     * @return 참가자 목록 (페이지네이션 적용)
     * 
     * 페이지네이션:
     * - 오프셋 기반 페이지네이션 사용
     * - 기본 페이지 크기: 20
     * - 성능 최적화를 위해 필요한 필드만 조회
     */
    @Operation(
        summary = "대회 참가자 목록 조회",
        description = "특정 이벤트의 참가자 목록을 조회합니다."
    )
    @GetMapping("/event/{eventId}")
    fun getParticipantsByEvent(
        @Parameter(description = "이벤트 ID")
        @PathVariable eventId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기")
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 이벤트별 참가자 목록 조회 (페이지네이션 적용)
            val participants = participantService.getParticipantsByEvent(eventId, page, size)
            
            // 조회 성공 로그 (참가자 수 포함)
            logger.info("이벤트별 참가자 목록 조회 성공: eventId=$eventId, 참가자 수=${participants.size}")
            
            // 200 OK로 참가자 목록 반환
            ResponseEntity.ok(ApiResponse(data = participants))
        } catch (e: IllegalArgumentException) {
            // 잘못된 이벤트 ID 또는 페이지 파라미터
            logger.warn("이벤트별 참가자 목록 조회 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("이벤트별 참가자 목록 조회 실패 - 서버 오류: eventId=$eventId", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 개발 및 테스트 목적으로 대량의 참가자 데이터를 생성합니다.
     * 
     * @param request 테스트 참가자 생성 요청 (이벤트 ID, 생성할 수량 등)
     * @return 생성 결과 (생성된 참가자 수, 소요 시간 등)
     * 
     * 기능:
     * - 실제적인 한국 이름과 다양한 국가 데이터 생성
     * - 배번은 자동으로 순차 할당
     * - 성능 테스트를 위한 대량 데이터 생성 지원
     * - 트랜잭션 내에서 배치 처리로 성능 최적화
     * 
     * 주의: 운영 환경에서는 사용하지 않도록 주의
     */
    @Operation(
        summary = "테스트 참가자 생성",
        description = "테스트용 참가자 데이터를 대량으로 생성합니다. 개발 및 테스트 목적으로 사용됩니다."
    )
    @PostMapping("/test/create")
    fun createTestParticipants(
        @Valid @RequestBody request: CreateTestParticipantsRequestDto
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 테스트 참가자 대량 생성
            val response = participantService.createTestParticipants(request)
            
            // 생성 성공 로그 (이벤트 ID와 생성 수량 포함)
            logger.info("테스트 참가자 생성 성공: eventId=${request.eventId}, 생성 수=${response.createdCount}")
            
            // 201 Created로 생성 결과 반환
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse(data = response))
        } catch (e: IllegalArgumentException) {
            // 잘못된 요청 (존재하지 않는 이벤트, 잘못된 수량 등)
            logger.warn("테스트 참가자 생성 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류 (DB 제약조건, 메모리 부족 등)
            logger.error("테스트 참가자 생성 실패 - 서버 오류: eventId=${request.eventId}", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 특정 이벤트의 모든 테스트 참가자를 삭제합니다.
     * 
     * @param eventId 테스트 참가자를 삭제할 이벤트 ID
     * @return 삭제 결과 (삭제된 참가자 수)
     * 
     * 기능:
     * - 테스트 환경 정리 목적
     * - 대량 삭제 작업이므로 트랜잭션 내에서 처리
     * - 연관된 트래킹 데이터도 함께 삭제
     * 
     * 주의: 실제 참가자 데이터와 구분하여 테스트 데이터만 삭제
     */
    @Operation(
        summary = "테스트 참가자 삭제",
        description = "특정 이벤트의 모든 테스트 참가자를 삭제합니다."
    )
    @DeleteMapping("/test/event/{eventId}")
    fun deleteTestParticipants(
        @Parameter(description = "이벤트 ID")
        @PathVariable eventId: Long
    ): ResponseEntity<Any> {
        return try {
            // 서비스에서 테스트 참가자 일괄 삭제
            val deletedCount = participantService.deleteTestParticipants(eventId)
            
            // 삭제 결과 응답 데이터 구성
            val result = mapOf(
                "eventId" to eventId,
                "deletedCount" to deletedCount,
                "message" to "테스트 참가자가 성공적으로 삭제되었습니다"
            )
            
            // 삭제 성공 로그
            logger.info("테스트 참가자 삭제 성공: eventId=$eventId, 삭제 수=$deletedCount")
            
            // 200 OK로 삭제 결과 반환
            ResponseEntity.ok(ApiResponse(data = result))
        } catch (e: IllegalArgumentException) {
            // 잘못된 이벤트 ID
            logger.warn("테스트 참가자 삭제 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("테스트 참가자 삭제 실패 - 서버 오류: eventId=$eventId", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    /**
     * 이벤트 내에서 참가자를 이름이나 배번으로 검색합니다.
     * 커서 기반 페이지네이션을 사용하여 일관된 결과를 보장합니다.
     * 
     * @param eventId 검색할 이벤트 ID
     * @param search 검색어 (이름 또는 배번, 부분 일치 지원)
     * @param size 페이지 크기 (1-100 사이)
     * @param cursor 다음 페이지를 위한 커서 (배번_타임스탬프 형태)
     * @return 검색된 참가자 목록과 다음 페이지 커서
     * 
     * 커서 페이지네이션:
     * - 오프셋 방식보다 일관성 있는 결과 제공
     * - 실시간으로 데이터가 추가/삭제되어도 중복이나 누락 없음
     * - 커서 형태: "배번_yyyyMMddHHmmss"
     * - 배번과 생성시간을 조합하여 고유성 보장
     * 
     * 검색 기능:
     * - 이름: 부분 일치, 대소문자 무시
     * - 배번: 정확한 일치 또는 부분 일치
     * - 검색어가 없으면 전체 목록 반환
     */
    @Operation(
        summary = "참가자 검색",
        description = "이벤트별 참가자를 이름 또는 배번으로 검색합니다. 커서 기반 페이징을 사용합니다."
    )
    @GetMapping("/search")
    fun searchParticipants(
        @Parameter(description = "이벤트 ID")
        @RequestParam eventId: Long,
        @Parameter(description = "검색어 (이름 또는 배번)")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "페이지 크기 (1-100)")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "다음 페이지 커서")
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<Any> {
        return try {
            // 요청 파라미터들을 DTO로 구성
            val request = ParticipantSearchRequestDto(
                eventId = eventId,
                search = search,
                size = size,
                cursor = cursor
            )
            
            // 서비스에서 커서 기반 검색 수행
            val response = participantService.searchParticipants(request)
            
            // 검색 성공 로그 (검색어와 결과 수 포함)
            logger.info("참가자 검색 성공: eventId=$eventId, 검색어='$search', 결과=${response.participants.size}건")
            
            // 200 OK로 검색 결과 반환
            ResponseEntity.ok(ApiResponse(data = response))
        } catch (e: IllegalArgumentException) {
            // 잘못된 요청 (잘못된 커서, 페이지 크기 등)
            logger.warn("참가자 검색 실패 - 잘못된 요청: ${e.message}")
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.BAD_REQUEST,
                code = ErrorCode.INVALID_INPUT_VALUE,
                message = e.message ?: "잘못된 요청입니다"
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: Exception) {
            // 서버 내부 오류
            logger.error("참가자 검색 실패 - 서버 오류: eventId=$eventId, 검색어='$search'", e)
            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "서버 내부 오류가 발생했습니다"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }
} 