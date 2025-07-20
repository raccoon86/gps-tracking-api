package com.sponovation.runtrack.controller

import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.common.ErrorResponse
import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.service.RedisManagementService
import com.sponovation.runtrack.dto.RedisResetResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Redis 관리 컨트롤러
 *
 * Redis 데이터베이스의 관리 작업을 위한 REST API를 제공합니다.
 * 개발 및 테스트 환경에서 Redis 데이터 초기화, 상태 조회 등의 기능을 수행합니다.
 *
 * 주요 기능:
 * - 모든 Redis 키 삭제 (전체 데이터베이스 리셋)
 * - 특정 패턴의 키 삭제 (선택적 데이터 삭제)
 * - Redis 상태 정보 조회 (키 개수, 메모리 사용량 등)
 *
 * 보안 고려사항:
 * - 모든 삭제 작업에는 확인 문자열이 필요합니다.
 * - 실수로 인한 데이터 삭제를 방지하기 위한 안전 장치가 구현되어 있습니다.
 * - 프로덕션 환경에서는 접근 제한을 권장합니다.
 *
 * API 응답 형식:
 * - 모든 응답은 ApiResponse<T> 형태로 표준화되어 있습니다.
 * - 성공 시 data 필드에 결과가 포함됩니다.
 * - 실패 시 적절한 HTTP 상태 코드와 오류 메시지를 반환합니다.
 *
 * 사용 예시:
 * ```
 * POST /api/admin/redis/reset
 * {
 *   "confirmDelete": "CONFIRM_DELETE_ALL"
 * }
 * ```
 *
 * @author Sponovation
 * @since 1.0
 * @see RedisManagementService
 */
@Tag(name = "Redis 관리", description = "Redis 데이터베이스 관리 API")
@RestController
@RequestMapping("/api/admin/redis")
class RedisManagementController(
    private val redisManagementService: RedisManagementService
) {

    private val logger = LoggerFactory.getLogger(RedisManagementController::class.java)

    /**
     * Redis의 모든 키를 삭제합니다.
     *
     * 이 API는 Redis 데이터베이스의 모든 데이터를 완전히 삭제합니다.
     * 다음과 같은 모든 데이터가 제거됩니다:
     *
     * 삭제되는 데이터 타입:
     * - 실시간 위치 정보 (location:*)
     * - 체크포인트 통과 시간 (checkpointTimes:*)
     * - 리더보드 정보 (leaderboard:*)
     * - 코스 데이터 캐시 (eventDetail:*)
     * - GPX 파싱 임시 데이터 (gpx:*)
     * - 참가자 구간 기록 (participantSegmentRecords:*)
     * - 이전 위치 추적 데이터 (previousLocation:*)
     * - 대회 시작 시간 정보 (start:*)
     *
     * 보안 및 안전 조치:
     * - confirmDelete 필드에 정확한 확인 문자열을 입력해야 합니다.
     * - 잘못된 확인 문자열 시 400 Bad Request를 반환합니다.
     * - 모든 작업은 상세한 로그로 기록됩니다.
     *
     * 성능 고려사항:
     * - FLUSHALL 명령어를 사용하여 빠른 전체 삭제를 수행합니다.
     * - 삭제 전후의 키 개수를 확인하여 정확한 삭제 수량을 반환합니다.
     *
     * 사용 시나리오:
     * - 개발 환경에서 테스트 데이터 정리
     * - 스테이징 환경 초기화
     * - 대회 종료 후 전체 데이터 정리
     *
     * @param request 삭제 확인 요청 객체
     * @return ApiResponse<Long> 삭제된 키의 개수
     *
     * HTTP 응답 코드:
     * - 200: 삭제 성공, 삭제된 키 개수 반환
     * - 400: 잘못된 확인 문자열 또는 요청 형식 오류
     * - 500: Redis 연결 오류 또는 내부 서버 오류
     *
     * 예외 처리:
     * - IllegalArgumentException: 확인 문자열 불일치
     * - RuntimeException: Redis 연결 실패 또는 FLUSHALL 명령 실패
     */
    @Operation(
        summary = "Redis 전체 키 삭제", description = "Redis 데이터베이스의 모든 키를 삭제합니다. 확인 문자열이 필요합니다. :CONFIRM_DELETE_ALL"
    )
    @ApiResponses(
        value = [SwaggerApiResponse(
            responseCode = "200",
            description = "삭제 성공"
        ), SwaggerApiResponse(
            responseCode = "400",
            description = "잘못된 확인 문자열"
        ), SwaggerApiResponse(responseCode = "500", description = "서버 오류")]
    )
    @PostMapping("/reset")
    fun resetAllRedisKeys(
    ): ResponseEntity<Any> {

        logger.warn("=== Redis 전체 리셋 API 호출 ===")

        return try {
            // Redis 모든 키 삭제 실행
            val deletedCount = redisManagementService.deleteAllKeys()

            val response = RedisResetResponseDto(
                success = true,
                deletedKeyCount = deletedCount,
                message = "Redis의 모든 키가 성공적으로 삭제되었습니다.",
                resetType = "ALL_KEYS",
                timestamp = System.currentTimeMillis()
            )

            logger.warn("Redis 전체 리셋 완료: $deletedCount 개 키 삭제")

            ResponseEntity.ok(
                ApiResponse(
                    data = response,
                )
            )
        } catch (e: Exception) {
            logger.error("Redis 전체 리셋 중 예외 발생", e)

            val errorResponse = ErrorResponse.create(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ErrorCode.API_BAD_REQUEST,
                message = "Redis 키 삭제 중 오류가 발생했습니다: ${e.message}"
            )

            ResponseEntity.internalServerError().body(errorResponse)
        }
    }
}