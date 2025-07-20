package com.sponovation.runtrack.controller

import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.common.ApiResponse
import com.sponovation.runtrack.service.GpxService
import com.sponovation.runtrack.service.CourseDataService
import com.sponovation.runtrack.service.CheckpointTimesService
import com.sponovation.runtrack.service.LeaderboardService
import com.sponovation.runtrack.service.GpxParsingRedisService
import com.sponovation.runtrack.service.EventService
import com.sponovation.runtrack.repository.EventDetailRepository
import com.sponovation.runtrack.domain.EventDetail
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
import com.sponovation.runtrack.repository.EventRepository
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
    private val eventRepository: EventRepository,
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
        val validationResult = validateEventAndCourse(request.userId, request.eventId, request.eventDetailId)
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
                    val course = findCourseByEventDetailId(request.eventDetailId)
                    if (course == null) {
                        logger.warn("EventDetail 정보를 찾을 수 없음: eventDetailId=${request.eventDetailId}")
                        throw NoSuchElementException("EventDetail 정보를 찾을 수 없습니다: eventDetailId=${request.eventDetailId}")
                    }

                    // S3 GPX 파일 URL 추출
                    val gpxFileUrl = course.gpxFile
                    logger.info("S3 GPX 파일 URL: $gpxFileUrl")

                    // S3에서 GPX 파일 다운로드 및 파싱
                    val gpxFileBytes = downloadGpxFromS3(gpxFileUrl)

                    // ByteArray를 MultipartFile로 변환
                    val multipartFile = createMultipartFileFromBytes(gpxFileBytes, "eventDetail.gpx")

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
     * 대회 및 코스 유효성 확인
     *
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID (EventDetail ID)
     * @return 유효성 검사 결과
     */
    private fun validateEventAndCourse(
        userId: Long,
        eventId: Long,
        eventDetailId: Long
    ): ErrorResponse.ValidationResult {
        return try {
            logger.info("대회 및 코스 유효성 검사 시작: userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId")

            // 1. Event 정보 조회 (eventId 사용)
            val event = eventRepository.findById(eventId)
                .orElse(null)

            if (event == null) {
                logger.warn("Event를 찾을 수 없음: eventId=$eventId")
                return ErrorResponse.ValidationResult(false, "EVENT_NOT_FOUND", "해당 이벤트를 찾을 수 없습니다.")
            }

            // 2. EventDetail 정보 조회 (eventDetailId를 courseId로 사용)
            val course = eventDetailRepository.findById(eventDetailId).orElse(null)

            if (course == null) {
                logger.warn("Course를 찾을 수 없음: eventDetailId=$eventDetailId")
                return ErrorResponse.ValidationResult(false, "COURSE_NOT_FOUND", "해당 코스를 찾을 수 없습니다.")
            }

            // 3. GPX 파싱 데이터 존재 여부 확인
            val gpxParsingData = gpxParsingRedisService.getGpxParsingData(
                eventId = eventId,
                eventDetailId = eventDetailId
            )

            if (!gpxParsingData.success || gpxParsingData.points.isEmpty()) {
                logger.warn("GPX 파싱 데이터를 찾을 수 없음: eventId=$eventId, eventDetailId=$eventDetailId")
                return ErrorResponse.ValidationResult(false, "GPX_DATA_NOT_FOUND", "GPX 파싱 데이터를 찾을 수 없습니다.")
            }

            logger.info("대회 및 코스 유효성 검사 통과: userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId")
            logger.info("- Event: ${event.name}")
            logger.info("- EventDetail: ${course.course}")
            logger.info("- GPX 포인트 수: ${gpxParsingData.points.size}")

            ErrorResponse.ValidationResult(true, null, null)

        } catch (e: Exception) {
            logger.error("대회 및 코스 유효성 검사 중 오류 발생: userId=$userId, eventId=$eventId, eventDetailId=$eventDetailId", e)
            ErrorResponse.ValidationResult(false, "VALIDATION_ERROR", "검증 중 오류가 발생했습니다.")
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

    /**
     * eventDetailId를 통해 EventDetail 조회
     *
     * @param eventDetailId 이벤트 상세 ID
     * @return EventDetail 엔티티 또는 null
     */
    private fun findCourseByEventDetailId(eventDetailId: Long): EventDetail? {
        return try {
            // eventDetailId를 courseId로 직접 사용하여 조회
            val course = eventDetailRepository.findById(eventDetailId)
            if (course.isPresent) {
                course.get()
            } else {
                logger.warn("EventDetail 조회 실패: courseId=$eventDetailId")
                null
            }
        } catch (e: Exception) {
            logger.error("EventDetail 조회 중 오류 발생: eventDetailId=$eventDetailId", e)
            null
        }
    }

    /**
     * S3에서 GPX 파일 다운로드
     *
     * @param gpxFileUrl S3 GPX 파일 URL
     * @return GPX 파일 바이트 배열
     */
    private fun downloadGpxFromS3(gpxFileUrl: String): ByteArray {
        return try {
            logger.info("S3 GPX 파일 다운로드 시작: $gpxFileUrl")

            // URL에서 S3 버킷과 키 정보 추출
            val url = java.net.URL(gpxFileUrl)
            val urlConnection = url.openConnection()

            // 파일 다운로드
            val inputStream = urlConnection.getInputStream()
            val gpxBytes = inputStream.readBytes()
            inputStream.close()

            logger.info("S3 GPX 파일 다운로드 완료: $gpxFileUrl, 파일 크기=${gpxBytes.size} bytes")
            gpxBytes

        } catch (e: Exception) {
            logger.error("S3 GPX 파일 다운로드 실패: $gpxFileUrl", e)
            throw RuntimeException("S3 GPX 파일 다운로드 실패: ${e.message}", e)
        }
    }

    /**
     * ByteArray를 MultipartFile로 변환
     *
     * @param bytes 파일 바이트 배열
     * @param fileName 파일명
     * @return MultipartFile 객체
     */
    private fun createMultipartFileFromBytes(bytes: ByteArray, fileName: String): MultipartFile {
        return object : MultipartFile {
            override fun getName(): String = "file"
            override fun getOriginalFilename(): String = fileName
            override fun getContentType(): String = "application/gpx+xml"
            override fun isEmpty(): Boolean = bytes.isEmpty()
            override fun getSize(): Long = bytes.size.toLong()
            override fun getBytes(): ByteArray = bytes
            override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream(bytes)
            override fun transferTo(dest: java.io.File) {
                dest.writeBytes(bytes)
            }
        }
    }

    /**
     * 모바일 테스트용 Fake GPS 데이터 생성
     *
     * 5명의 유저가 GPX 경로를 따라 이동하는 GPS 데이터를 생성합니다.
     */
    @GetMapping("/generate-fake-gps-data")
    @Operation(
        summary = "모바일 테스트용 Fake GPS 데이터 생성",
        description = "5명의 유저가 GPX 경로를 따라 이동하는 GPS 데이터를 생성합니다."
    )
    fun generateFakeGpsData(
        @Parameter(description = "이벤트 ID", required = false)
        @RequestParam(defaultValue = "1") eventId: Long,
        @Parameter(description = "이벤트 상세 ID", required = false)
        @RequestParam(defaultValue = "100") eventDetailId: Long,
        @Parameter(description = "GPS 데이터 간격 (초)", required = false)
        @RequestParam(defaultValue = "10") intervalSeconds: Int,
        @Parameter(description = "GPS 오차 범위 (미터)", required = false)
        @RequestParam(defaultValue = "15") errorRangeMeters: Double
    ): ResponseEntity<Any> {

        return try {
            logger.info("Fake GPS 데이터 생성 시작: eventId=$eventId, eventDetailId=$eventDetailId")

            // GPX 경로 포인트들 (기존 test_route.gpx 데이터)
            val gpxPoints = listOf(
                Pair(37.5413553485092, 127.115719020367),
                Pair(37.5390881874808, 127.114029228687),
                Pair(37.5379482005428, 127.113452553749),
                Pair(37.5353682776934, 127.11048334837),
                Pair(37.5351258071487, 127.110266089439),
                Pair(37.5344154064531, 127.109783291817),
                Pair(37.5337836971563, 127.109482884407),
                Pair(37.5320799696497, 127.108764052391),
                Pair(37.5317162475003, 127.108696997166),
                Pair(37.5315503385624, 127.108699679375),
                Pair(37.5301273352067, 127.107908427715),
                Pair(37.5301188268838, 127.107884287834),
                Pair(37.5266260783458, 127.1042740345),
                Pair(37.5258773070672, 127.104005813599),
                Pair(37.5255709893779, 127.103651762009),
                Pair(37.5252731793075, 127.102814912796),
                Pair(37.5235713846696, 127.101398706436)
            )

            // 5명의 테스트 유저 정보
            val testUsers = listOf(
                mapOf("userId" to 1, "name" to "김러너", "bibNumber" to "A001", "speedFactor" to 1.0, "delayMinutes" to 0),
                mapOf(
                    "userId" to 2,
                    "name" to "이스피드",
                    "bibNumber" to "A002",
                    "speedFactor" to 1.2,
                    "delayMinutes" to 2
                ),
                mapOf(
                    "userId" to 3,
                    "name" to "박마라톤",
                    "bibNumber" to "A003",
                    "speedFactor" to 0.9,
                    "delayMinutes" to 1
                ),
                mapOf("userId" to 4, "name" to "최러닝", "bibNumber" to "A004", "speedFactor" to 1.1, "delayMinutes" to 3),
                mapOf("userId" to 5, "name" to "정트랙", "bibNumber" to "A005", "speedFactor" to 0.8, "delayMinutes" to 5)
            )

            val usersGpsData = mutableListOf<Map<String, Any>>()

            testUsers.forEach { user ->
                val userId = user["userId"] as Int
                val name = user["name"] as String
                val bibNumber = user["bibNumber"] as String
                val speedFactor = user["speedFactor"] as Double
                val delayMinutes = user["delayMinutes"] as Int

                // 각 유저의 GPS 데이터 생성
                val gpsDataList = generateUserGpsData(
                    gpxPoints = gpxPoints,
                    speedFactor = speedFactor,
                    delayMinutes = delayMinutes,
                    intervalSeconds = intervalSeconds,
                    errorRangeMeters = errorRangeMeters
                )

                usersGpsData.add(
                    mapOf(
                        "userId" to userId,
                        "name" to name,
                        "bibNumber" to bibNumber,
                        "eventId" to eventId,
                        "eventDetailId" to eventDetailId,
                        "totalPoints" to gpsDataList.size,
                        "estimatedDurationMinutes" to (gpsDataList.size * intervalSeconds / 60),
                        "gpsData" to gpsDataList
                    )
                )
            }

            val response = mapOf(
                "success" to true,
                "message" to "Fake GPS 데이터 생성 완료",
                "totalUsers" to usersGpsData.size,
                "intervalSeconds" to intervalSeconds,
                "errorRangeMeters" to errorRangeMeters,
                "routeInfo" to mapOf(
                    "totalPoints" to gpxPoints.size,
                    "estimatedDistance" to "2.4km",
                    "startPoint" to mapOf("lat" to gpxPoints.first().first, "lng" to gpxPoints.first().second),
                    "endPoint" to mapOf("lat" to gpxPoints.last().first, "lng" to gpxPoints.last().second)
                ),
                "users" to usersGpsData
            )

            logger.info("Fake GPS 데이터 생성 완료: ${usersGpsData.size}명 유저")
            ResponseEntity.ok(ApiResponse(data = response))

        } catch (e: IllegalArgumentException) {
            logger.warn("Fake GPS 데이터 생성 실패", e)
            return ResponseEntity.badRequest().body(
                ErrorResponse.create(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_INPUT_VALUE,
                    e.message ?: ErrorCode.INVALID_INPUT_VALUE.message
                )
            )
        } catch (e: Exception) {
            logger.error("Fake GPS 데이터 생성 실패", e)
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
     * 개별 유저의 GPS 데이터 생성 (순차 진행)
     */
    private fun generateUserGpsData(
        gpxPoints: List<Pair<Double, Double>>,
        speedFactor: Double,
        delayMinutes: Int,
        intervalSeconds: Int,
        errorRangeMeters: Double
    ): List<Map<String, Any>> {

        val gpsDataList = mutableListOf<Map<String, Any>>()
        val startTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)

        // 전체 경로 길이 계산
        val totalDistance = calculateTotalDistance(gpxPoints)

        // 유저의 평균 속도 계산 (km/h를 m/s로 변환)
        val baseSpeedKmh = 5.0 + (Math.random() * 3.0) // 5-8 km/h
        val userSpeedKmh = baseSpeedKmh * speedFactor
        val userSpeedMs = userSpeedKmh * 1000.0 / 3600.0 // m/s로 변환

        // 목표 총 소요시간 계산 (초)
        val totalTimeSeconds = (totalDistance / userSpeedMs).toInt()

        // 전체 경로에서 일정한 간격으로 포인트 생성
        val totalPoints = (totalTimeSeconds / intervalSeconds).coerceAtLeast(10)

        // 경로상의 거리 간격 계산
        val distanceInterval = totalDistance / totalPoints

        logger.info(
            "유저 GPS 데이터 생성: 총거리=${totalDistance}m, 속도=${userSpeedKmh}km/h, " +
                    "총시간=${totalTimeSeconds}초, 포인트수=${totalPoints}개"
        )

        var currentDistance = 0.0
        var currentTime = 0L

        for (pointIndex in 0 until totalPoints) {
            // 현재 거리에서의 위치 계산
            val locationInfo = getLocationAtDistance(gpxPoints, currentDistance)

            if (locationInfo != null) {
                val (lat, lng, bearing) = locationInfo

                // 진행 방향에 맞는 작은 GPS 오차 추가 (경로를 크게 벗어나지 않도록)
                val maxErrorMeters = minOf(errorRangeMeters, 10.0) // 최대 10m 오차
                val errorLat = (Math.random() - 0.5) * 2 * (maxErrorMeters / 111000.0)
                val errorLng = (Math.random() - 0.5) * 2 * (maxErrorMeters / (111000.0 * Math.cos(Math.toRadians(lat))))

                val finalLat = lat + errorLat
                val finalLng = lng + errorLng

                // 시간 계산
                val timestamp = startTime + currentTime * 1000

                // 고도 계산 (점진적 변화)
                val altitude = 10.0 + (Math.sin(pointIndex * 0.1) * 5.0) + (Math.random() * 2.0)

                // 정확도 계산 (3-8m 범위)
                val accuracy = 3.0 + (Math.random() * 5.0)

                gpsDataList.add(
                    mapOf(
                        "lat" to finalLat,
                        "lng" to finalLng,
                        "altitude" to altitude,
                        "accuracy" to accuracy,
                        "speed" to userSpeedKmh,
                        "bearing" to bearing,
                        "timestamp" to java.time.Instant.ofEpochMilli(timestamp).toString(),
                        "distanceFromStart" to currentDistance,
                        "progressPercent" to (currentDistance / totalDistance * 100).toInt()
                    )
                )

                // 다음 포인트로 이동
                currentDistance += distanceInterval
                currentTime += intervalSeconds
            }
        }

        logger.info("유저 GPS 데이터 생성 완료: 실제 생성된 포인트 수=${gpsDataList.size}")
        return gpsDataList
    }

    /**
     * 전체 경로 길이 계산
     */
    private fun calculateTotalDistance(gpxPoints: List<Pair<Double, Double>>): Double {
        var totalDistance = 0.0
        for (i in 0 until gpxPoints.size - 1) {
            val currentPoint = gpxPoints[i]
            val nextPoint = gpxPoints[i + 1]
            totalDistance += calculateDistance(
                currentPoint.first, currentPoint.second,
                nextPoint.first, nextPoint.second
            )
        }
        return totalDistance
    }

    /**
     * 특정 거리에서의 위치 및 방향 계산
     */
    private fun getLocationAtDistance(
        gpxPoints: List<Pair<Double, Double>>,
        targetDistance: Double
    ): Triple<Double, Double, Double>? {

        if (gpxPoints.isEmpty()) return null

        var accumulatedDistance = 0.0

        for (i in 0 until gpxPoints.size - 1) {
            val currentPoint = gpxPoints[i]
            val nextPoint = gpxPoints[i + 1]

            val segmentDistance = calculateDistance(
                currentPoint.first, currentPoint.second,
                nextPoint.first, nextPoint.second
            )

            if (accumulatedDistance + segmentDistance >= targetDistance) {
                // 이 세그먼트 내에서 목표 거리에 해당하는 위치 찾기
                val remainingDistance = targetDistance - accumulatedDistance
                val ratio = if (segmentDistance > 0) remainingDistance / segmentDistance else 0.0

                // 보간된 위치 계산
                val lat = currentPoint.first + (nextPoint.first - currentPoint.first) * ratio
                val lng = currentPoint.second + (nextPoint.second - currentPoint.second) * ratio

                // 방향 계산
                val bearing = calculateBearing(
                    currentPoint.first, currentPoint.second,
                    nextPoint.first, nextPoint.second
                )

                return Triple(lat, lng, bearing)
            }

            accumulatedDistance += segmentDistance
        }

        // 마지막 포인트 반환
        val lastPoint = gpxPoints.last()
        val secondLastPoint = if (gpxPoints.size > 1) gpxPoints[gpxPoints.size - 2] else lastPoint
        val lastBearing = calculateBearing(
            secondLastPoint.first, secondLastPoint.second,
            lastPoint.first, lastPoint.second
        )

        return Triple(lastPoint.first, lastPoint.second, lastBearing)
    }

    /**
     * 두 지점 간 거리 계산 (미터)
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0 // 지구 반지름 (미터)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * 두 지점 간 방향 계산 (도)
     */
    private fun calculateBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = Math.sin(dLng) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng)

        val bearing = Math.toDegrees(Math.atan2(y, x))
        return (bearing + 360) % 360
    }
}

