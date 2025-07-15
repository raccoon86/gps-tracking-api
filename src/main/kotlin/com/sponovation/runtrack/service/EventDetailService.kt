package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.EventDetail
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.repository.EventRepository
import com.sponovation.runtrack.repository.CourseRepository
import com.sponovation.runtrack.repository.UserRepository
import com.sponovation.runtrack.repository.EventParticipantRepository
import com.sponovation.runtrack.enums.EventStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 대회 상세 조회 서비스
 * 
 * 특정 대회 코스에 참여하는 참가자들의 실시간 위치 데이터와 
 * 대회 관련 정보를 종합적으로 제공합니다.
 */
@Service
class EventDetailService(
    private val eventRepository: EventRepository,
    private val courseRepository: CourseRepository,
    private val leaderboardService: LeaderboardService,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    
    private val logger = LoggerFactory.getLogger(EventDetailService::class.java)
    
    @Value("\${aws.s3.bucket-name:runtrack-gpx-files}")
    private lateinit var bucketName: String
    
    @Value("\${aws.s3.region:ap-northeast-2}")
    private lateinit var s3Region: String
    
    companion object {
        private const val LOCATION_KEY_PREFIX = "location"
        private const val LEADERBOARD_KEY_PREFIX = "leaderboard"
        private const val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        private const val USER_TRACKERS_KEY_PREFIX = "userTrackers"
        private const val KEY_SEPARATOR = ":"
    }
    
    /**
     * 대회 상세 정보 조회
     * 
     * @param eventId 이벤트 ID (Event 테이블의 기본키)
     * @param eventDetailId 이벤트 상세 ID (EventDetail 테이블의 기본키로 해석)
     * @param currentUserId 현재 로그인한 사용자 ID (선택사항)
     * @return 대회 상세 정보
     */
    fun getEventDetail(
        eventId: Long,
        eventDetailId: Long,
        currentUserId: Long?
    ): EventDetailResponseDto {
        logger.info("대회 상세 조회 시작: eventId=$eventId, eventDetailId=$eventDetailId, currentUserId=$currentUserId")
        
        try {
            // 1. 대회 정보 조회 및 유효성 검증
            val event = eventRepository.findById(eventId)
                .orElseThrow { IllegalArgumentException("해당 대회를 찾을 수 없습니다: $eventId") }
            
            logger.info("대회 조회 완료: eventName=${event.name}")

            // 2. 코스 정보 및 GPX 파일 URL 조회
            val courseInfo = getCourseInfoAndGpxUrl(eventId, eventDetailId)

            // 3. 참가자 위치 데이터 조회 (1~3위 + 트래커 목록)
            val participantsLocations = getParticipantsLocations(eventId, eventDetailId, currentUserId)
            
            // 4. 상위 랭커 정보 조회
            val topRankers = getTopRankers(eventDetailId)
            
            // 5. 코스 카테고리 정보 조회
            val courseCategory = getCourseCategories(listOf(courseInfo))
            
            // 6. 응답 DTO 생성
            val response = EventDetailResponseDto(
                eventId = eventId,
                eventDetailId = eventDetailId,
                name = event.name, // 실제 대회 이름 사용
                courseCategory = courseCategory,
                participantsLocations = participantsLocations,
                topRankers = topRankers
            )
            
            return response
            
        } catch (e: Exception) {
            logger.error("대회 상세 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            throw e
        }
    }
    
    /**
     * 대회 진행 상태 확인
     * 
     * @param eventStatus 이벤트 상태
     * @param eventDate 이벤트 날짜
     * @return 진행 중 여부
     */
    private fun isEventOngoing(eventStatus: EventStatus, eventDate: LocalDate): Boolean {
        val today = LocalDate.now()
        return when (eventStatus) {
            EventStatus.SCHEDULED -> eventDate.isEqual(today) || eventDate.isAfter(today)
            EventStatus.COMPLETED -> false
            EventStatus.CANCELED -> false
            EventStatus.HOLD -> false
            else -> false
        }
    }
    
    /**
     * Long 타입 ID를 문자열로 변환
     * 
     * @param longId Long 타입 ID
     * @return 문자열
     */
    private fun convertLongToString(longId: Long): String {
        return longId.toString()
    }
    
    /**
     * 코스 카테고리 정보 조회
     * 
     * @param cours 코스 리스트
     * @return 코스 카테고리 리스트
     */
    private fun getCourseCategories(eventDetail: List<EventDetail>): List<CourseCategoryDto> {
        return eventDetail.map { course ->
            CourseCategoryDto(
                course = course.distance?.toDouble(), // BigDecimal을 Double로 변환
                eventDetailId = course.id // UUID를 문자열로 변환
            )
        }
    }
    
    /**
     * 참가자 위치 데이터 조회 (1~3위 + 트래커 목록)
     * 
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param currentUserId 현재 로그인한 사용자 ID
     * @return 참가자 위치 리스트
     */
    private fun getParticipantsLocations(eventId: Long, eventDetailId: Long, currentUserId: Long?): List<EventParticipantLocationDto> {
        logger.info("참가자 위치 데이터 조회 시작: eventId=$eventId, eventDetailId=$eventDetailId")
        try {
            // TODO: 실제 테이블에서 전체 참가자 목록을 가져오도록 수정 필요
            // val allUserIds = eventParticipantRepository.findAllByEventDetailId(eventDetailId).map { it.userId.toString() }
            // 임시: 테스트용 전체 유저 ID 하드코딩
            val allUserIds = listOf(1L, 2L, 3L, 4L, 5L)
            val participantLocations = getBatchParticipantLocations(allUserIds, eventDetailId)
            val mapMarkerData = buildMapMarkerData(participantLocations, eventId, eventDetailId, currentUserId)
            logger.info("참가자 위치 데이터 조회 완료: eventId=$eventId, eventDetailId=$eventDetailId, 실제 위치 데이터 수=${participantLocations.size}, 지도 마커 데이터 수=${mapMarkerData.size}")
            return mapMarkerData
        } catch (e: Exception) {
            logger.error("참가자 위치 데이터 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            return emptyList()
        }
    }
    
    /**
     * 상위 랭커 정보 조회
     * 
     * leaderboard:{eventDetailId} Sorted Set에서 상위 N명(예: 3명)의 userId를 조회하고,
     * 각 userId에 대해 사용자/대회 참가자 데이터베이스에서 name, bibNumber, profileImageUrl,
     * cumulativeDistance, cumulativeTime 등의 상세 정보를 추가 조회하여 결합합니다.
     * 
     * @param eventDetailId 이벤트 상세 ID
     * @return 상위 랭커 리스트
     */
    private fun getTopRankers(eventDetailId: Long): List<TopRankerDto> {
        logger.info("상위 랭커 조회 시작: eventDetailId=$eventDetailId")
        try {
            // 1. Redis Sorted Set에서 상위 3명의 userId 조회
            val topRankersWithScores = leaderboardService.getTopRankersWithScores(eventDetailId.toString())
            if (topRankersWithScores.isEmpty()) {
                logger.info("상위 랭커 없음: eventDetailId=$eventDetailId")
                return emptyList()
            }

            // 2. userId 목록 추출
            val userIdStrings = topRankersWithScores.map { it.first }
            // val userIds = userIdStrings.mapNotNull { it }

            // 테스트용 하드코딩 유저 데이터
            val testUsers = listOf(
                mapOf("userId" to 1L, "name" to "김러너", "bibNumber" to "A001", "profileImageUrl" to "https://api.dicebear.com/7.x/avataaars/svg?seed=1"),
                mapOf("userId" to 2L, "name" to "이스피드", "bibNumber" to "A002", "profileImageUrl" to "https://api.dicebear.com/7.x/avataaars/svg?seed=2"),
                mapOf("userId" to 3L, "name" to "박마라톤", "bibNumber" to "A003", "profileImageUrl" to "https://api.dicebear.com/7.x/avataaars/svg?seed=3"),
                mapOf("userId" to 4L, "name" to "최러닝", "bibNumber" to "A004", "profileImageUrl" to "https://api.dicebear.com/7.x/avataaars/svg?seed=4"),
                mapOf("userId" to 5L, "name" to "정트랙", "bibNumber" to "A005", "profileImageUrl" to "https://api.dicebear.com/7.x/avataaars/svg?seed=5")
            )
            val userMap = testUsers.associateBy { it["userId"] }
            val participantMap = userMap // bibNumber 등도 동일하게 사용

            // 3. 사용자 정보 일괄 조회 (DB 접근 주석처리)
            // val users = userRepository.findByUserIdIn(userIds)
            // val userMap = users.associateBy { it.userId }
            // val eventParticipants = eventParticipantRepository.findByUserIdInAndEventId(userIds, eventDetailId)
            // val participantMap = eventParticipants.associateBy { it.userId }

            // 4. 상위 랭커 정보 구성
            val topRankers = topRankersWithScores.mapIndexedNotNull { index, (userId, score) ->
                val user = userMap[userId]
                val participant = participantMap[userId]
                if (user == null) {
                    logger.warn("사용자 정보 없음: userId=$userId, eventDetailId=$eventDetailId")
                    return@mapIndexedNotNull null
                }
                // 점수에서 체크포인트 정보 추출
                val (checkpointIndex, cumulativeTime) = leaderboardService.extractCheckpointInfoFromScore(score)
                // 참가자 위치 정보 조회
                val location = getParticipantLocation(userId, eventDetailId)
                TopRankerDto(
                    rank = index + 1,
                    userId = userId,
                    name = user["name"] as String,
                    bibNumber = user["bibNumber"] as String,
                    profileImageUrl = user["profileImageUrl"] as String?,
                    farthestCpId = getFarthestCheckpointId(userId.toString(), eventDetailId),
                    farthestCpIndex = checkpointIndex,
                    cumulativeTimeAtFarthestCp = formatTime(cumulativeTime),
                    cumulativeDistance = location?.distanceCovered,
                    averageSpeed = calculateAverageSpeed(location?.distanceCovered, cumulativeTime),
                    isFinished = checkpointIndex >= getMaxCheckpointIndex(eventDetailId)
                )
            }
            logger.info("상위 랭커 조회 완료: eventDetailId=$eventDetailId, 랭커 수=${topRankers.size}, 사용자 정보 조회=${userMap.size}명, 참가자 정보 조회=${participantMap.size}명")
            return topRankers
        } catch (e: Exception) {
            logger.error("상위 랭커 조회 실패: eventDetailId=$eventDetailId", e)
            return emptyList()
        }
    }
    
    /**
     * 통합 대상 참가자 실시간 위치 데이터 조회
     * 
     * 통합된 userId 목록에 있는 각 참가자에 대해 location:{userId}:{eventDetailId} 키를 사용하여 
     * Redis에서 해당 참가자들의 location 데이터를 조회합니다.
     * 
     * Redis Key 패턴: location:{userId}:{eventDetailId}
     * Type: Hash
     * Fields: userId, correctedLatitude, correctedLongitude, correctedAltitude, heading, distanceCovered, cumulativeTime
     * 
     * @param userIds 통합된 사용자 ID 목록
     * @param eventDetailId 이벤트 상세 ID
     * @return 참가자 위치 정보 리스트
     */
    private fun getBatchParticipantLocations(userIds: List<Long>, eventDetailId: Long): List<EventParticipantLocationDto> {
        logger.info("통합 대상 참가자 실시간 위치 데이터 조회 시작: eventDetailId=$eventDetailId, " +
            "대상 사용자 수=${userIds.size}, userIds=$userIds")
        
        val participantLocations = mutableListOf<EventParticipantLocationDto>()
        var successCount = 0
        var failureCount = 0
        val failedUserIds = mutableListOf<Long>()
        
        userIds.forEach { userId ->
            try {
                val redisUserId = userId.toString()
                val redisEventDetailId = eventDetailId.toString()
                // Redis Key 생성: location:{userId}:{eventDetailId}
                val key = "$LOCATION_KEY_PREFIX:${redisUserId}:$redisEventDetailId"
                
                // Redis Hash에서 위치 데이터 조회
                val locationData = getParticipantLocationFromRedis(key, userId, eventDetailId)
                
                if (locationData != null) {
                    participantLocations.add(locationData)
                    successCount++
                    logger.debug("참가자 위치 조회 성공: userId=$userId, eventDetailId=$eventDetailId")
                } else {
                    failureCount++
                    failedUserIds.add(userId)
                    logger.warn("참가자 위치 데이터 없음: userId=$userId, eventDetailId=$eventDetailId, key=$key")
                }
                
            } catch (e: Exception) {
                failureCount++
                failedUserIds.add(userId)
                logger.error("참가자 위치 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            }
        }
        
        logger.info("통합 대상 참가자 실시간 위치 데이터 조회 완료: eventDetailId=$eventDetailId, " +
            "대상 사용자 수=${userIds.size}, 성공=$successCount, 실패=$failureCount, " +
            "실제 조회된 위치 수=${participantLocations.size}")
        
        if (failedUserIds.isNotEmpty()) {
            logger.warn("위치 조회 실패한 사용자들: eventDetailId=$eventDetailId, failedUserIds=$failedUserIds")
        }
        
        return participantLocations
    }
    
    /**
     * 특정 참가자의 위치 정보 조회 (단일 조회용)
     * 
     * @param userId 사용자 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 참가자 위치 정보
     */
    private fun getParticipantLocation(userId: Long, eventDetailId: Long): EventParticipantLocationDto? {
        try {
            val key = "$LOCATION_KEY_PREFIX:$userId:$eventDetailId"
            return getParticipantLocationFromRedis(key, userId, eventDetailId)
            
        } catch (e: Exception) {
            logger.warn("참가자 위치 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            return null
        }
    }
    
    /**
     * Redis Hash에서 참가자 위치 데이터 조회 및 필드 추출
     * 
     * location:{userId}:{eventDetailId} 키를 사용하여 Redis Hash에서 위치 데이터를 조회하고,
     * 각 데이터에서 userId, correctedLatitude, correctedLongitude, correctedAltitude, 
     * heading, distanceCovered, cumulativeTime 등의 필드를 추출합니다.
     * 
     * @param key Redis 키 (location:{userId}:{eventDetailId})
     * @param userId 사용자 ID (로깅용)
     * @param eventDetailId 이벤트 상세 ID (로깅용)
     * @return 참가자 위치 DTO (실패 시 null)
     */
    private fun getParticipantLocationFromRedis(key: String, userId: Long, eventDetailId: Long): EventParticipantLocationDto? {
        try {
            logger.debug("Redis Hash 위치 데이터 조회 시작: key=$key, userId=$userId, eventDetailId=$eventDetailId")
            
            val hashOps = redisTemplate.opsForHash<String, Any>()
            val allFields = hashOps.entries(key)
            allFields.forEach {
                logger.debug("Redis Hash 데이터 : key=${it.key}, value=${it.value}")
            }
            if (allFields.isEmpty()) {
                logger.debug("Redis Hash 데이터 없음: key=$key, userId=$userId, eventDetailId=$eventDetailId")
                return null
            }


            logger.debug("Redis Hash 필드 수: key=$key, userId=$userId, 필드 수=${allFields.size}, " +
                "필드 목록=${allFields.keys}")
            
            // 필수 필드 추출 및 검증 (Any 타입을 안전하게 String으로 변환)
            val extractedUserId = allFields["userId"]
            logger.debug("Redis Hash 필드 수: key=$key, userId=$extractedUserId, 필드 수=${allFields.size}, " +
                    "필드 목록=${allFields.keys}")
            val userIdLong = when (extractedUserId) {
                is Long -> extractedUserId
                is Int -> extractedUserId.toLong()
                is String -> extractedUserId.toLongOrNull()
                else -> null
            }
            if (userIdLong == null) {
                logger.warn("필수 필드 누락 - userId: key=$key, userId=$userId, extractedUserId=$extractedUserId")
                return null
            }
            
            val correctedLatitude = allFields["correctedLatitude"]?.toString()?.toDoubleOrNull()
            if (correctedLatitude == null) {
                logger.warn("필수 필드 누락/파싱 실패 - correctedLatitude: key=$key, userId=$userId, " +
                    "rawValue=${allFields["correctedLatitude"]}, type=${allFields["correctedLatitude"]?.javaClass?.simpleName}")
                return null
            }
            
            val correctedLongitude = allFields["correctedLongitude"]?.toString()?.toDoubleOrNull()
            if (correctedLongitude == null) {
                logger.warn("필수 필드 누락/파싱 실패 - correctedLongitude: key=$key, userId=$userId, " +
                    "rawValue=${allFields["correctedLongitude"]}, type=${allFields["correctedLongitude"]?.javaClass?.simpleName}")
                return null
            }
            
            // 선택적 필드 추출 (Any 타입을 안전하게 변환)
            val correctedAltitude = allFields["correctedAltitude"]?.toString()?.toDoubleOrNull()
            val heading = allFields["heading"]?.toString()?.toDoubleOrNull()
            val distanceCovered = allFields["distanceCovered"]?.toString()?.toDoubleOrNull()
            val cumulativeTimeSeconds = allFields["cumulativeTime"]?.toString()?.toLongOrNull()
            val cumulativeTime = cumulativeTimeSeconds?.let { formatTime(it) }
            
            logger.debug("필드 추출 완료: key=$key, userId=$userId, " +
                "correctedLatitude=$correctedLatitude, correctedLongitude=$correctedLongitude, " +
                "correctedAltitude=$correctedAltitude, heading=$heading, " +
                "distanceCovered=$distanceCovered, cumulativeTime=$cumulativeTime")
            
            return EventParticipantLocationDto(
                userId = userIdLong ?: 0L,
                correctedLatitude = correctedLatitude,
                correctedLongitude = correctedLongitude,
                correctedAltitude = correctedAltitude,
                heading = heading,
                distanceCovered = distanceCovered,
                cumulativeTime = cumulativeTime
            )
            
        } catch (e: Exception) {
            logger.error("Redis 위치 데이터 파싱 실패: key=$key, userId=$userId, eventDetailId=$eventDetailId", e)
            return null
        }
    }
    
    /**
     * Redis에서 위치 데이터 조회 (기존 메서드, 하위 호환성 유지)
     * 
     * @param key Redis 키
     * @return 참가자 위치 DTO
     */
    private fun getLocationFromRedis(key: String): EventParticipantLocationDto? {
        try {
            val hashOps = redisTemplate.opsForHash<String, Any>()
            val allFields = hashOps.entries(key)
            
            if (allFields.isEmpty()) return null
            
            val userId = allFields["userId"]?.toString()?.toLongOrNull() ?: return null
            val correctedLatitude = allFields["correctedLatitude"]?.toString()?.toDoubleOrNull() ?: return null
            val correctedLongitude = allFields["correctedLongitude"]?.toString()?.toDoubleOrNull() ?: return null
            
            return EventParticipantLocationDto(
                userId = userId,
                correctedLatitude = correctedLatitude,
                correctedLongitude = correctedLongitude,
                correctedAltitude = allFields["correctedAltitude"]?.toString()?.toDoubleOrNull(),
                heading = allFields["heading"]?.toString()?.toDoubleOrNull(),
                distanceCovered = allFields["distanceCovered"]?.toString()?.toDoubleOrNull(),
                cumulativeTime = allFields["cumulativeTime"]?.toString()?.toLongOrNull()?.let { formatTime(it) }
            )
            
        } catch (e: Exception) {
            logger.warn("Redis 위치 데이터 파싱 실패: key=$key", e)
            return null
        }
    }
    
    /**
     * 참가자 이름 생성 (임시)
     * TODO: 실제 사용자 정보에서 이름 조회
     */
    private fun generateParticipantName(userId: String): String {
        return "참가자_${userId.takeLast(4)}"
    }
    
    /**
     * 참가자 번호 생성 (임시)
     * TODO: 실제 참가자 번호 조회
     */
    private fun generateBibNumber(userId: String): String {
        return "B${userId.hashCode().toString().takeLast(4)}"
    }
    
    /**
     * 프로필 이미지 URL 생성 (임시)
     * TODO: 실제 프로필 이미지 URL 조회
     */
    private fun generateProfileImageUrl(userId: String): String? {
        return "https://api.dicebear.com/7.x/avataaars/svg?seed=$userId"
    }
    
    /**
     * 가장 최근 체크포인트 ID 조회
     * TODO: 실제 체크포인트 정보 조회
     */
    private fun getFarthestCheckpointId(userId: String, eventDetailId: Long): String? {
        try {
            val key = "$LOCATION_KEY_PREFIX:$userId:$eventDetailId"
            val hashOps = redisTemplate.opsForHash<String, Any>()
            return hashOps.get(key, "farthestCpId")?.toString()
        } catch (e: Exception) {
            logger.warn("가장 최근 체크포인트 ID 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
            return null
        }
    }
    
    /**
     * 최대 체크포인트 인덱스 조회
     * TODO: 실제 코스 체크포인트 정보에서 조회
     */
    private fun getMaxCheckpointIndex(eventDetailId: Long): Int {
        // 임시로 5개 체크포인트 가정
        return 5
    }
    
    /**
     * 평균 속도 계산
     * 
     * @param distance 거리 (미터)
     * @param time 시간 (초)
     * @return 평균 속도 (m/min)
     */
    private fun calculateAverageSpeed(distance: Double?, time: Long): Double? {
        return if (distance != null && time > 0) {
            distance / (time / 60.0) // m/min
        } else {
            null
        }
    }
    
    /**
     * 시간 포맷팅 (초를 HH:mm:ss 형식으로 변환)
     * 
     * @param seconds 초
     * @return 포맷된 시간 문자열
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
    
    /**
     * 코스 정보 및 GPX 파일 URL 조회
     * 
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID (코스 ID)
     * @return 코스 정보
     */
    private fun getCourseInfoAndGpxUrl(eventId: Long, eventDetailId: Long): com.sponovation.runtrack.domain.EventDetail {
        try {
            // 방법 1: eventDetailId를 직접 EventDetail ID로 사용하여 조회 시도
            val directCourse = courseRepository.findById(eventDetailId)
            
            if (directCourse.isPresent) {
                logger.info("직접 코스 조회 성공: courseId=$eventDetailId")
                return directCourse.get()
            }
            
            // 방법 2: Event에 속한 코스들 중에서 조회
            val courses = courseRepository.findByEventIdOrderByDistanceAsc(eventId)
            
            if (courses.isNotEmpty()) {
                // 첫 번째 코스를 기본 코스로 사용 (현재는 단일 코스만 지원)
                val course = courses.first()
                logger.info("이벤트 기반 코스 조회 성공: eventId=$eventId, courseCount=${courses.size}")
                return course
            }
            
            // 방법 3: eventDetailId를 이용한 대체 조회 방법 (향후 확장)
            // TODO: eventDetailId와 EventDetail 간의 명확한 매핑 테이블 구현 시 사용
            
            throw IllegalArgumentException("해당 대회의 코스 정보를 찾을 수 없습니다: eventId=$eventId, eventDetailId=$eventDetailId")
            
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            logger.error("코스 정보 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            throw IllegalArgumentException("코스 정보 조회 중 오류가 발생했습니다: ${e.message}")
        }
    }
    
    /**
     * GPX 파일 URL 유효성 검증 및 포맷팅
     * 
     * @param gpxFileUrl 원본 GPX 파일 URL
     * @param eventDetailId 이벤트 상세 ID (로깅용)
     * @return 유효성 검증된 GPX 파일 URL
     */
    private fun validateAndFormatGpxUrl(gpxFileUrl: String, eventDetailId: Long): String {
        try {
            // URL 형식 검증
            if (gpxFileUrl.isBlank()) {
                logger.warn("GPX 파일 URL이 비어있음: eventDetailId=$eventDetailId")
                throw IllegalArgumentException("GPX 파일 URL이 설정되지 않았습니다")
            }
            
            // S3 URL 형식 확인
            if (gpxFileUrl.startsWith("https://") || gpxFileUrl.startsWith("http://")) {
                logger.info("유효한 GPX 파일 URL: $gpxFileUrl")
                return gpxFileUrl
            }
            
            // 상대 경로인 경우 S3 전체 URL 생성
            if (!gpxFileUrl.startsWith("http")) {
                val fullUrl = generateS3Url(gpxFileUrl)
                logger.info("GPX 파일 URL 생성: $gpxFileUrl -> $fullUrl")
                return fullUrl
            }
            
            return gpxFileUrl
            
        } catch (e: Exception) {
            logger.error("GPX 파일 URL 검증 실패: eventDetailId=$eventDetailId, url=$gpxFileUrl", e)
            throw IllegalArgumentException("GPX 파일 URL이 유효하지 않습니다: ${e.message}")
        }
    }
    
    /**
     * 지도 마커를 위한 데이터 구성
     * 
     * 조회된 참가자들의 보정된 위치 데이터를 기반으로, 클라이언트의 지도에 마커로 표시할 데이터를 구성합니다.
     * distanceCovered 값을 기준으로 참가자의 상대적인 위치를 나타내며, 마커 표시 우선순위를 적용합니다.
     * 
     * 마커 우선순위:
     * 1. 상위 3명 참가자 (leaderboard 기준) - 가장 높은 우선순위
     * 2. 현재 로그인 사용자 - 중간 우선순위
     * 3. 트래커 목록 사용자들 - 낮은 우선순위
     * 
     * 정렬 기준:
     * - distanceCovered 내림차순 (가장 멀리 진행한 참가자 우선)
     * - 같은 거리일 경우 마커 우선순위 기준
     * 
     * @param participantLocations 조회된 참가자 위치 데이터 목록
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param currentUserId 현재 로그인한 사용자 ID
     * @return 지도 마커용으로 구성된 참가자 위치 데이터 목록
     */
    private fun buildMapMarkerData(
        participantLocations: List<EventParticipantLocationDto>,
        eventId: Long,
        eventDetailId: Long,
        currentUserId: Long?
    ): List<EventParticipantLocationDto> {
        logger.info("지도 마커 데이터 구성 시작: eventId=$eventId, eventDetailId=$eventDetailId, " +
            "참가자 수=${participantLocations.size}, currentUserId=$currentUserId")
        
        try {
            if (participantLocations.isEmpty()) {
                logger.info("참가자 위치 데이터가 비어있음: eventId=$eventId, eventDetailId=$eventDetailId")
                return emptyList()
            }
            
            // 1. 마커 우선순위 분류를 위한 사용자 그룹 조회
            val top3UserIds = leaderboardService.getTopRankers(eventDetailId).toSet()
            val trackedUserIds = currentUserId?.let { getTrackedUserIds(it.toString(), eventId) }?.toSet() ?: emptySet()
            
            logger.info("마커 우선순위 분류: eventDetailId=$eventDetailId, " +
                "상위 3명=${top3UserIds.size}명 $top3UserIds, " +
                "트래킹 목록=${trackedUserIds.size}명 $trackedUserIds, " +
                "현재 사용자=$currentUserId")
            
            // 2. distanceCovered 기준 상대적 위치 분석
            val distanceCoveredStats = analyzeDistanceCoveredStats(participantLocations)
            
            // 3. 지도 마커용 데이터 정렬 및 구성
            val sortedMarkerData = participantLocations.sortedWith { a, b ->
                // 첫 번째 기준: distanceCovered 내림차순 (멀리 진행한 참가자 우선)
                val distanceComparison = compareValues(b.distanceCovered, a.distanceCovered)
                if (distanceComparison != 0) return@sortedWith distanceComparison
                
                // 두 번째 기준: 마커 우선순위 (상위 랭커 > 현재 사용자 > 트래킹 목록)
                val aPriority = getMarkerPriority(a.userId, top3UserIds, currentUserId, trackedUserIds)
                val bPriority = getMarkerPriority(b.userId, top3UserIds, currentUserId, trackedUserIds)
                compareValues(aPriority, bPriority)
            }
            
            logger.info("지도 마커 데이터 구성 완료: eventId=$eventId, eventDetailId=$eventDetailId, " +
                "원본 참가자 수=${participantLocations.size}, 구성된 마커 수=${sortedMarkerData.size}, " +
                "거리 통계=$distanceCoveredStats")
            
            // 4. 마커 데이터 상세 로깅 (DEBUG 레벨)
            sortedMarkerData.forEachIndexed { index, marker ->
                val markerType = getMarkerType(marker.userId, top3UserIds, currentUserId, trackedUserIds)
                val priority = getMarkerPriority(marker.userId, top3UserIds, currentUserId, trackedUserIds)
                
                logger.debug("마커 ${index + 1}: userId=${marker.userId}, " +
                    "distanceCovered=${marker.distanceCovered}m, " +
                    "좌표=(${marker.correctedLatitude}, ${marker.correctedLongitude}), " +
                    "마커타입=$markerType, 우선순위=$priority")
            }
            
            return sortedMarkerData
            
        } catch (e: Exception) {
            logger.error("지도 마커 데이터 구성 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            // 실패 시 원본 데이터를 distanceCovered 기준으로만 정렬해서 반환
            return participantLocations.sortedByDescending { it.distanceCovered ?: 0.0 }
        }
    }
    
    /**
     * distanceCovered 통계 분석
     * 
     * @param participantLocations 참가자 위치 데이터 목록
     * @return 거리 통계 정보
     */
    private fun analyzeDistanceCoveredStats(participantLocations: List<EventParticipantLocationDto>): String {
        val distances = participantLocations.mapNotNull { it.distanceCovered }
        
        if (distances.isEmpty()) return "거리 데이터 없음"
        
        val maxDistance = distances.maxOrNull() ?: 0.0
        val minDistance = distances.minOrNull() ?: 0.0
        val avgDistance = distances.average()
        
        return "최대=${maxDistance}m, 최소=${minDistance}m, 평균=${"%.1f".format(avgDistance)}m"
    }
    
    /**
     * 마커 우선순위 계산
     * 
     * @param userId 사용자 ID
     * @param top3UserIds 상위 3명 사용자 ID 집합
     * @param currentUserId 현재 로그인한 사용자 ID
     * @param trackedUserIds 트래킹 목록 사용자 ID 집합
     * @return 우선순위 (낮을수록 높은 우선순위)
     */
    private fun getMarkerPriority(
        userId: Long,
        top3UserIds: Set<Long>,
        currentUserId: Long?,
        trackedUserIds: Set<String>
    ): Long {
        return when {
            userId in top3UserIds -> 1 // 최고 우선순위: 상위 3명
            userId == currentUserId -> 2 // 중간 우선순위: 현재 사용자
            userId.toString() in trackedUserIds -> 3 // 낮은 우선순위: 트래킹 목록
            else -> 4 // 최저 우선순위: 기타
        }
    }
    
    /**
     * 마커 타입 결정
     * 
     * @param userId 사용자 ID
     * @param top3UserIds 상위 3명 사용자 ID 집합
     * @param currentUserId 현재 로그인한 사용자 ID
     * @param trackedUserIds 트래킹 목록 사용자 ID 집합
     * @return 마커 타입 문자열
     */
    private fun getMarkerType(
        userId: Long,
        top3UserIds: Set<Long>,
        currentUserId: Long?,
        trackedUserIds: Set<String>
    ): String {
        return when {
            userId in top3UserIds -> "TOP_RANKER"
            userId == currentUserId -> "CURRENT_USER"
            userId.toString() in trackedUserIds -> "TRACKED_USER"
            else -> "OTHER"
        }
    }
    
    /**
     * 위치 조회 대상 userId 통합
     * 
     * 상위 3명의 userId와 트래커 목록의 userId를 통합하여 중복을 제거한 최종 위치 조회 대상 userId 목록을 구성합니다.
     * 
     * 통합 순서:
     * 1. 상위 3명 참가자 (leaderboard 기준)
     * 2. 현재 로그인 사용자 (상위 3명에 포함되지 않은 경우)
     * 3. 트래커 목록 사용자들 (중복 제거)
     * 
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param currentUserId 현재 로그인한 사용자 ID
     * @return 중복이 제거된 최종 위치 조회 대상 userId 목록
     */
    private fun getTargetUserIds(eventId: Long, eventDetailId: Long, currentUserId: String?): List<String> {
        logger.info("위치 조회 대상 userId 통합 시작: eventId=$eventId, eventDetailId=$eventDetailId, currentUserId=$currentUserId")
        
        try {
            val targetUserIds = mutableSetOf<String>() // Set을 사용하여 중복 자동 제거
            
            // 1. 상위 3명 참가자 추가 (leaderboard:{eventDetailId} Redis Sorted Set에서 조회)
            val top3UserIds = leaderboardService.getTopRankers(eventDetailId)
            targetUserIds.addAll(top3UserIds.map { it.toString() })
            logger.info("상위 3명 userId 추가 완료: eventDetailId=$eventDetailId, userIds=$top3UserIds")
            
            // 2. 현재 로그인 사용자 추가 (null이 아닌 경우)
            currentUserId?.let { userId ->
                targetUserIds.add(userId)
                logger.info("현재 사용자 추가 완료: currentUserId=$userId")
            }
            
            // 3. 트래커 목록 사용자들 추가
            currentUserId?.let { userId ->
                val trackedUserIds = getTrackedUserIds(userId, eventId)
                targetUserIds.addAll(trackedUserIds)
                logger.info("트래커 목록 사용자 추가 완료: currentUserId=$userId, eventId=$eventId, " +
                    "trackedUsers=$trackedUserIds")
            }
            
            val finalUserIds = targetUserIds.toList()
            logger.info("위치 조회 대상 userId 통합 완료: eventId=$eventId, eventDetailId=$eventDetailId, " +
                "최종 대상 사용자 수=${finalUserIds.size}, finalUserIds=$finalUserIds")
            
            return finalUserIds
            
        } catch (e: Exception) {
            logger.error("위치 조회 대상 userId 통합 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            return emptyList()
        }
    }
    
    /**
     * 트래킹 목록 userId 조회
     * 
     * userTrackers:{userId}:{eventId} Redis Set에서 현재 로그인 유저가 추적 중인 participantId (즉, userId) 목록을 조회합니다.
     * 
     * Redis Key 패턴: userTrackers:{userId}:{eventId}
     * Type: Set
     * Example: userTrackers:user001:eventA → Set{"participant123", "participant456", "participant789"}
     * 
     * @param currentUserId 현재 로그인한 사용자 ID
     * @param eventId 이벤트 ID
     * @return 추적 중인 참가자 ID 목록
     */
    private fun getTrackedUserIds(currentUserId: String, eventId: Long): List<String> {
        val key = "$USER_TRACKERS_KEY_PREFIX$KEY_SEPARATOR$currentUserId${KEY_SEPARATOR}event$eventId"
        
        return try {
            logger.info("트래킹 목록 조회 시작: currentUserId=$currentUserId, eventId=$eventId, key=$key")
            
            // Redis Set에서 추적 중인 참가자 ID 목록 조회
            val setOps = redisTemplate.opsForSet()
            val trackedUserIds = setOps.members(key)?.map { it.toString() } ?: emptyList()
            
            logger.info("트래킹 목록 조회 완료: currentUserId=$currentUserId, eventId=$eventId, " +
                "추적 중인 참가자 수=${trackedUserIds.size}, trackedUserIds=$trackedUserIds")
            
            trackedUserIds
            
        } catch (e: Exception) {
            logger.error("트래킹 목록 조회 실패: currentUserId=$currentUserId, eventId=$eventId, key=$key", e)
            emptyList()
        }
    }
    
    /**
     * S3 URL 생성
     * 
     * @param fileName 파일명 또는 상대 경로
     * @return 완전한 S3 URL
     */
    private fun generateS3Url(fileName: String): String {
        // 파일명에서 불필요한 슬래시 제거
        val cleanFileName = fileName.removePrefix("/").removePrefix("\\")
        return "https://$bucketName.s3.$s3Region.amazonaws.com/$cleanFileName"
    }
} 