package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Event
import com.sponovation.runtrack.enums.EventStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * EventRepository의 기본 동작을 테스트하는 단위 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class EventRepositoryTest {
    
    @Autowired
    private lateinit var eventRepository: EventRepository
    
    @Test
    fun `이벤트 저장 및 조회 테스트`() {
        // Given
        val event = Event(
            eventName = "2024 서울 마라톤 대회",
            eventDate = LocalDate.of(2024, 10, 15),
            eventStatus = EventStatus.SCHEDULED,
            description = "서울 시내를 달리는 마라톤 대회",
            registrationStartDate = LocalDate.of(2024, 8, 1),
            registrationEndDate = LocalDate.of(2024, 9, 30)
        )
        
        // When
        val savedEvent = eventRepository.save(event)
        val foundEvent = eventRepository.findById(savedEvent.id)
        
        // Then
        assertTrue(foundEvent.isPresent)
        assertEquals("2024 서울 마라톤 대회", foundEvent.get().eventName)
        assertEquals(EventStatus.SCHEDULED, foundEvent.get().eventStatus)
        assertEquals(LocalDate.of(2024, 10, 15), foundEvent.get().eventDate)
    }
    
    @Test
    fun `이벤트 상태별 조회 테스트`() {
        // Given
        val scheduledEvent = Event(
            eventName = "예정된 이벤트",
            eventDate = LocalDate.now().plusDays(30),
            eventStatus = EventStatus.SCHEDULED
        )
        val draftEvent = Event(
            eventName = "초안 이벤트",
            eventDate = LocalDate.now().plusDays(60),
            eventStatus = EventStatus.DRAFT
        )
        
        eventRepository.saveAll(listOf(scheduledEvent, draftEvent))
        
        // When
        val scheduledEvents = eventRepository.findByEventStatusOrderByEventDateDesc(
            EventStatus.SCHEDULED, 
            PageRequest.of(0, 10)
        )
        val draftEvents = eventRepository.findByEventStatusOrderByEventDateDesc(
            EventStatus.DRAFT, 
            PageRequest.of(0, 10)
        )
        
        // Then
        assertEquals(1, scheduledEvents.totalElements)
        assertEquals(1, draftEvents.totalElements)
        assertEquals("예정된 이벤트", scheduledEvents.content[0].eventName)
        assertEquals("초안 이벤트", draftEvents.content[0].eventName)
    }
    
    @Test
    fun `참가 신청 가능한 이벤트 조회 테스트`() {
        // Given
        val currentDate = LocalDate.now()
        val openEvent = Event(
            eventName = "참가 신청 가능 이벤트",
            eventDate = currentDate.plusDays(30),
            eventStatus = EventStatus.SCHEDULED,
            registrationStartDate = currentDate.minusDays(10),
            registrationEndDate = currentDate.plusDays(10)
        )
        val closedEvent = Event(
            eventName = "참가 신청 마감 이벤트",
            eventDate = currentDate.plusDays(30),
            eventStatus = EventStatus.SCHEDULED,
            registrationStartDate = currentDate.minusDays(20),
            registrationEndDate = currentDate.minusDays(1)
        )
        
        eventRepository.saveAll(listOf(openEvent, closedEvent))
        
        // When
        val openEvents = eventRepository.findOpenRegistrationEvents(
            currentDate, 
            PageRequest.of(0, 10)
        )
        
        // Then
        assertEquals(1, openEvents.totalElements)
        assertEquals("참가 신청 가능 이벤트", openEvents.content[0].eventName)
    }
    
    @Test
    fun `예정된 이벤트 조회 테스트`() {
        // Given
        val currentDate = LocalDate.now()
        val upcomingEvent = Event(
            eventName = "다가오는 이벤트",
            eventDate = currentDate.plusDays(10),
            eventStatus = EventStatus.SCHEDULED
        )
        val pastEvent = Event(
            eventName = "지난 이벤트",
            eventDate = currentDate.minusDays(10),
            eventStatus = EventStatus.COMPLETED
        )
        
        eventRepository.saveAll(listOf(upcomingEvent, pastEvent))
        
        // When
        val upcomingEvents = eventRepository.findUpcomingEvents(
            currentDate, 
            PageRequest.of(0, 10)
        )
        
        // Then
        assertEquals(1, upcomingEvents.totalElements)
        assertEquals("다가오는 이벤트", upcomingEvents.content[0].eventName)
    }
    
    @Test
    fun `이벤트 이름 검색 테스트`() {
        // Given
        val marathonEvent = Event(
            eventName = "서울 마라톤 대회",
            eventDate = LocalDate.now().plusDays(30),
            eventStatus = EventStatus.SCHEDULED
        )
        val runningEvent = Event(
            eventName = "한강 러닝 페스티벌",
            eventDate = LocalDate.now().plusDays(45),
            eventStatus = EventStatus.SCHEDULED
        )
        
        eventRepository.saveAll(listOf(marathonEvent, runningEvent))
        
        // When
        val searchResults = eventRepository.findByEventNameContainingIgnoreCaseOrderByEventDateDesc(
            "마라톤", 
            PageRequest.of(0, 10)
        )
        
        // Then
        assertEquals(1, searchResults.totalElements)
        assertEquals("서울 마라톤 대회", searchResults.content[0].eventName)
    }
    
    @Test
    fun `이벤트 상태별 개수 조회 테스트`() {
        // Given
        val event1 = Event(
            eventName = "이벤트1",
            eventDate = LocalDate.now().plusDays(10),
            eventStatus = EventStatus.SCHEDULED
        )
        val event2 = Event(
            eventName = "이벤트2",
            eventDate = LocalDate.now().plusDays(20),
            eventStatus = EventStatus.SCHEDULED
        )
        val event3 = Event(
            eventName = "이벤트3",
            eventDate = LocalDate.now().plusDays(30),
            eventStatus = EventStatus.DRAFT
        )
        
        eventRepository.saveAll(listOf(event1, event2, event3))
        
        // When
        val scheduledCount = eventRepository.countByEventStatus(EventStatus.SCHEDULED)
        val draftCount = eventRepository.countByEventStatus(EventStatus.DRAFT)
        
        // Then
        assertEquals(2, scheduledCount)
        assertEquals(1, draftCount)
    }
} 