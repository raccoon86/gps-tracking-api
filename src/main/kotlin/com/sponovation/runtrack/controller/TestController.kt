package com.sponovation.runtrack.controller

import com.sponovation.runtrack.service.CourseDataService
import com.sponovation.runtrack.service.EventCourse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test")
class TestController(
    private val courseDataService: CourseDataService
) {
    
    private val logger = LoggerFactory.getLogger(TestController::class.java)

    /**
     * Redis에 저장된 모든 코스 데이터를 로그로 출력합니다.
     */
    @GetMapping("/redis/courses")
    fun logAllRedisData(): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: Redis 모든 코스 데이터 조회 요청 ===")
        
        try {
            // 모든 코스 데이터 로그 출력
            courseDataService.logAllCourseData()
            
            // API 응답용 데이터 수집
            val courseKeys = courseDataService.getAllCourseKeys()
            val eventKeys = courseDataService.getAllEventIndexKeys()
            
            val response = mapOf(
                "success" to true,
                "message" to "Redis 데이터 조회 완료 (로그 확인)",
                "courseKeysCount" to courseKeys.size,
                "eventKeysCount" to eventKeys.size,
                "courseKeys" to courseKeys,
                "eventKeys" to eventKeys
            )
            
            logger.info("테스트 API 응답: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Redis 데이터 조회 중 오류 발생", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Redis 데이터 조회 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 특정 코스 ID의 데이터를 조회합니다.
     */
    @GetMapping("/redis/course/{courseId}")
    fun getCourseData(@PathVariable courseId: String): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 특정 코스 데이터 조회 - $courseId ===")
        
        try {
            val courseData = courseDataService.getCourseData(courseId)
            
            if (courseData != null) {
                val response = mapOf(
                    "success" to true,
                    "message" to "코스 데이터 조회 성공",
                    "data" to mapOf(
                        "courseId" to courseData.courseId,
                        "eventId" to courseData.eventId,
                        "fileName" to courseData.fileName,
                        "totalDistance" to courseData.totalDistance,
                        "totalPoints" to courseData.totalPoints,
                        "createdAt" to courseData.createdAt,
                        "firstPoint" to if (courseData.interpolatedPoints.isNotEmpty()) {
                            val first = courseData.interpolatedPoints.first()
                            mapOf("lat" to first.latitude, "lng" to first.longitude, "elevation" to first.elevation)
                        } else null,
                        "lastPoint" to if (courseData.interpolatedPoints.isNotEmpty()) {
                            val last = courseData.interpolatedPoints.last()
                            mapOf("lat" to last.latitude, "lng" to last.longitude, "elevation" to last.elevation)
                        } else null
                    )
                )
                
                return ResponseEntity.ok(response)
            } else {
                return ResponseEntity.notFound().build()
            }
            
        } catch (e: Exception) {
            logger.error("코스 데이터 조회 중 오류 발생", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "코스 데이터 조회 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 특정 이벤트 ID의 코스 데이터를 조회합니다.
     */
    @GetMapping("/redis/event/{eventId}")
    fun getCourseDataByEventId(@PathVariable eventId: Long): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 이벤트 ID로 코스 데이터 조회 - $eventId ===")
        
        try {
            val courseData = courseDataService.getCourseDataByEventId(eventId)
            
            if (courseData != null) {
                val response = mapOf(
                    "success" to true,
                    "message" to "이벤트 코스 데이터 조회 성공",
                    "data" to mapOf(
                        "courseId" to courseData.courseId,
                        "eventId" to courseData.eventId,
                        "fileName" to courseData.fileName,
                        "totalDistance" to courseData.totalDistance,
                        "totalPoints" to courseData.totalPoints,
                        "createdAt" to courseData.createdAt
                    )
                )
                
                return ResponseEntity.ok(response)
            } else {
                return ResponseEntity.notFound().build()
            }
            
        } catch (e: Exception) {
            logger.error("이벤트 코스 데이터 조회 중 오류 발생", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "이벤트 코스 데이터 조회 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 테스트용 코스 데이터를 생성합니다.
     */
    @PostMapping("/redis/course/create")
    fun createTestCourseData(@RequestBody request: TestCourseRequest): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 코스 데이터 생성 요청 ===")
        logger.info("요청 데이터: eventId=${request.eventId}, fileName=${request.fileName}")
        
        try {
            val courseId = courseDataService.loadAndCacheCourseData(request.eventId, request.fileName)
            
            val response = mapOf(
                "success" to true,
                "message" to "코스 데이터 생성 성공",
                "courseId" to courseId,
                "eventId" to request.eventId,
                "fileName" to request.fileName
            )
            
            logger.info("코스 데이터 생성 완료: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("코스 데이터 생성 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "코스 데이터 생성 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 여러 테스트 코스 데이터를 일괄 생성합니다.
     */
    @PostMapping("/redis/courses/create-multiple")
    fun createMultipleTestCourseData(@RequestBody request: MultipleTestCourseRequest): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 여러 코스 데이터 일괄 생성 요청 ===")
        logger.info("요청 코스 수: ${request.courses.size}")
        
        try {
            val eventCourses = request.courses.map { EventCourse(it.eventId, it.fileName) }
            val courseIds = courseDataService.loadMultipleCourseData(eventCourses)
            
            val response = mapOf(
                "success" to true,
                "message" to "여러 코스 데이터 생성 완료",
                "totalRequested" to request.courses.size,
                "successCount" to courseIds.size,
                "courseIds" to courseIds
            )
            
            logger.info("여러 코스 데이터 생성 완료: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("여러 코스 데이터 생성 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "여러 코스 데이터 생성 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 특정 코스 데이터를 삭제합니다.
     */
    @DeleteMapping("/redis/course/{courseId}")
    fun deleteCourseData(@PathVariable courseId: String): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 코스 데이터 삭제 요청 - $courseId ===")
        
        try {
            courseDataService.deleteCourseData(courseId)
            
            val response = mapOf(
                "success" to true,
                "message" to "코스 데이터 삭제 완료",
                "courseId" to courseId
            )
            
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("코스 데이터 삭제 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "코스 데이터 삭제 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * Redis 연결 상태를 확인합니다.
     */
    @GetMapping("/redis/health")
    fun checkRedisHealth(): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: Redis 연결 상태 확인 ===")
        
        try {
            // Redis에 테스트 키 저장 및 조회
            val testKey = "test:health:${System.currentTimeMillis()}"
            val testValue = "Redis 연결 테스트"
            
            courseDataService.redisTemplate.opsForValue().set(testKey, testValue, 60, java.util.concurrent.TimeUnit.SECONDS)
            val retrievedValue = courseDataService.redisTemplate.opsForValue().get(testKey)
            
            // 테스트 키 삭제
            courseDataService.redisTemplate.delete(testKey)
            
            val isHealthy = retrievedValue == testValue
            
            val response = mapOf(
                "success" to isHealthy,
                "message" to if (isHealthy) "Redis 연결 정상" else "Redis 연결 이상",
                "testKey" to testKey,
                "testValue" to testValue,
                "retrievedValue" to (retrievedValue ?: "null")
            )
            
            logger.info("Redis 상태 확인 결과: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Redis 상태 확인 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Redis 상태 확인 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 테스트용 로컬 GPX 파일로 코스 데이터를 생성합니다.
     */
    @PostMapping("/redis/course/create-local")
    fun createTestCourseDataFromLocal(@RequestBody request: LocalTestCourseRequest): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 로컬 GPX 파일로 코스 데이터 생성 요청 ===")
        logger.info("요청 데이터: eventId=${request.eventId}, localFilePath=${request.localFilePath}")
        
        try {
            val courseId = courseDataService.loadAndCacheLocalGpxFile(request.eventId, request.localFilePath)
            
            val response = mapOf(
                "success" to true,
                "message" to "로컬 GPX 파일로 코스 데이터 생성 성공",
                "courseId" to courseId,
                "eventId" to request.eventId,
                "localFilePath" to request.localFilePath
            )
            
            logger.info("로컬 GPX 파일 코스 데이터 생성 완료: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("로컬 GPX 파일 코스 데이터 생성 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "로컬 GPX 파일 코스 데이터 생성 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 기본 테스트 데이터를 생성합니다 (test_route.gpx 파일 사용).
     */
    @PostMapping("/redis/course/create-default")
    fun createDefaultTestCourseData(): ResponseEntity<Map<String, Any>> {
        logger.info("=== 테스트: 기본 테스트 코스 데이터 생성 요청 ===")
        
        try {
            // 프로젝트 루트의 test_route.gpx 파일 사용
            val localFilePath = "test_route.gpx"
            val eventId = 1L
            
            val courseId = courseDataService.loadAndCacheLocalGpxFile(eventId, localFilePath)
            
            val response = mapOf(
                "success" to true,
                "message" to "기본 테스트 코스 데이터 생성 성공",
                "courseId" to courseId,
                "eventId" to eventId,
                "localFilePath" to localFilePath
            )
            
            logger.info("기본 테스트 코스 데이터 생성 완료: $response")
            return ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("기본 테스트 코스 데이터 생성 실패", e)
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "기본 테스트 코스 데이터 생성 실패: ${e.message}"
                )
            )
        }
    }
}

/**
 * 테스트용 코스 생성 요청 DTO
 */
data class TestCourseRequest(
    val eventId: Long,
    val fileName: String
)

/**
 * 테스트용 여러 코스 생성 요청 DTO
 */
data class MultipleTestCourseRequest(
    val courses: List<TestCourseRequest>
)

/**
 * 테스트용 로컬 파일 코스 생성 요청 DTO
 */
data class LocalTestCourseRequest(
    val eventId: Long,
    val localFilePath: String
) 