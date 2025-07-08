package com.sponovation.runtrack.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class EventSchedulerService(
    private val courseDataService: CourseDataService
) {

    private val logger = LoggerFactory.getLogger(EventSchedulerService::class.java)

    /**
     * 매 10분마다 시작 예정인 대회들의 코스 데이터를 미리 로드합니다.
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    fun preloadUpcomingEventCourses() {
        try {
            logger.info("예정된 대회 코스 데이터 사전 로드 시작")
            
            val upcomingEvents = getUpcomingEvents()
            
            if (upcomingEvents.isNotEmpty()) {
                logger.info("사전 로드할 대회 수: ${upcomingEvents.size}")
                
                val courseIds = courseDataService.loadMultipleCourseData(upcomingEvents)
                
                logger.info("코스 데이터 사전 로드 완료: ${courseIds.size}개 코스")
            } else {
                logger.debug("사전 로드할 대회가 없습니다")
            }
            
        } catch (e: Exception) {
            logger.error("예정된 대회 코스 데이터 사전 로드 실패", e)
        }
    }

    /**
     * 매 시간마다 만료된 코스 데이터를 정리합니다.
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3,600,000ms
    fun cleanupExpiredCourseData() {
        try {
            logger.info("만료된 코스 데이터 정리 시작")
            
            val expiredEvents = getExpiredEvents()
            
            expiredEvents.forEach { eventCourse ->
                try {
                    val courseData = courseDataService.getCourseDataByEventId(eventCourse.eventId)
                    courseData?.let {
                        courseDataService.deleteCourseData(it.courseId)
                    }
                } catch (e: Exception) {
                    logger.error("코스 데이터 삭제 실패: eventId=${eventCourse.eventId}", e)
                }
            }
            
            logger.info("만료된 코스 데이터 정리 완료: ${expiredEvents.size}개")
            
        } catch (e: Exception) {
            logger.error("만료된 코스 데이터 정리 실패", e)
        }
    }

    /**
     * 특정 대회의 코스 데이터를 즉시 로드합니다.
     */
    fun loadEventCourseImmediately(eventId: Long, gpxFileName: String): String {
        logger.info("대회 코스 데이터 즉시 로드: eventId=$eventId, file=$gpxFileName")
        return courseDataService.loadAndCacheCourseData(eventId, gpxFileName)
    }

    /**
     * 향후 2시간 이내에 시작되는 대회 목록을 조회합니다.
     * 실제 구현에서는 데이터베이스에서 조회해야 합니다.
     */
    private fun getUpcomingEvents(): List<EventCourse> {
        // TODO: 실제 데이터베이스에서 조회하도록 구현
        // 현재는 예시 데이터 반환
        val now = LocalDateTime.now()
        val twoHoursLater = now.plus(2, ChronoUnit.HOURS)
        
        // 예시: 데이터베이스 쿼리
        // return eventRepository.findByStartTimeBetweenAndStatus(now, twoHoursLater, EventStatus.SCHEDULED)
        //     .map { event -> EventCourse(event.id, event.gpxFileName) }
        
        return emptyList() // 임시로 빈 리스트 반환
    }

    /**
     * 24시간 이상 지난 대회 목록을 조회합니다.
     * 실제 구현에서는 데이터베이스에서 조회해야 합니다.
     */
    private fun getExpiredEvents(): List<EventCourse> {
        // TODO: 실제 데이터베이스에서 조회하도록 구현
        // 현재는 예시 데이터 반환
        val now = LocalDateTime.now()
        val twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS)
        
        // 예시: 데이터베이스 쿼리
        // return eventRepository.findByEndTimeBefore(twentyFourHoursAgo)
        //     .map { event -> EventCourse(event.id, event.gpxFileName) }
        
        return emptyList() // 임시로 빈 리스트 반환
    }
} 