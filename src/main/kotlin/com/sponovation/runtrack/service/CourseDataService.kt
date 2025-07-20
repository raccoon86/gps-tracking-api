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