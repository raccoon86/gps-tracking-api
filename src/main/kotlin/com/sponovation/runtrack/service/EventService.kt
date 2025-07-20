package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.Event
import com.sponovation.runtrack.dto.CreateTestEventRequestDto
import com.sponovation.runtrack.dto.CreateTestEventResponseDto
import com.sponovation.runtrack.repository.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Event 엔티티 관리 서비스
 * 
 * Event 엔티티의 생성, 조회, 수정, 삭제 작업을 담당합니다.
 */
@Service
@Transactional
class EventService(
    private val eventRepository: EventRepository
) {
    
    private val logger = LoggerFactory.getLogger(EventService::class.java)
    
    /**
     * 테스트용 Event 생성
     * 
     * @param request Event 생성 요청 DTO
     * @return Event 생성 응답 DTO
     */
    fun createTestEvent(request: CreateTestEventRequestDto): CreateTestEventResponseDto {
        logger.info("테스트 Event 생성 시작: name=${request.name}")
        
        try {
            // 기본값 설정
            val startDateTime = request.startDateTime ?: LocalDateTime.now().plusDays(1)
            val endDateTime = request.endDateTime ?: startDateTime.plusHours(6)
            
            // Event 엔티티 생성
            val event = Event(
                name = request.name,
                sports = request.sports,
                startDateTime = startDateTime,
                endDateTime = endDateTime,
                country = request.country,
                city = request.city,
                address = request.address,
                place = request.place,
                latitude = request.latitude.toBigDecimal(),
                longitude = request.longitude.toBigDecimal(),
                thumbnail = request.thumbnail ?: "test://thumbnail/test.jpg"
            )
            
            val savedEvent = eventRepository.save(event)
            
            logger.info("테스트 Event 생성 완료: eventId=${savedEvent.id}, name=${savedEvent.name}")
            
            return CreateTestEventResponseDto(
                eventId = savedEvent.id!!,
                eventName = savedEvent.name ?: "",
                sports = savedEvent.sports ?: "",
                startDateTime = savedEvent.startDateTime.toString(),
                endDateTime = savedEvent.endDateTime.toString(),
                country = savedEvent.country ?: "",
                city = savedEvent.city,
                createdAt = savedEvent.createdAt.toString(),
                message = "새 이벤트 생성 완료",
                isNewlyCreated = true
            )
            
        } catch (e: Exception) {
            logger.error("테스트 Event 생성 실패: name=${request.name}", e)
            throw e
        }
    }
    
    /**
     * Event ID로 Event 조회 (존재하는 경우 정보 반환)
     * 
     * @param eventId Event ID
     * @param name Event 이름 (새로 생성할 때 사용)
     * @return Event 응답 DTO 또는 null
     */
    @Transactional(readOnly = true)
    fun getTestEventOrNull(eventId: Long, name: String): CreateTestEventResponseDto? {
        logger.info("테스트 Event 조회: eventId=$eventId")
        
        return try {
            val event = eventRepository.findById(eventId).orElse(null)
                ?: return null
            
            logger.info("기존 Event 조회 완료: eventId=$eventId, name=${event.name}")
            
            CreateTestEventResponseDto(
                eventId = event.id!!,
                eventName = event.name ?: "",
                sports = event.sports ?: "",
                startDateTime = event.startDateTime.toString(),
                endDateTime = event.endDateTime.toString(),
                country = event.country ?: "",
                city = event.city,
                createdAt = event.createdAt.toString(),
                message = "기존 이벤트 사용",
                isNewlyCreated = false
            )
            
        } catch (e: Exception) {
            logger.error("테스트 Event 조회 실패: eventId=$eventId", e)
            null
        }
    }
    
    /**
     * Event 존재 여부 확인
     * 
     * @param eventId Event ID
     * @return 존재 여부
     */
    @Transactional(readOnly = true)
    fun existsById(eventId: Long): Boolean {
        return eventRepository.existsById(eventId)
    }
    
    /**
     * Event 조회
     * 
     * @param eventId Event ID
     * @return Event 엔티티
     * @throws IllegalArgumentException Event를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    fun findById(eventId: Long): Event {
        return eventRepository.findById(eventId)
            .orElseThrow { IllegalArgumentException("Event를 찾을 수 없습니다: eventId=$eventId") }
    }
} 