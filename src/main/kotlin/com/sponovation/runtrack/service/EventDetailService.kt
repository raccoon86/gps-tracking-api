package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.EventDetail
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.repository.EventRepository
import com.sponovation.runtrack.repository.EventDetailRepository
import com.sponovation.runtrack.repository.ParticipantRepository
import com.sponovation.runtrack.repository.TrackerRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 대회 상세 조회 서비스
 */
@Service
class EventDetailService(
    private val eventRepository: EventRepository,
    private val eventDetailRepository: EventDetailRepository,
    private val participantRepository: ParticipantRepository,
    private val trackerRepository: TrackerRepository,
    private val trackerService: TrackerService,
    private val leaderboardService: LeaderboardService,
    private val redisTemplate: RedisTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(EventDetailService::class.java)

    @Value("\${aws.s3.bucket-name:runtrack-gpx-files}")
    private lateinit var bucketName: String

    @Value("\${aws.s3.region:ap-northeast-2}")
    private lateinit var s3Region: String

    companion object {
        private const val KEY_PREFIX = "gps"
    }

    /**
     * 대회 상세 정보 조회
     */
    fun getEventDetail(
        eventId: Long,
        eventDetailId: Long
    ): EventDetailResponseDto {
        logger.info("대회 상세 조회 시작: eventId=$eventId, eventDetailId=$eventDetailId")

        try {
            // 대회 정보 조회 및 유효성 검증
            val event = eventRepository.findById(eventId)
                .orElseThrow { IllegalArgumentException("해당 대회를 찾을 수 없습니다: $eventId") }

            // 코스 정보 조회
            val courseInfo = getCourseInfo(eventId, eventDetailId)

            // 코스 카테고리 정보 생성
            val courseCategory = listOf(
                CourseCategoryDto(
                    course = courseInfo.distance,
                    eventDetailId = courseInfo.id
                )
            )

            return EventDetailResponseDto(
                eventId = eventId,
                eventDetailId = eventDetailId,
                name = event.name ?: "",
                courseCategory = courseCategory,
            )

        } catch (e: Exception) {
            logger.error("대회 상세 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            throw e
        }
    }

    /**
     * 대회 현장 상세 정보 조회
     */
    fun getEventVenueDetail(
        eventId: Long,
        eventDetailId: Long,
        currentUserId: Long?
    ): EventVenueDetailResponseDto {
        logger.info("대회 현장 상세 조회 시작: eventId=$eventId, eventDetailId=$eventDetailId")

        try {
            // 참가자 위치 데이터 조회
            val participantsLocations = getParticipantsLocations(eventId, eventDetailId, currentUserId)

            // 상위 랭커 정보 조회
            val topRankers = getTopRankers(eventId, eventDetailId)

            return EventVenueDetailResponseDto(
                participantsLocations = participantsLocations,
                topRankers = topRankers
            )

        } catch (e: Exception) {
            logger.error("대회 현장 상세 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            throw e
        }
    }

    /**
     * 이벤트 및 코스 정보 조회
     */
    fun getEventAndCourseInfo(eventId: Long): EventInfoResponseDto {
        logger.info("이벤트 및 코스 정보 조회 요청: eventId=$eventId")
        
        // 이벤트 조회
        val event = eventRepository.findById(eventId)
            .orElseThrow { IllegalArgumentException("이벤트를 찾을 수 없습니다: eventId=$eventId") }
        
        // 이벤트 상세 정보 조회 (거리 순으로 정렬)
        val eventDetails = eventDetailRepository.findByEventIdOrderByDistanceAsc(eventId)
        
        // 이벤트 DTO 생성
        val eventDto = EventInfoDto(
            id = event.id,
            name = event.name ?: "",
            sports = event.sports,
            startDateTime = event.startDateTime,
            endDateTime = event.endDateTime,
            country = event.country ?: "KR",
            city = event.city,
            address = event.address,
            place = event.place,
            latitude = event.latitude?.toDouble(),
            longitude = event.longitude?.toDouble(),
            thumbnail = event.thumbnail,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt
        )
        
        // 이벤트 상세 DTO 목록 생성
        val eventDetailsList = eventDetails.map { eventDetail ->
            EventDetailInfoDto(
                id = eventDetail.id,
                eventId = eventDetail.eventId,
                distance = eventDetail.distance,
                course = eventDetail.course,
                gpxFile = eventDetail.gpxFile,
                createdAt = eventDetail.createdAt,
                updatedAt = eventDetail.updatedAt
            )
        }
        
        logger.info("이벤트 및 코스 정보 조회 완료: eventId=$eventId, 코스 수=${eventDetailsList.size}")
        
        return EventInfoResponseDto(
            event = eventDto,
            eventDetails = eventDetailsList
        )
    }
    
    /**
     * 테스트용 EventDetail 생성
     */
    @Transactional
    fun createTestCourse(request: CreateTestCourseRequestDto): CreateTestCourseResponseDto {
        logger.info("테스트 EventDetail 생성 시작: eventId=${request.eventId}, courseName=${request.courseName}")
        
        try {
            // Event 존재 여부 확인
            if (!eventRepository.existsById(request.eventId)) {
                throw IllegalArgumentException("Event를 찾을 수 없습니다: eventId=${request.eventId}")
            }
            
            // 기본값 설정
            val startDateTime = request.startDateTime ?: LocalDateTime.now().plusDays(1)
            val endDateTime = request.endDateTime ?: startDateTime.plusHours(6)
            val gpxFile = request.gpxFile ?: "test://gpx/test_route.gpx"
            
            // EventDetail 엔티티 생성
            val eventDetail = EventDetail(
                eventId = request.eventId,
                distance = request.distance,
                course = request.courseName,
                gpxFile = gpxFile,
                startDateTime = startDateTime,
                endDateTime = endDateTime
            )
            
            val savedEventDetail = eventDetailRepository.save(eventDetail)
            
            logger.info("테스트 EventDetail 생성 완료: eventDetailId=${savedEventDetail.id}")
            
            return CreateTestCourseResponseDto(
                eventDetailId = savedEventDetail.id!!,
                eventId = savedEventDetail.eventId,
                distance = savedEventDetail.distance,
                course = savedEventDetail.course ?: "",
                gpxFile = savedEventDetail.gpxFile,
                startDateTime = savedEventDetail.startDateTime?.toString(),
                endDateTime = savedEventDetail.endDateTime?.toString(),
                createdAt = savedEventDetail.createdAt.toString(),
                message = "새 이벤트 상세 생성 완료",
                isNewlyCreated = true
            )
            
        } catch (e: Exception) {
            logger.error("테스트 EventDetail 생성 실패: eventId=${request.eventId}", e)
            throw e
        }
    }
    
    /**
     * EventDetail 존재 여부 확인
     */
    @Transactional(readOnly = true)
    fun existsById(eventDetailId: Long): Boolean {
        return eventDetailRepository.existsById(eventDetailId)
    }

    // ========== Private Helper Methods ==========

    /**
     * 코스 정보 조회
     */
    private fun getCourseInfo(eventId: Long, eventDetailId: Long): EventDetail {
        try {
            // eventDetailId로 직접 조회 시도
            val directCourse = eventDetailRepository.findById(eventDetailId)
            if (directCourse.isPresent) {
                return directCourse.get()
            }

            // Event에 속한 코스들 중에서 조회
            val courses = eventDetailRepository.findByEventIdOrderByDistanceAsc(eventId)
            if (courses.isNotEmpty()) {
                return courses.first()
            }

            throw IllegalArgumentException("해당 대회의 코스 정보를 찾을 수 없습니다: eventId=$eventId, eventDetailId=$eventDetailId")

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            logger.error("코스 정보 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            throw IllegalArgumentException("코스 정보 조회 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 참가자 위치 데이터 조회
     */
    private fun getParticipantsLocations(
        eventId: Long,
        eventDetailId: Long,
        currentUserId: Long?
    ): List<EventParticipantLocationDto> {
        logger.info("참가자 위치 데이터 조회 시작: eventId=$eventId, eventDetailId=$eventDetailId")
        
        try {
            // 1. 리더보드 상위 3명 유저 ID 조회
            val top3UserIds = leaderboardService.getTopRankers(eventId, eventDetailId)
            logger.info("리더보드 상위 3명 조회: $top3UserIds")
            
            // 2. 현재 사용자가 트래킹하는 유저 ID 조회 (/api/v1/trackers/{userId} API 호출)
            val trackedUserIds = if (currentUserId != null) {
                try {
                    val trackerList = trackerService.getTrackerList(currentUserId)
                    trackerList.participants.map { it.participantId }
                } catch (e: Exception) {
                    logger.warn("트래킹 목록 조회 실패: userId=$currentUserId", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            logger.info("트래킹 중인 유저 조회: $trackedUserIds")
            
            // 3. 중복 제거하여 전체 유저 ID 목록 생성
            val allUserIds = (top3UserIds + trackedUserIds).distinct()
            logger.info("전체 대상 유저 ID: $allUserIds")
            
            // 4. 위치 데이터 조회
            val participantLocations = getBatchParticipantLocations(allUserIds, eventId, eventDetailId)
            val mapMarkerData = buildMapMarkerData(participantLocations, eventId, eventDetailId, currentUserId)
            
            logger.info("참가자 위치 데이터 조회 완료: 실제 위치 데이터 수=${participantLocations.size}")
            return mapMarkerData
        } catch (e: Exception) {
            logger.error("참가자 위치 데이터 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            return emptyList()
        }
    }

    /**
     * 상위 랭커 정보 조회
     */
    private fun getTopRankers(eventId: Long, eventDetailId: Long): List<TopRankerDto> {
        logger.info("상위 랭커 조회 시작: eventDetailId=$eventDetailId")
        
        try {
            // Redis Sorted Set에서 상위 3명의 userId 조회
            val topRankersWithScores = leaderboardService.getTopRankersWithScores(eventId.toString(), eventDetailId.toString())
            if (topRankersWithScores.isEmpty()) {
                logger.info("상위 랭커 없음: eventDetailId=$eventDetailId")
                return emptyList()
            }

            // 상위 랭커 정보 구성 (participants 테이블에서 실제 데이터 조회)
            val topRankers = topRankersWithScores.mapIndexedNotNull { index, (userId, score) ->
                try {
                    // participants 테이블에서 참가자 정보 조회
                    val participant = participantRepository.findByEventDetailIdAndUserId(eventDetailId, userId)
                    
                    if (participant == null) {
                        logger.warn("참가자 정보 없음: userId=$userId, eventDetailId=$eventDetailId")
                        return@mapIndexedNotNull null
                    }

                    TopRankerDto(
                        rank = index + 1,
                        userId = userId,
                        name = participant.name,
                        bibNumber = participant.bibNumber ?: "N/A",
                        profileImageUrl = participant.profileImageUrl
                    )
                } catch (e: Exception) {
                    logger.warn("참가자 정보 조회 실패: userId=$userId, eventDetailId=$eventDetailId", e)
                    null
                }
            }
            
            logger.info("상위 랭커 조회 완료: eventDetailId=$eventDetailId, 랭커 수=${topRankers.size}")
            return topRankers
        } catch (e: Exception) {
            logger.error("상위 랭커 조회 실패: eventDetailId=$eventDetailId", e)
            return emptyList()
        }
    }

    /**
     * 참가자 실시간 위치 데이터 일괄 조회
     */
    private fun getBatchParticipantLocations(
        userIds: List<Long>,
        eventId: Long,
        eventDetailId: Long
    ): List<EventParticipantLocationDto> {
        logger.info("참가자 위치 일괄 조회 시작: eventDetailId=$eventDetailId, 대상 사용자 수=${userIds.size}")

        val participantLocations = mutableListOf<EventParticipantLocationDto>()
        var successCount = 0

        userIds.forEach { userId ->
            try {
                val key = "$KEY_PREFIX:${eventId}:$eventDetailId:$userId"
                val locationData = getParticipantLocationFromRedis(key, userId, eventDetailId)

                if (locationData != null) {
                    participantLocations.add(locationData)
                    successCount++
                } else {
                    logger.warn("참가자 위치 데이터 없음: userId=$userId, key=$key")
                }

            } catch (e: Exception) {
                logger.error("참가자 위치 조회 실패: userId=$userId", e)
            }
        }

        logger.info("참가자 위치 일괄 조회 완료: 성공=$successCount, 실제 조회된 위치 수=${participantLocations.size}")
        return participantLocations
    }

    /**
     * Redis Hash에서 참가자 위치 데이터 조회
     */
    private fun getParticipantLocationFromRedis(
        key: String,
        userId: Long,
        eventDetailId: Long
    ): EventParticipantLocationDto? {
        try {
            val hashOps = redisTemplate.opsForHash<String, Any>()
            val allFields = hashOps.entries(key)
            
            if (allFields.isEmpty()) {
                return null
            }

            // 필수 필드 추출 및 검증
            val extractedUserId = when (val rawUserId = allFields["userId"]) {
                is Long -> rawUserId
                is Int -> rawUserId.toLong()
                is String -> rawUserId.toLongOrNull()
                else -> null
            }
            
            if (extractedUserId == null) {
                logger.warn("필수 필드 누락 - userId: key=$key")
                return null
            }

            val correctedLatitude = allFields["latitude"]?.toString()?.toDoubleOrNull()
            val correctedLongitude = allFields["longitude"]?.toString()?.toDoubleOrNull()
            
            if (correctedLatitude == null || correctedLongitude == null) {
                logger.warn("필수 위치 필드 누락: key=$key")
                return null
            }

            // 선택적 필드 추출
            val correctedAltitude = allFields["altitude"]?.toString()?.toDoubleOrNull()
            val rawSpeed = allFields["speed"]?.toString()?.takeIf { it != "null" }?.toFloatOrNull()

            // 참가자 정보 조회 (participants 테이블에서)
            val participant = try {
                participantRepository.findByEventDetailIdAndUserId(eventDetailId, extractedUserId)
            } catch (e: Exception) {
                logger.warn("참가자 정보 조회 실패: userId=$extractedUserId", e)
                null
            }

            return EventParticipantLocationDto(
                userId = extractedUserId,
                name = participant?.name ?: "참가자_${extractedUserId}",
                profileUrl = participant?.profileImageUrl,
                bibNumber = participant?.bibNumber,
                latitude = correctedLatitude,
                longitude = correctedLongitude,
                altitude = correctedAltitude,
                speed = rawSpeed,
            )

        } catch (e: Exception) {
            logger.error("Redis 위치 데이터 파싱 실패: key=$key", e)
            return null
        }
    }

    /**
     * 지도 마커를 위한 데이터 구성
     */
    private fun buildMapMarkerData(
        participantLocations: List<EventParticipantLocationDto>,
        eventId: Long,
        eventDetailId: Long,
        currentUserId: Long?
    ): List<EventParticipantLocationDto> {
        if (participantLocations.isEmpty()) {
            return emptyList()
        }

        try {
            // 마커 우선순위 분류를 위한 사용자 그룹 조회
            val top3UserIds = leaderboardService.getTopRankers(eventId, eventDetailId).toSet()
            val trackedUserIds = currentUserId?.let { 
                getTrackedUserIds(it)
            }?.toSet() ?: emptySet()

            // 지도 마커용 데이터 정렬
            return participantLocations.sortedWith { a, b ->
                // 위도 기준 내림차순 정렬 (북쪽에 있는 참가자 우선)
                val latitudeComparison = compareValues(b.latitude, a.latitude)
                if (latitudeComparison != 0) return@sortedWith latitudeComparison

                // 경도 기준 내림차순 정렬 (동쪽에 있는 참가자 우선)
                val longitudeComparison = compareValues(b.longitude, a.longitude)
                if (longitudeComparison != 0) return@sortedWith longitudeComparison

                // 마커 우선순위 적용
                val aPriority = getMarkerPriority(a.userId, top3UserIds, currentUserId, trackedUserIds)
                val bPriority = getMarkerPriority(b.userId, top3UserIds, currentUserId, trackedUserIds)
                compareValues(aPriority, bPriority)
            }

        } catch (e: Exception) {
            logger.error("지도 마커 데이터 구성 실패", e)
            return participantLocations.sortedByDescending { it.latitude }
        }
    }

    /**
     * 마커 우선순위 계산
     */
    private fun getMarkerPriority(
        userId: Long,
        top3UserIds: Set<Long>,
        currentUserId: Long?,
        trackedUserIds: Set<Long>
    ): Int {
        return when {
            userId in top3UserIds -> 1 // 최고 우선순위: 상위 3명
            userId == currentUserId -> 2 // 중간 우선순위: 현재 사용자
            userId in trackedUserIds -> 3 // 낮은 우선순위: 트래킹 목록
            else -> 4 // 최저 우선순위: 기타
        }
    }

    /**
     * 트래킹 목록 userId 조회 (/api/v1/trackers/{userId} API 호출)
     */
    private fun getTrackedUserIds(currentUserId: Long): List<Long> {
        return try {
            val trackerList = trackerService.getTrackerList(currentUserId)
            val trackedUserIds = trackerList.participants.map { it.participantId }
            
            logger.info("트래킹 목록 조회 완료: currentUserId=$currentUserId, 트래킹 참가자 수=${trackerList.participants.size}")
            trackedUserIds

        } catch (e: Exception) {
            logger.error("트래킹 목록 조회 실패: currentUserId=$currentUserId", e)
            emptyList()
        }
    }
} 