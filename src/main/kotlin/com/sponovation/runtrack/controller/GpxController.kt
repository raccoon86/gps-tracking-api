package com.sponovation.runtrack.controller

import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.service.GpxService
import com.sponovation.runtrack.service.CourseDataService
import com.sponovation.runtrack.service.LeaderboardService
import com.sponovation.runtrack.service.GpxParsingRedisService
import com.sponovation.runtrack.service.EventService
import com.sponovation.runtrack.repository.EventDetailRepository
import java.util.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import com.sponovation.runtrack.enums.ErrorCode
import com.sponovation.runtrack.common.ErrorResponse
import com.sponovation.runtrack.service.EventDetailService

/**
 * GPX 경로 처리 및 위치 보정 API 컨트롤러
 *
 * 이 컨트롤러는 GPX 파일 기반의 경로 처리와 GPS 위치 보정 기능을 제공합니다.
 * 주요 기능으로는 실시간 GPS 좌표를 사전 정의된 GPX 경로에 매칭하여
 * 보정된 위치를 반환하는 서비스를 포함합니다.
 *
 * 사용 사례:
 * - 마라톤 대회에서 참가자의 실시간 위치 추적
 * - GPS 신호 오차 보정을 통한 정확한 경로 매칭
 * - 체크포인트 도달 여부 판정
 * - 경로 이탈 감지 및 알림
 *
 * @see GpxService GPX 데이터 처리 서비스
 * @see CorrectLocationRequestDto 위치 보정 요청 DTO
 * @see CorrectLocationResponseDto 위치 보정 응답 DTO
 */
@RestController
@RequestMapping("/api/v1/gpx")
@Tag(name = "GPX 경로 처리", description = "GPX 파일 기반 경로 처리 및 GPS 위치 보정 API")
class GpxController(
    private val gpxService: GpxService,
    private val courseDataService: CourseDataService,
    private val leaderboardService: LeaderboardService,
    private val gpxParsingRedisService: GpxParsingRedisService,
    private val eventDetailRepository: EventDetailRepository,
    private val eventService: EventService,
    private val eventDetailService: EventDetailService,
) {

    private val logger = LoggerFactory.getLogger(GpxController::class.java)

    /**
     * GPS 위치를 GPX 경로에 매칭하여 보정된 위치를 반환합니다
     *
     * 이 엔드포인트는 실시간으로 수신된 GPS 좌표를 분석하여 다음과 같은 처리를 수행합니다:
     *
     * 처리 과정:
     * 1. 대회 및 코스 유효성 확인
     * 2. Redis에서 GPX 파싱 데이터 및 웨이포인트(CP) 조회
     * 3. GPX 데이터 로드 및 보간 (Redis에 데이터가 없는 경우)
     *    - EventDetail 테이블에서 GPX 파일 URL 조회
     *    - S3에서 GPX 파일 다운로드
     *    - 웨이포인트 간 100미터 당 보간 포인트 생성
     *    - 각 CP에 cpId와 cpIndex 부여
     *    - Redis에 gpx:{eventId}:{eventDetailId} 키로 저장
     * 4. GPS 위치 보정 처리 (칼만 필터, 맵 매칭, 체크포인트 도달 여부 확인)
     * 5. 체크포인트 통과 시간 기록 및 리더보드 업데이트
     *
     * 보정 알고리즘:
     * - Weighted Snap-to-Road: 거리와 방향을 고려한 가중치 기반 경로 매칭
     * - 산악 지형 특화: 고도 변화와 방향 급변에 최적화된 알고리즘
     * - 실시간 처리: 100ms 이내의 빠른 응답 시간 보장
     *
     * @param request GPS 위치 보정 요청 데이터
     *                - eventDetailId: 대회 상세 ID (필수)
     *                - gpsData: GPS 좌표 정보 (위도, 경도, 고도, 정확도, 속도, 방향 등)
     * @return 보정된 위치 정보 및 경로 매칭 결과
     *         - correctedLocations: 칼만 필터 및 맵 매칭을 통해 보정된 위치 리스트
     *         - matchingResults: 각 GPS 포인트의 경로 매칭 결과 (성공/실패, 거리 등)
     *         - checkpointReaches: 새로 도달한 체크포인트 정보
     *         - routeProgress: 전체 경로에서의 진행률 (0.0 ~ 1.0)
     *
     * HTTP 상태 코드:
     * - 200 OK: 보정 성공
     * - 400 Bad Request: 잘못된 요청 데이터 (필수 파라미터 누락, 잘못된 형식 등)
     * - 404 Not Found: 해당 대회 정보, GPX 파일, 또는 GPX 파싱 데이터를 찾을 수 없음
     * - 500 Internal Server Error: 서버 내부 오류 (보정 로직 오류, 데이터베이스 오류 등)
     *
     */
    @PostMapping("/correct-location")
    @Operation(
        summary = "GPS 위치 보정",
        description = "실시간 GPS 좌표를 GPX 경로에 매칭하여 보정된 위치와 경로 진행 상황을 반환합니다."
    )
    fun correctLocation(
        @Parameter(description = "GPS 위치 보정 요청 데이터", required = true)
        @Valid @RequestBody request: CorrectLocationRequestDto
    ): ResponseEntity<Any> {
        val validationResult = gpxService.validateEventAndCourse(request.userId, request.eventId, request.eventDetailId)
        if (!validationResult.isValid) {
            logger.warn("대회 및 코스 유효성 검사 실패: ${validationResult.errorMessage}")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.create(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_VALUE, validationResult))
        }
        return try {
            // 1. GPX 데이터 및 웨이포인트(CP) 조회 (Redis)
            val gpxParsingData = gpxParsingRedisService.getGpxParsingData(
                eventId = request.eventId,
                eventDetailId = request.eventDetailId
            )

            if (!gpxParsingData.success || gpxParsingData.points.isEmpty()) {
                logger.warn("GPX 파싱 데이터 조회 실패: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}")

                // 2. GPX 데이터 로드 및 보간 - Redis에 데이터가 없으면 S3에서 로드
                try {
                    logger.info("S3에서 GPX 파일 로드 및 파싱 시작: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}")

                    // EventDetail 조회 (eventDetailId를 통해)
                    val course = eventDetailService.findCourseByEventDetailId(request.eventDetailId)
                    if (course == null) {
                        logger.warn("EventDetail 정보를 찾을 수 없음: eventDetailId=${request.eventDetailId}")
                        throw NoSuchElementException("EventDetail 정보를 찾을 수 없습니다: eventDetailId=${request.eventDetailId}")
                    }

                    // S3 GPX 파일 URL 추출
                    val gpxFileUrl = course.gpxFile
                    logger.info("S3 GPX 파일 URL: $gpxFileUrl")

                    // S3에서 GPX 파일 다운로드 및 파싱
                    val gpxFileBytes = gpxService.downloadGpxFromS3(gpxFileUrl)

                    // ByteArray를 MultipartFile로 변환
                    val multipartFile = gpxService.createMultipartFileFromBytes(gpxFileBytes, "eventDetail.gpx")

                    // GPX 파싱 및 Redis 저장
                    val parseResult = gpxParsingRedisService.parseAndSaveGpxFile(
                        file = multipartFile,
                        eventId = request.eventId,
                        eventDetailId = request.eventDetailId,
                        checkpointDistanceInterval = 0.0, // 모든 트랙포인트를 체크포인트로
                        interpolationInterval = 100.0 // 100m 간격 보간
                    )

                    if (!parseResult.success) {
                        logger.error("GPX 파싱 및 Redis 저장 실패: ${parseResult.message}")
                        throw RuntimeException("GPX 파싱 및 Redis 저장 실패: ${parseResult.message}")
                    }

                    logger.info("GPX 파싱 및 Redis 저장 성공: 총 ${parseResult.totalPoints}개 포인트, 체크포인트 ${parseResult.checkpointCount}개")

                    // 파싱 완료 후 다시 조회
                    val retryGpxParsingData = gpxParsingRedisService.getGpxParsingData(
                        eventId = request.eventId,
                        eventDetailId = request.eventDetailId
                    )

                    if (!retryGpxParsingData.success || retryGpxParsingData.points.isEmpty()) {
                        logger.error("GPX 파싱 완료 후 재조회 실패: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}")
                        throw RuntimeException("GPX 파싱 완료 후 데이터 재조회에 실패했습니다")
                    }

                    // 새로 파싱된 데이터 사용
                    val checkpoints =
                        retryGpxParsingData.points.filter { it.type in listOf("start", "checkpoint", "finish") }
                    logger.info("새로 파싱된 GPX 데이터 사용: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}")
                    logger.info("- 총 포인트: ${retryGpxParsingData.points.size}개")
                    logger.info("- 체크포인트: ${checkpoints.size}개 (시작점/중간점/종료점 포함)")

                    checkpoints.forEach { cp ->
                        logger.debug(
                            "체크포인트 정보: cpId=${cp.cpId}, cpIndex=${cp.cpIndex}, type=${cp.type}, " +
                                    "위치=(${cp.latitude}, ${cp.longitude})"
                        )
                    }

                } catch (e: Exception) {
                    logger.error(
                        "GPX 파일 S3 로드 및 파싱 중 오류 발생: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}",
                        e
                    )
                    throw RuntimeException("GPX 파일 S3 로드 및 파싱 중 오류 발생: ${e.message}", e)
                }

            } else {
                // 웨이포인트 및 체크포인트 정보 로깅
                val checkpoints = gpxParsingData.points.filter { it.type in listOf("start", "checkpoint", "finish") }
                logger.info("GPX 파싱 데이터 조회 성공: eventId=${request.eventId}, eventDetailId=${request.eventDetailId}")
                logger.info("- 총 포인트: ${gpxParsingData.points.size}개")
                logger.info("- 체크포인트: ${checkpoints.size}개 (시작점/중간점/종료점 포함)")

                checkpoints.forEach { cp ->
                    logger.debug(
                        "체크포인트 정보: cpId=${cp.cpId}, cpIndex=${cp.cpIndex}, type=${cp.type}, " +
                                "위치=(${cp.latitude}, ${cp.longitude})"
                    )
                }
            }

            // 3. 위치 보정 처리
            val response = gpxService.correctLocation(request)

            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: IllegalArgumentException) {
            logger.warn("GPS 위치 보정 실패 - 잘못된 요청: ${e.message}")
            throw e // GlobalExceptionHandler가 처리하도록 위임
        } catch (e: NoSuchElementException) {
            logger.warn("GPS 위치 보정 실패 - 대회 정보 없음: eventDetailId=${request.eventDetailId}")
            throw e // GlobalExceptionHandler가 처리하도록 위임
        } catch (e: Exception) {
            logger.error("GPS 위치 보정 실패 - 서버 오류: eventDetailId=${request.eventDetailId}", e)
            throw e // GlobalExceptionHandler가 처리하도록 위임
        }
    }

    /**
     * 테스트용 Event 생성
     *
     * 테스트 환경에서 Event 엔티티를 생성합니다.
     *
     * @param request Event 생성 요청 DTO
     * @return 생성된 Event 정보
     */
    @PostMapping("/create-test-event")
    @Operation(
        summary = "테스트용 Event 생성",
        description = "테스트 환경에서 Event 엔티티를 생성합니다."
    )
    fun createTestEvent(
        @Parameter(description = "Event 생성 요청 데이터", required = true)
        @Valid @RequestBody request: CreateTestEventRequestDto
    ): ResponseEntity<Any> {

        return try {
            // 서비스 계층을 통해 Event 생성
            val response = eventService.createTestEvent(request)
            
            logger.info("테스트 Event 생성 성공: eventId=${response.eventId}, name=${response.eventName}")
            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: IllegalArgumentException) {
            logger.warn("테스트 Event 생성 실패 - 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                ErrorResponse.create(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_INPUT_VALUE,
                    e.message ?: ErrorCode.INVALID_INPUT_VALUE.message
                )
            )
        } catch (e: Exception) {
            logger.error("테스트 Event 생성 실패: name=${request.name}", e)
            ResponseEntity.internalServerError().body(
                ErrorResponse.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.API_BAD_REQUEST,
                    e.message ?: ErrorCode.API_BAD_REQUEST.message
                )
            )
        }
    }

    /**
     * 테스트용 EventDetail 생성
     *
     * 테스트 환경에서 EventDetail 엔티티를 생성합니다.
     *
     * @param request EventDetail 생성 요청 DTO
     * @return 생성된 EventDetail 정보
     */
    @PostMapping("/create-test-course")
    @Operation(
        summary = "테스트용 EventDetail 생성",
        description = "테스트 환경에서 EventDetail 엔티티를 생성합니다."
    )
    fun createTestCourse(
        @Parameter(description = "EventDetail 생성 요청 데이터", required = true)
        @Valid @RequestBody request: CreateTestCourseRequestDto
    ): ResponseEntity<Any> {

        return try {
            // 서비스 계층을 통해 EventDetail 생성
            val response = eventDetailService.createTestCourse(request)
            
            logger.info("테스트 EventDetail 생성 성공: eventDetailId=${response.eventDetailId}, course=${response.course}")
            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: IllegalArgumentException) {
            logger.warn("테스트 EventDetail 생성 실패 - 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                ErrorResponse.create(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_INPUT_VALUE,
                    e.message ?: ErrorCode.INVALID_INPUT_VALUE.message
                )
            )
        } catch (e: Exception) {
            logger.error("테스트 EventDetail 생성 실패: eventId=${request.eventId}, courseName=${request.courseName}", e)
            ResponseEntity.internalServerError().body(
                ErrorResponse.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.API_BAD_REQUEST,
                    e.message ?: ErrorCode.API_BAD_REQUEST.message
                )
            )
        }
    }

    /**
     * 테스트용 유저 5명 생성
     *
     * 테스트 환경에서 GPS 시뮬레이션을 위한 5명의 테스트 유저를 생성합니다.
     *
     * @param eventId Event ID
     * @param eventDetailId Event Detail ID (EventDetail ID)
     * @return 생성된 유저 정보 목록
     */
    @PostMapping("/create-test-users")
    @Operation(
        summary = "테스트용 유저 5명 생성",
        description = "GPS 시뮬레이션을 위한 5명의 테스트 유저를 생성하고 리더보드에 등록합니다."
    )
    fun createTestUsers(
        @Parameter(description = "Event ID", required = true)
        @RequestParam("eventId") eventId: Long,
        @Parameter(description = "Event Detail ID", required = true)
        @RequestParam("eventDetailId") eventDetailId: Long
    ): ResponseEntity<Any> {

        return try {
            logger.info("테스트 유저 5명 생성 시작: eventId=$eventId, eventDetailId=$eventDetailId")

            val testUsers = listOf(
                mapOf("userId" to 1L, "name" to "김러너", "bibNumber" to "A001"),
                mapOf("userId" to 2L, "name" to "이스피드", "bibNumber" to "A002"),
                mapOf("userId" to 3L, "name" to "박마라톤", "bibNumber" to "A003"),
                mapOf("userId" to 4L, "name" to "최러닝", "bibNumber" to "A004"),
                mapOf("userId" to 5L, "name" to "정트랙", "bibNumber" to "A005")
            )

            // 각 유저를 리더보드에 초기 등록 (START 지점으로 설정)
            testUsers.forEach { user ->
                val userId = user["userId"] as Long
                try {
                    leaderboardService.updateLeaderboard(
                        userId = userId,
                        eventId = eventId,
                        eventDetailId = eventDetailId,
                        checkpointOrder = 0, // START 지점
                        cumulativeTime = 0L // 시작 시간
                    )
                    logger.info("리더보드 초기 등록: userId=$userId")
                } catch (e: Exception) {
                    logger.warn("리더보드 초기 등록 실패: userId=$userId", e)
                }
            }

            val response = mapOf(
                "success" to true,
                "message" to "테스트 유저 5명 생성 및 리더보드 등록 완료",
                "eventId" to eventId,
                "eventDetailId" to eventDetailId,
                "users" to testUsers,
                "totalUsers" to testUsers.size
            )

            logger.info("테스트 유저 5명 생성 완료: eventId=$eventId, eventDetailId=$eventDetailId")
            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: Exception) {
            logger.error("테스트 유저 생성 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 테스트용 GPX 파일 업로드
     *
     * 이 엔드포인트는 테스트 환경에서 GPX 파일을 업로드하고 코스 데이터를 생성합니다.
     *
     * 처리 과정:
     * 1. GPX 파일을 한 번만 파싱하고 보간 포인트를 생성하여 DB와 Redis에 저장
     *
     * @param file 업로드할 GPX 파일
     * @param request 경로 정보가 포함된 JSON 데이터
     * @return 업로드 결과 및 코스 ID
     */
    @PostMapping("/upload-gpx", consumes = ["multipart/form-data"])
    @Operation(
        summary = "GPX 파일 업로드",
        description = "테스트용으로 GPX 파일을 업로드하고 100미터 간격 보간 포인트를 생성하여 Redis에 저장합니다."
    )
    fun uploadGpx(
        @Parameter(description = "업로드할 GPX 파일", required = true)
        @RequestPart("file") file: MultipartFile,
        @Parameter(description = "이벤트 ID", required = true)
        @RequestParam("eventId") eventId: Long,
        @Parameter(description = "이벤트 상세 ID", required = true)
        @RequestParam("eventDetailId") eventDetailId: Long,
        @Parameter(description = "경로 이름", required = true)
        @RequestParam("routeName") routeName: String,
    ): ResponseEntity<Any> {

        return try {
            logger.info("GPX 파일 업로드 시작: 파일명=${file.originalFilename}, eventId=$eventId, eventDetailId=$eventDetailId")

            // GPX 파일 유효성 검증
            if (file.isEmpty) {
                logger.warn("빈 GPX 파일 업로드 시도")
                return ResponseEntity.badRequest().build()
            }

            // 1. GPX 파일을 파싱하고 보간 포인트를 생성하여 Redis에 저장 (모든 트랙포인트를 체크포인트로)
            val parseResult = gpxParsingRedisService.parseAndSaveGpxFile(
                file = file,
                eventId = eventId,
                eventDetailId = eventDetailId,
                checkpointDistanceInterval = 100.0, // 모든 트랙포인트를 체크포인트로
                interpolationInterval = 100.0 // 100m 간격 보간
            )

            if (!parseResult.success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }

            // 2. 응답 생성
            val response = GpxUploadResponseDto(
                courseId = "course_${eventId}_${eventDetailId}",
                routeId = 0L, // GpxRoute 엔티티 삭제로 기본값 사용
                routeName = routeName, // 파라미터에서 받은 이름 사용
                totalDistance = parseResult.totalDistance,
                totalPoints = parseResult.totalPoints,
                createdAt = java.time.LocalDateTime.now().toString()
            )

            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: IllegalArgumentException) {
            logger.warn("GPX 파일 업로드 실패 - 잘못된 요청: ${e.message}")
            return ResponseEntity.badRequest().body(
                ErrorResponse.create(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_INPUT_VALUE,
                    e.message ?: ErrorCode.INVALID_INPUT_VALUE.message
                )
            )
        } catch (e: Exception) {
            logger.error("GPX 파일 업로드 실패 - 서버 오류: ${e.message}", e)
            return ResponseEntity.internalServerError().body(
                ErrorResponse.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.API_BAD_REQUEST,
                    e.message ?: ErrorCode.API_BAD_REQUEST.message
                )
            )
        }
    }

    /**
     * 코스 ID로 코스 데이터 조회
     *
     * Redis에 저장된 코스 데이터를 코스 ID로 조회합니다.
     *
     * @param courseId 코스 ID
     * @return 코스 데이터 (100미터 간격 보간 포인트 포함)
     */
    @GetMapping("/course/id/{courseId}")
    @Operation(
        summary = "코스 ID로 코스 데이터 조회",
        description = "Redis에 저장된 코스 데이터를 코스 ID로 조회합니다."
    )
    fun getCourseDataByCourseId(
        @Parameter(description = "코스 ID", required = true)
        @PathVariable courseId: String
    ): ResponseEntity<Any> {

        return try {
            logger.info("코스 데이터 조회 시작: courseId=$courseId")

            val courseData = courseDataService.getCourseData(courseId)

            if (courseData != null) {
                val response = CourseDataResponseDto(
                    courseId = courseData.courseId,
                    eventId = courseData.eventId,
                    fileName = courseData.fileName,
                    totalDistance = courseData.totalDistance,
                    totalPoints = courseData.totalPoints,
                    interpolatedPoints = courseData.interpolatedPoints.map { point ->
                        InterpolatedPointDto(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            elevation = point.elevation,
                            distanceFromStart = point.distanceFromStart
                        )
                    },
                    createdAt = courseData.createdAt
                )

                logger.info("코스 데이터 조회 성공: courseId=${courseData.courseId}, 포인트 수=${courseData.totalPoints}")
                ResponseEntity.ok(ApiResponse(data = response))
            } else {
                logger.warn("코스 데이터를 찾을 수 없음: courseId=$courseId")
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("코스 데이터 조회 실패: courseId=$courseId", e)
            return ResponseEntity.internalServerError().body(
                ErrorResponse.create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.API_BAD_REQUEST,
                    e.message ?: ErrorCode.API_BAD_REQUEST.message
                )
            )
        }
    }
}

