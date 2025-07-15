package com.sponovation.runtrack.service

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.service.GpxParsingException
import com.sponovation.runtrack.util.GeoUtils
import io.jenetics.jpx.WayPoint
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class CourseDataService(
    private val s3Client: S3Client,
    val redisTemplate: RedisTemplate<String, Any>,
    private val gpxParsingService: GpxParsingService,
    private val routeInterpolationService: RouteInterpolationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(CourseDataService::class.java)

    @Value("\${aws.s3.bucket-name:runtrack-gpx-files}")
    private lateinit var bucketName: String

    @Value("\${course.cache.ttl-hours:24}")
    private var cacheTtlHours: Long = 24

    /**
     * 대회 코스 GPX 파일을 S3에서 다운로드하고 파싱하여 Redis에 저장합니다.
     */
    fun loadAndCacheCourseData(eventId: Long, gpxFileName: String): String {
        val courseId = generateCourseId(eventId, gpxFileName)

        try {
            logger.info("코스 데이터 로드 시작: eventId=$eventId, file=$gpxFileName")

            // S3에서 GPX 파일 다운로드
            val gpxInputStream = downloadGpxFromS3(gpxFileName)

            // GPX 파일 파싱
            val waypoints = gpxParsingService.parseGpxFile(gpxInputStream)
            logger.info("GPX 파싱 완료: ${waypoints.size}개 웨이포인트")

            // 100미터 간격으로 보간
            val interpolatedPoints = routeInterpolationService.interpolateRoute(waypoints)
            logger.info("경로 보간 완료: ${interpolatedPoints.size}개 보간 포인트")

            // 총 거리 계산
            val totalDistance = calculateTotalDistance(waypoints)
            logger.info("총 거리 계산 완료: ${String.format("%.2f", totalDistance)}km")

            // 코스 데이터 생성
            val courseData = CourseData(
                courseId = courseId,
                eventId = eventId,
                fileName = gpxFileName,
                totalDistance = totalDistance,
                totalPoints = interpolatedPoints.size,
                interpolatedPoints = interpolatedPoints,
                createdAt = LocalDateTime.now().toString()
            )

            // Redis에 저장
            saveCourseDataToRedis(courseId, courseData)

            logger.info("코스 데이터 로드 완료: courseId=$courseId, points=${interpolatedPoints.size}, distance=${String.format("%.2f", totalDistance)}km")

            return courseId

        } catch (e: Exception) {
            logger.error("코스 데이터 로드 실패: eventId=$eventId, file=$gpxFileName", e)
            throw CourseDataException("코스 데이터 로드에 실패했습니다: ${e.message}", e)
        }
    }

    /**
     * S3에서 GPX 파일을 다운로드합니다.
     */
    private fun downloadGpxFromS3(fileName: String): ResponseInputStream<GetObjectResponse> {
        logger.info("S3에서 GPX 파일 다운로드 시작: bucket=$bucketName, key=$fileName")
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(fileName)
            .build()

        val response = s3Client.getObject(getObjectRequest)
        logger.info("S3 GPX 파일 다운로드 완료: $fileName")
        return response
    }

    /**
     * 코스 데이터를 Redis에 저장합니다.
     */
    private fun saveCourseDataToRedis(courseId: String, courseData: CourseData) {
        val key = "eventDetail:$courseId"
        val eventKey = "eventDetail:event:${courseData.eventId}"

        logger.info("Redis에 코스 데이터 저장 시작:")
        logger.info("  - 메인 키: $key")
        logger.info("  - 이벤트 인덱스 키: $eventKey")
        logger.info("  - TTL: ${cacheTtlHours}시간")
        logger.info("  - 데이터 크기: 총 ${courseData.totalPoints}개 포인트")

        // 메인 데이터를 JSON 문자열로 저장
        val jsonString = objectMapper.writeValueAsString(courseData)
        redisTemplate.opsForValue().set(key, jsonString, cacheTtlHours, TimeUnit.HOURS)

        // 인덱스를 위한 추가 키 저장
        redisTemplate.opsForValue().set(eventKey, courseId, cacheTtlHours, TimeUnit.HOURS)

        logger.info("Redis 저장 완료:")
        logger.info("  - 코스 ID: ${courseData.courseId}")
        logger.info("  - 이벤트 ID: ${courseData.eventId}")
        logger.info("  - 파일명: ${courseData.fileName}")
        logger.info("  - 총 거리: ${String.format("%.2f", courseData.totalDistance)}km")
        logger.info("  - 생성 시간: ${courseData.createdAt}")

        // 저장된 데이터 검증
        try {
            val savedJsonString = redisTemplate.opsForValue().get(key) as? String
            if (savedJsonString != null) {
                val savedData = objectMapper.readValue(savedJsonString, CourseData::class.java)
                logger.info("Redis 저장 검증 성공: 데이터가 정상적으로 저장되었습니다")
                logger.info("검증된 데이터 코스 ID: ${savedData.courseId}")
            } else {
                logger.error("Redis 저장 검증 실패: 데이터를 읽을 수 없습니다")
            }
        } catch (e: Exception) {
            logger.error("Redis 저장 검증 중 예외 발생: ${e.message}", e)
        }
    }

    /**
     * Redis에서 코스 데이터를 조회합니다.
     */
    fun getCourseData(courseId: String): CourseData? {
        val key = "eventDetail:$courseId"
        logger.info("Redis에서 코스 데이터 조회: $key")
        
        try {
            val jsonString = redisTemplate.opsForValue().get(key) as? String
            
            if (jsonString != null) {
                val courseData = objectMapper.readValue(jsonString, CourseData::class.java)
                logger.info("Redis 조회 성공:")
                logger.info("  - 코스 ID: ${courseData.courseId}")
                logger.info("  - 이벤트 ID: ${courseData.eventId}")
                logger.info("  - 파일명: ${courseData.fileName}")
                logger.info("  - 총 거리: ${String.format("%.2f", courseData.totalDistance)}km")
                logger.info("  - 포인트 수: ${courseData.totalPoints}개")
                logger.info("  - 생성 시간: ${courseData.createdAt}")
                return courseData
            } else {
                logger.warn("Redis 조회 실패: 키 '$key'에 해당하는 데이터가 없습니다")
                return null
            }
        } catch (e: Exception) {
            logger.error("Redis 조회 중 예외 발생: ${e.message}", e)
            return null
        }
    }

    /**
     * 이벤트 ID로 코스 데이터를 조회합니다.
     */
    fun getCourseDataByEventId(eventId: Long): CourseData? {
        val eventKey = "eventDetail:event:$eventId"
        logger.info("이벤트 ID로 코스 데이터 조회: eventId=$eventId")
        
        val courseId = redisTemplate.opsForValue().get(eventKey) as? String
        
        if (courseId != null) {
            logger.info("이벤트 인덱스에서 코스 ID 발견: $courseId")
            return getCourseData(courseId)
        } else {
            logger.warn("이벤트 ID '$eventId'에 해당하는 코스 데이터가 없습니다")
            return null
        }
    }

    /**
     * 여러 대회의 코스 데이터를 일괄 로드합니다.
     */
    fun loadMultipleCourseData(eventCourses: List<EventCourse>): List<String> {
        logger.info("여러 코스 데이터 일괄 로드 시작: ${eventCourses.size}개 코스")

        val courseIds = mutableListOf<String>()

        eventCourses.forEachIndexed { index, eventCourse ->
            try {
                logger.info("코스 로드 진행 상황: ${index + 1}/${eventCourses.size} - 이벤트 ID: ${eventCourse.eventId}")
                val courseId = loadAndCacheCourseData(eventCourse.eventId, eventCourse.gpxFileName)
                courseIds.add(courseId)
                logger.info("코스 로드 성공: ${eventCourse.eventId} -> $courseId")
            } catch (e: Exception) {
                logger.error("코스 데이터 로드 실패: ${eventCourse.eventId} - ${eventCourse.gpxFileName}", e)
            }
        }

        logger.info("일괄 로드 완료: ${courseIds.size}/${eventCourses.size}개 성공")
        return courseIds
    }

    /**
     * Redis에 저장된 모든 코스 데이터 키를 조회합니다.
     */
    fun getAllCourseKeys(): List<String> {
        val pattern = "eventDetail:course_*"
        val keys = redisTemplate.keys(pattern)
        
        logger.info("Redis에서 코스 키 조회:")
        logger.info("  - 패턴: $pattern")
        logger.info("  - 발견된 키 수: ${keys?.size ?: 0}개")
        
        keys?.forEach { key ->
            logger.info("  - 키: $key")
        }
        
        return keys?.toList() ?: emptyList()
    }

    /**
     * Redis에 저장된 모든 이벤트 인덱스 키를 조회합니다.
     */
    fun getAllEventIndexKeys(): List<String> {
        val pattern = "eventDetail:event:*"
        val keys = redisTemplate.keys(pattern)
        
        logger.info("Redis에서 이벤트 인덱스 키 조회:")
        logger.info("  - 패턴: $pattern")
        logger.info("  - 발견된 키 수: ${keys?.size ?: 0}개")
        
        keys?.forEach { key ->
            val courseId = redisTemplate.opsForValue().get(key) as? String
            logger.info("  - 키: $key -> 코스 ID: $courseId")
        }
        
        return keys?.toList() ?: emptyList()
    }

    /**
     * Redis에 저장된 모든 코스 데이터를 조회하고 로그로 출력합니다.
     */
    fun logAllCourseData() {
        logger.info("=== Redis 저장된 모든 코스 데이터 조회 시작 ===")

        val courseKeys = redisTemplate.keys("eventDetail:course_*")
        val eventKeys = redisTemplate.keys("eventDetail:event:*")

        logger.info("총 코스 데이터 키: ${courseKeys?.size ?: 0}개")
        logger.info("총 이벤트 인덱스 키: ${eventKeys?.size ?: 0}개")

        courseKeys?.forEach { key ->
            try {
                val courseData = redisTemplate.opsForValue().get(key) as? CourseData
                if (courseData != null) {
                    logger.info("코스 데이터 상세:")
                    logger.info("  키: $key")
                    logger.info("  코스 ID: ${courseData.courseId}")
                    logger.info("  이벤트 ID: ${courseData.eventId}")
                    logger.info("  파일명: ${courseData.fileName}")
                    logger.info("  총 거리: ${String.format("%.2f", courseData.totalDistance)}km")
                    logger.info("  포인트 수: ${courseData.totalPoints}개")
                    logger.info("  생성 시간: ${courseData.createdAt}")

                    // 첫 번째와 마지막 포인트 로그
                    if (courseData.interpolatedPoints.isNotEmpty()) {
                        val firstPoint = courseData.interpolatedPoints.first()
                        val lastPoint = courseData.interpolatedPoints.last()
                        logger.info("  첫 번째 포인트: (${firstPoint.latitude}, ${firstPoint.longitude})")
                        logger.info("  마지막 포인트: (${lastPoint.latitude}, ${lastPoint.longitude})")
                    }
                    logger.info("  ---")
                } else {
                    logger.warn("키 '$key'에서 데이터를 읽을 수 없습니다")
                }
            } catch (e: Exception) {
                logger.error("키 '$key' 처리 중 오류 발생", e)
            }
        }

        logger.info("=== Redis 코스 데이터 조회 완료 ===")
    }

    /**
     * 코스 ID를 생성합니다.
     */
    private fun generateCourseId(eventId: Long, fileName: String): String {
        return "course_${eventId}_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    /**
     * 웨이포인트 목록의 총 거리를 계산합니다.
     */
    private fun calculateTotalDistance(waypoints: List<ParsedGpxWaypoint>): Double {
        if (waypoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            val prevPoint = waypoints[i - 1]
            val currentPoint = waypoints[i]
            totalDistance += GeoUtils.calculateDistance(
                prevPoint.latitude, prevPoint.longitude,
                currentPoint.latitude, currentPoint.longitude
            )
        }
        
        return totalDistance / 1000.0 // 킬로미터로 변환
    }

    /**
     * Redis에서 코스 데이터를 삭제합니다.
     */
    fun deleteCourseData(courseId: String) {
        logger.info("Redis에서 코스 데이터 삭제 시작: $courseId")
        
        val courseData = getCourseData(courseId)
        courseData?.let {
            val mainKey = "eventDetail:$courseId"
            val eventKey = "eventDetail:event:${it.eventId}"
            
            redisTemplate.delete(mainKey)
            redisTemplate.delete(eventKey)
            
            logger.info("코스 데이터 삭제 완료:")
            logger.info("  - 삭제된 메인 키: $mainKey")
            logger.info("  - 삭제된 이벤트 키: $eventKey")
            logger.info("  - 코스 ID: $courseId")
        } ?: run {
            logger.warn("삭제할 코스 데이터를 찾을 수 없습니다: $courseId")
        }
    }

    /**
     * 이미 파싱된 웨이포인트로부터 코스 데이터를 생성하여 Redis에 저장합니다.
     * (GPX 파일 중복 파싱 방지용)
     * 
     * @param eventId 이벤트 ID
     * @param fileName 파일명
     * @param waypoints 이미 파싱되고 보간된 웨이포인트 리스트
     * @return 생성된 코스 ID
     */
    fun saveFromParsedWaypoints(
        eventId: Long, 
        fileName: String, 
        waypoints: List<WayPoint>
    ): String {
        val courseId = generateCourseId(eventId, fileName)
        
        try {
            logger.info("파싱된 웨이포인트로부터 코스 데이터 생성 시작:")
            logger.info("  - 이벤트 ID: $eventId")
            logger.info("  - 파일명: $fileName")
            logger.info("  - 웨이포인트 수: ${waypoints.size}개")
            logger.info("  - 생성된 코스 ID: $courseId")

            // 웨이포인트 데이터 검증
            if (waypoints.isEmpty()) {
                logger.error("웨이포인트가 비어있습니다")
                throw IllegalArgumentException("웨이포인트가 없습니다")
            }
            
            if (waypoints.size < 2) {
                logger.error("웨이포인트가 너무 적습니다: ${waypoints.size}개")
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 좌표점이 필요합니다 (현재: ${waypoints.size}개)")
            }
            
            // WayPoint를 ParsedGpxWaypoint로 변환
            logger.debug("WayPoint를 ParsedGpxWaypoint로 변환 시작")
            val parsedWaypoints = waypoints.map { waypoint ->
                ParsedGpxWaypoint(
                    latitude = waypoint.latitude.toDegrees(),
                    longitude = waypoint.longitude.toDegrees(),
                    elevation = waypoint.elevation.map { it.toDouble() }.orElse(0.0),
                    timestamp = waypoint.time.orElse(java.time.Instant.now())
                )
            }
            
            logger.debug("WayPoint 변환 완료: ${parsedWaypoints.size}개")
            
            // 좌표 범위 검증
            parsedWaypoints.forEach { waypoint ->
                if (waypoint.latitude < -90 || waypoint.latitude > 90) {
                    logger.error("유효하지 않은 위도: ${waypoint.latitude}")
                    throw IllegalArgumentException("유효하지 않은 위도 값이 포함되어 있습니다: ${waypoint.latitude}")
                }
                if (waypoint.longitude < -180 || waypoint.longitude > 180) {
                    logger.error("유효하지 않은 경도: ${waypoint.longitude}")
                    throw IllegalArgumentException("유효하지 않은 경도 값이 포함되어 있습니다: ${waypoint.longitude}")
                }
            }
            
            logger.debug("웨이포인트 데이터 검증 완료")
            
            // 100미터 간격으로 보간 (이미 보간된 데이터지만 InterpolatedPoint 형태로 변환 필요)
            logger.debug("보간 포인트 변환 시작")
            val interpolatedPoints = try {
                routeInterpolationService.interpolateRoute(parsedWaypoints)
            } catch (e: Exception) {
                logger.error("보간 포인트 변환 실패: ${e.message}", e)
                throw Exception("보간 포인트 변환 중 오류가 발생했습니다: ${e.message}", e)
            }
            
            logger.info("보간 포인트 변환 완료: ${interpolatedPoints.size}개")
            
            // 총 거리 계산
            logger.debug("총 거리 계산 시작")
            val totalDistance = try {
                calculateTotalDistance(parsedWaypoints)
            } catch (e: Exception) {
                logger.error("총 거리 계산 실패: ${e.message}", e)
                throw Exception("경로 거리 계산 중 오류가 발생했습니다: ${e.message}", e)
            }
            
            logger.info("총 거리 계산 완료: ${String.format("%.2f", totalDistance)}km")
            
            // 최소 거리 검증
            if (totalDistance < 0.01) { // 10미터 미만
                logger.error("경로 거리가 너무 짧습니다: ${totalDistance}km")
                throw IllegalArgumentException("경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${String.format("%.2f", totalDistance * 1000)}m)")
            }
            
            // 코스 데이터 생성
            logger.debug("코스 데이터 객체 생성")
            val courseData = CourseData(
                courseId = courseId,
                eventId = eventId,
                fileName = fileName,
                totalDistance = totalDistance,
                totalPoints = interpolatedPoints.size,
                interpolatedPoints = interpolatedPoints,
                createdAt = LocalDateTime.now().toString()
            )
            
            // Redis에 저장
            logger.debug("Redis 저장 시작")
            try {
                saveCourseDataToRedis(courseId, courseData)
            } catch (e: Exception) {
                logger.error("Redis 저장 실패: ${e.message}", e)
                throw Exception("코스 데이터 저장 중 오류가 발생했습니다: ${e.message}", e)
            }
            
            logger.info("파싱된 웨이포인트로부터 코스 데이터 생성 완료:")
            logger.info("  - 코스 ID: $courseId")
            logger.info("  - 원본 웨이포인트: ${parsedWaypoints.size}개")
            logger.info("  - 보간 포인트: ${interpolatedPoints.size}개") 
            logger.info("  - 총 거리: ${String.format("%.2f", totalDistance)}km")
            
            return courseId
            
        } catch (e: IllegalArgumentException) {
            // 검증 실패 (사용자 오류)
            logger.warn("웨이포인트 검증 실패: ${e.message}")
            throw CourseDataException("웨이포인트 데이터가 유효하지 않습니다: ${e.message}", e)
        } catch (e: Exception) {
            // 예상치 못한 오류
            logger.error("파싱된 웨이포인트로부터 코스 데이터 생성 중 예상치 못한 오류:", e)
            logger.error("  - 이벤트 ID: $eventId")
            logger.error("  - 파일명: $fileName")
            logger.error("  - 웨이포인트 수: ${waypoints.size}")
            logger.error("  - 오류 타입: ${e.javaClass.simpleName}")
            logger.error("  - 오류 메시지: ${e.message}")
            
            throw CourseDataException("코스 데이터 생성 중 서버 오류가 발생했습니다. 관리자에게 문의해주세요.", e)
        }
    }

    /**
     * 업로드된 MultipartFile로부터 GPX 데이터를 로드합니다.
     */
    fun loadAndCacheFromMultipartFile(eventId: Long, file: org.springframework.web.multipart.MultipartFile): String {
        val courseId = generateCourseId(eventId, file.originalFilename ?: "unknown.gpx")

        try {
            logger.info("업로드된 GPX 파일 로드 시작:")
            logger.info("  - 이벤트 ID: $eventId")
            logger.info("  - 파일명: ${file.originalFilename}")
            logger.info("  - 파일 크기: ${file.size}bytes")
            logger.info("  - 콘텐츠 타입: ${file.contentType}")
            logger.info("  - 생성된 코스 ID: $courseId")

            // 파일 유효성 검증
            logger.debug("파일 유효성 검증 시작")

            if (file.isEmpty) {
                logger.error("업로드된 파일이 비어있습니다")
                throw IllegalArgumentException("업로드된 파일이 비어있습니다")
            }

            if (file.size > 10 * 1024 * 1024) { // 10MB 제한
                logger.error("파일 크기 초과: ${file.size}bytes (최대 10MB)")
                throw IllegalArgumentException("파일 크기가 너무 큽니다 (최대 10MB)")
            }

            // 파일 확장자 검증
            val fileName = file.originalFilename ?: ""
            if (!fileName.lowercase().endsWith(".gpx")) {
                logger.warn("파일 확장자가 .gpx가 아닙니다: $fileName")
            }

            logger.debug("파일 유효성 검증 완료")

            // MultipartFile에서 GPX 데이터 안전하게 읽기 (바이트 배열 방식)
            logger.debug("GPX 파일 파싱 시작")
            val waypoints = try {
                logger.debug("바이트 배열 방식으로 파일 읽기 시도")
                val fileBytes = file.bytes
                logger.debug("파일 바이트 배열 읽기 완료: ${fileBytes.size}bytes")

                // 바이트 배열의 첫 부분 검사 (디버깅)
                if (fileBytes.isNotEmpty()) {
                    val preview = String(fileBytes.take(100).toByteArray())
                    logger.debug("파일 내용 미리보기: ${preview}")
                }

                gpxParsingService.parseGpxFile(fileBytes)
            } catch (e: GpxParsingException) {
                logger.warn("바이트 배열 방식 실패, InputStream 방식으로 재시도: ${e.message}")
                try {
                    file.inputStream.use { inputStream ->
                        logger.debug("InputStream 방식으로 파일 읽기 시도")
                        gpxParsingService.parseGpxFile(inputStream)
                    }
                } catch (retryException: Exception) {
                    logger.error("InputStream 방식도 실패: ${retryException.message}")
                    throw GpxParsingException("GPX 파일 파싱 실패 (두 방식 모두 실패): ${retryException.message}", retryException)
                }
            } catch (e: Exception) {
                logger.warn("바이트 배열 방식 실패 (일반 예외), InputStream 방식으로 재시도: ${e.message}")
                try {
                    file.inputStream.use { inputStream ->
                        logger.debug("InputStream 방식으로 파일 읽기 시도")
                        gpxParsingService.parseGpxFile(inputStream)
                    }
                } catch (retryException: Exception) {
                    logger.error("InputStream 방식도 실패: ${retryException.message}")
                    throw Exception("GPX 파일 읽기 실패 (두 방식 모두 실패): ${retryException.message}", retryException)
                }
            }

            logger.info("GPX 파싱 완료: ${waypoints.size}개 웨이포인트")

            // 웨이포인트 데이터 검증
            if (waypoints.isEmpty()) {
                logger.error("파싱된 웨이포인트가 비어있습니다")
                throw IllegalArgumentException("GPX 파일에 유효한 경로 데이터가 없습니다")
            }

            if (waypoints.size < 2) {
                logger.error("웨이포인트가 너무 적습니다: ${waypoints.size}개")
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 좌표점이 필요합니다 (현재: ${waypoints.size}개)")
            }

            // 좌표 범위 검증
            waypoints.forEach { waypoint ->
                if (waypoint.latitude < -90 || waypoint.latitude > 90) {
                    logger.error("유효하지 않은 위도: ${waypoint.latitude}")
                    throw IllegalArgumentException("유효하지 않은 위도 값이 포함되어 있습니다: ${waypoint.latitude}")
                }
                if (waypoint.longitude < -180 || waypoint.longitude > 180) {
                    logger.error("유효하지 않은 경도: ${waypoint.longitude}")
                    throw IllegalArgumentException("유효하지 않은 경도 값이 포함되어 있습니다: ${waypoint.longitude}")
                }
            }

            logger.debug("웨이포인트 데이터 검증 완료")

            // 100미터 간격으로 보간
            logger.debug("경로 보간 시작")
            val interpolatedPoints = try {
                routeInterpolationService.interpolateRoute(waypoints)
            } catch (e: Exception) {
                logger.error("경로 보간 실패: ${e.message}", e)
                throw Exception("경로 보간 처리 중 오류가 발생했습니다: ${e.message}", e)
            }

            logger.info("경로 보간 완료: ${interpolatedPoints.size}개 보간 포인트")

            // 총 거리 계산
            logger.debug("총 거리 계산 시작")
            val totalDistance = try {
                calculateTotalDistance(waypoints)
            } catch (e: Exception) {
                logger.error("총 거리 계산 실패: ${e.message}", e)
                throw Exception("경로 거리 계산 중 오류가 발생했습니다: ${e.message}", e)
            }

            logger.info("총 거리 계산 완료: ${String.format("%.2f", totalDistance)}km")

            // 최소 거리 검증
            if (totalDistance < 0.01) { // 10미터 미만
                logger.error("경로 거리가 너무 짧습니다: ${totalDistance}km")
                throw IllegalArgumentException("경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${String.format("%.2f", totalDistance * 1000)}m)")
            }

            // 코스 데이터 생성
            logger.debug("코스 데이터 객체 생성")
            val courseData = CourseData(
                courseId = courseId,
                eventId = eventId,
                fileName = file.originalFilename ?: "unknown.gpx",
                totalDistance = totalDistance,
                totalPoints = interpolatedPoints.size,
                interpolatedPoints = interpolatedPoints,
                createdAt = LocalDateTime.now().toString()
            )

            // Redis에 저장
            logger.debug("Redis 저장 시작")
            try {
                saveCourseDataToRedis(courseId, courseData)
            } catch (e: Exception) {
                logger.error("Redis 저장 실패: ${e.message}", e)
                throw Exception("코스 데이터 저장 중 오류가 발생했습니다: ${e.message}", e)
            }

            logger.info("업로드된 GPX 파일 로드 완료:")
            logger.info("  - 코스 ID: $courseId")
            logger.info("  - 원본 웨이포인트: ${waypoints.size}개")
            logger.info("  - 보간 포인트: ${interpolatedPoints.size}개")
            logger.info("  - 총 거리: ${String.format("%.2f", totalDistance)}km")

            return courseId

        } catch (e: IllegalArgumentException) {
            // 검증 실패 (사용자 오류)
            logger.warn("GPX 파일 검증 실패: ${e.message}")
            throw CourseDataException("업로드된 파일이 유효하지 않습니다: ${e.message}", e)
        } catch (e: GpxParsingException) {
            // GPX 파싱 실패 (파일 형식 오류)
            logger.error("GPX 파싱 실패: ${e.message}")
            throw CourseDataException("GPX 파일 파싱에 실패했습니다: ${e.message}", e)
        } catch (e: Exception) {
            // 예상치 못한 오류
            logger.error("업로드된 GPX 파일 로드 중 예상치 못한 오류:", e)
            logger.error("  - 이벤트 ID: $eventId")
            logger.error("  - 파일명: ${file.originalFilename}")
            logger.error("  - 오류 타입: ${e.javaClass.simpleName}")
            logger.error("  - 오류 메시지: ${e.message}")

            throw CourseDataException("GPX 파일 처리 중 서버 오류가 발생했습니다. 관리자에게 문의해주세요.", e)
        }
    }

    /**
     * 테스트용: 로컬 파일에서 GPX 데이터를 로드합니다.
     */
    fun loadAndCacheLocalGpxFile(eventId: Long, localFilePath: String): String {
        val courseId = generateCourseId(eventId, localFilePath)
        
        try {
            logger.info("로컬 GPX 파일 로드 시작: eventId=$eventId, file=$localFilePath")

            // 로컬 파일에서 GPX 데이터 읽기
            val file = java.io.File(localFilePath)
            if (!file.exists()) {
                throw CourseDataException("로컬 GPX 파일을 찾을 수 없습니다: $localFilePath")
            }
            
            val gpxInputStream = file.inputStream()
            
            // GPX 파일 파싱
            val waypoints = gpxParsingService.parseGpxFile(gpxInputStream)
            logger.info("GPX 파싱 완료: ${waypoints.size}개 웨이포인트")
            
            // 100미터 간격으로 보간
            val interpolatedPoints = routeInterpolationService.interpolateRoute(waypoints)
            logger.info("경로 보간 완료: ${interpolatedPoints.size}개 보간 포인트")
            
            // 총 거리 계산
            val totalDistance = calculateTotalDistance(waypoints)
            logger.info("총 거리 계산 완료: ${String.format("%.2f", totalDistance)}km")
            
            // 코스 데이터 생성
            val courseData = CourseData(
                courseId = courseId,
                eventId = eventId,
                fileName = file.name,
                totalDistance = totalDistance,
                totalPoints = interpolatedPoints.size,
                interpolatedPoints = interpolatedPoints,
                createdAt = LocalDateTime.now().toString()
            )
            
            // Redis에 저장
            saveCourseDataToRedis(courseId, courseData)
            
            logger.info("로컬 GPX 파일 로드 완료: courseId=$courseId, points=${interpolatedPoints.size}, distance=${String.format("%.2f", totalDistance)}km")
            
            return courseId
            
        } catch (e: Exception) {
            logger.error("로컬 GPX 파일 로드 실패: eventId=$eventId, file=$localFilePath", e)
            throw CourseDataException("로컬 GPX 파일 로드에 실패했습니다: ${e.message}", e)
        }
    }
}

/**
 * 코스 데이터 클래스
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CourseData(
    val courseId: String,
    val eventId: Long,
    val fileName: String,
    val totalDistance: Double,
    val totalPoints: Int,
    val interpolatedPoints: List<InterpolatedPoint>,
    val createdAt: String
)

/**
 * 이벤트 코스 정보
 */
data class EventCourse(
    val eventId: Long,
    val gpxFileName: String
)

/**
 * 코스 데이터 관련 예외
 */
class CourseDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) 