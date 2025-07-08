package com.sponovation.runtrack.controller

import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.service.GpxService
import com.sponovation.runtrack.service.CourseDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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
@RequestMapping("/api/gpx")
@Tag(name = "GPX 경로 처리", description = "GPX 파일 기반 경로 처리 및 GPS 위치 보정 API")
class GpxController(
    private val gpxService: GpxService,
    private val courseDataService: CourseDataService
) {
    
    private val logger = LoggerFactory.getLogger(GpxController::class.java)
    
    /**
     * GPS 위치를 GPX 경로에 매칭하여 보정된 위치를 반환합니다
     * 
     * 이 엔드포인트는 실시간으로 수신된 GPS 좌표를 분석하여 다음과 같은 처리를 수행합니다:
     * 
     * 처리 과정:
     * 1. 칼만 필터를 통한 GPS 신호 노이즈 제거
     * 2. 맵 매칭 알고리즘을 통한 경로 상의 가장 적절한 지점 계산
     * 3. 체크포인트 도달 여부 확인
     * 4. 경로 이탈 감지 및 거리 계산
     * 5. 보정된 위치 정보 및 진행 상황 반환
     * 
     * 보정 알고리즘:
     * - Weighted Snap-to-Road: 거리와 방향을 고려한 가중치 기반 경로 매칭
     * - 산악 지형 특화: 고도 변화와 방향 급변에 최적화된 알고리즘
     * - 실시간 처리: 100ms 이내의 빠른 응답 시간 보장
     * 
     * @param request GPS 위치 보정 요청 데이터
     *                - eventDetailId: 대회 상세 ID (필수)
     *                - gpsData: GPS 좌표 정보 리스트 (위도, 경도, 고도, 정확도, 속도, 방향 등)
     * @return 보정된 위치 정보 및 경로 매칭 결과
     *         - correctedLocations: 칼만 필터 및 맵 매칭을 통해 보정된 위치 리스트
     *         - matchingResults: 각 GPS 포인트의 경로 매칭 결과 (성공/실패, 거리 등)
     *         - checkpointReaches: 새로 도달한 체크포인트 정보
     *         - routeProgress: 전체 경로에서의 진행률 (0.0 ~ 1.0)
     * 
     * HTTP 상태 코드:
     * - 200 OK: 보정 성공
     * - 400 Bad Request: 잘못된 요청 데이터 (필수 파라미터 누락, 잘못된 형식 등)
     * - 404 Not Found: 해당 대회 정보 또는 GPX 파일을 찾을 수 없음
     * - 500 Internal Server Error: 서버 내부 오류 (보정 로직 오류, 데이터베이스 오류 등)
     * 
     */
    @PostMapping("/correct-location")
    @Operation(
        summary = "GPS 위치 보정",
        description = "실시간 GPS 좌표를 GPX 경로에 매칭하여 보정된 위치와 경로 진행 상황을 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "위치 보정 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 파라미터 누락)"),
            ApiResponse(responseCode = "404", description = "대회 정보 또는 GPX 파일 없음"),
            ApiResponse(responseCode = "500", description = "보정 로직 오류")
        ]
    )
    fun correctLocation(
        @Parameter(description = "GPS 위치 보정 요청 데이터", required = true)
        @Valid @RequestBody request: CorrectLocationRequestDto
    ): ResponseEntity<CorrectLocationResponseDto> {

        return try {
            // GPS 데이터 유효성 검증
            if (request.gpsData.isEmpty()) {
                logger.warn("GPS 데이터가 비어있습니다")
                return ResponseEntity.badRequest().build()
            }
            
            // 위치 보정 처리
            val response = gpxService.correctLocation(request)

            ResponseEntity.ok(response)

        } catch (e: IllegalArgumentException) {
            logger.warn("GPS 위치 보정 실패 - 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().build()
        } catch (e: NoSuchElementException) {
            logger.warn("GPS 위치 보정 실패 - 대회 정보 없음: eventDetailId=${request.eventDetailId}")
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            logger.error("GPS 위치 보정 실패 - 서버 오류: eventDetailId=${request.eventDetailId}", e)
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
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "업로드 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 또는 파라미터 오류)"),
            ApiResponse(responseCode = "500", description = "서버 내부 오류")
        ]
    )
    fun uploadGpx(
        @Parameter(description = "업로드할 GPX 파일", required = true)
        @RequestPart("file") file: MultipartFile,
        @Parameter(description = "사용자 ID", required = true)
        @RequestParam("userId") userId: Long,
        @Parameter(description = "이벤트 ID", required = true)
        @RequestParam("eventDetailId") eventDetailId: Long,
        @Parameter(description = "경로 이름", required = true)
        @RequestParam("routeName") routeName: String,
        @Parameter(description = "경로 설명")
        @RequestParam("description", required = false, defaultValue = "") description: String
    ): ResponseEntity<GpxUploadResponseDto> {

        return try {
            logger.info("GPX 파일 업로드 시작: 파일명=${file.originalFilename}, eventDetailId=$eventDetailId")
            
            // GPX 파일 유효성 검증
            if (file.isEmpty) {
                logger.warn("빈 GPX 파일 업로드 시도")
                return ResponseEntity.badRequest().build()
            }

            // 1. GPX 파일을 한 번만 파싱하고 보간 포인트를 생성하여 DB와 Redis에 저장
            val uploadResult = gpxService.parseAndSaveGpxFileWithCourseData(
                file = file,
                routeName = routeName,
                description = description,
                eventDetailId = eventDetailId
            )
            
            logger.info("GPX 파일 업로드 및 코스 데이터 생성 완료")
            logger.info("  - GPX Route ID: ${uploadResult.gpxRoute.id}")
            logger.info("  - Course ID: ${uploadResult.courseId}")
            logger.info("  - 총 거리: ${uploadResult.gpxRoute.totalDistance}m")
            logger.info("  - 보간 포인트: ${uploadResult.totalInterpolatedPoints}개")

            // 4. 응답 생성
            val response = GpxUploadResponseDto(
                courseId = uploadResult.courseId,
                routeId = uploadResult.gpxRoute.id,
                routeName = uploadResult.gpxRoute.name,
                totalDistance = uploadResult.gpxRoute.totalDistance,
                totalPoints = uploadResult.totalInterpolatedPoints,
                createdAt = uploadResult.createdAt
            )

            ResponseEntity.ok(response)

        } catch (e: IllegalArgumentException) {
            logger.warn("GPX 파일 업로드 실패 - 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            logger.error("GPX 파일 업로드 실패 - 서버 오류: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 이벤트 ID로 코스 데이터 조회
     * 
     * Redis에 저장된 코스 데이터를 이벤트 ID로 조회합니다.
     * 
     * @param eventId 이벤트 ID
     * @return 코스 데이터 (100미터 간격 보간 포인트 포함)
     */
    @GetMapping("/course/{eventId}")
    @Operation(
        summary = "이벤트 ID로 코스 데이터 조회",
        description = "Redis에 저장된 코스 데이터를 이벤트 ID로 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "코스 데이터 조회 성공"),
            ApiResponse(responseCode = "404", description = "코스 데이터를 찾을 수 없음"),
            ApiResponse(responseCode = "500", description = "서버 내부 오류")
        ]
    )
    fun getCourseDataByEventId(
        @Parameter(description = "이벤트 ID", required = true)
        @PathVariable eventId: Long
    ): ResponseEntity<CourseDataResponseDto> {

        return try {
            logger.info("코스 데이터 조회 시작: eventId=$eventId")
            
            val courseData = courseDataService.getCourseDataByEventId(eventId)
            
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
                ResponseEntity.ok(response)
            } else {
                logger.warn("코스 데이터를 찾을 수 없음: eventId=$eventId")
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("코스 데이터 조회 실패: eventId=$eventId", e)
            ResponseEntity.internalServerError().build()
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
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "코스 데이터 조회 성공"),
            ApiResponse(responseCode = "404", description = "코스 데이터를 찾을 수 없음"),
            ApiResponse(responseCode = "500", description = "서버 내부 오류")
        ]
    )
    fun getCourseDataByCourseId(
        @Parameter(description = "코스 ID", required = true)
        @PathVariable courseId: String
    ): ResponseEntity<CourseDataResponseDto> {

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
                ResponseEntity.ok(response)
            } else {
                logger.warn("코스 데이터를 찾을 수 없음: courseId=$courseId")
                ResponseEntity.notFound().build()
            }

        } catch (e: Exception) {
            logger.error("코스 데이터 조회 실패: courseId=$courseId", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 모든 코스 데이터 목록 조회
     * 
     * Redis에 저장된 모든 코스 데이터의 요약 정보를 조회합니다.
     * 
     * @return 코스 데이터 목록 (요약 정보만 포함)
     */
    @GetMapping("/courses")
    @Operation(
        summary = "모든 코스 데이터 목록 조회",
        description = "Redis에 저장된 모든 코스 데이터의 요약 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "코스 데이터 목록 조회 성공"),
            ApiResponse(responseCode = "500", description = "서버 내부 오류")
        ]
    )
    fun getAllCourseData(): ResponseEntity<CourseDataListResponseDto> {

        return try {
            logger.info("모든 코스 데이터 목록 조회 시작")
            
            val courseKeys = courseDataService.getAllCourseKeys()
            val courses = mutableListOf<CourseDataSummaryDto>()
            
            courseKeys.forEach { key ->
                try {
                    val courseId = key.removePrefix("course:")
                    val courseData = courseDataService.getCourseData(courseId)
                    
                    courseData?.let {
                        courses.add(
                            CourseDataSummaryDto(
                                courseId = it.courseId,
                                eventId = it.eventId,
                                fileName = it.fileName,
                                totalDistance = it.totalDistance,
                                totalPoints = it.totalPoints,
                                createdAt = it.createdAt
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("코스 데이터 처리 실패: key=$key", e)
                }
            }
            
            val response = CourseDataListResponseDto(
                courses = courses,
                totalCount = courses.size
            )
            
            logger.info("모든 코스 데이터 목록 조회 성공: 총 ${courses.size}개")
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("모든 코스 데이터 목록 조회 실패", e)
            ResponseEntity.internalServerError().build()
        }
    }
}
