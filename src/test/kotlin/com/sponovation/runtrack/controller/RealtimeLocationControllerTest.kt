package com.sponovation.runtrack.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.dto.RealtimeLocationResponseDto
import com.sponovation.runtrack.dto.RealtimeLocationDataDto
import com.sponovation.runtrack.dto.ParticipantLocationDto
import com.sponovation.runtrack.dto.RankingDto
import com.sponovation.runtrack.service.RealtimeLocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@WebMvcTest(RealtimeLocationController::class)
@DisplayName("실시간 위치 추적 컨트롤러 테스트")
class RealtimeLocationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var realtimeLocationService: RealtimeLocationService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var mockResponse: RealtimeLocationResponseDto

    @BeforeEach
    fun setUp() {
        mockResponse = createMockResponse()
    }

    @Test
    @DisplayName("정상적인 실시간 위치 조회 - eventDetailId만 포함")
    fun getRealtimeLocations_WithEventDetailIdOnly_ShouldReturnOk() {
        // Given
        val eventDetailId = 456L
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, null))
            .thenReturn(mockResponse)

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data.participants").isArray)
            .andExpect(jsonPath("$.data.top3").isArray)
    }

    @Test
    @DisplayName("정상적인 실시간 위치 조회 - eventDetailId와 zoomLevel 모두 포함")
    fun getRealtimeLocations_WithEventDetailIdAndZoomLevel_ShouldReturnOk() {
        // Given
        val eventDetailId = 456L
        val zoomLevel = 14
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, zoomLevel))
            .thenReturn(mockResponse)

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .param("zoomLevel", zoomLevel.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data.participants").isArray)
            .andExpect(jsonPath("$.data.top3").isArray)
    }

    @Test
    @DisplayName("eventDetailId 누락 시 400 에러 반환")
    fun getRealtimeLocations_WithoutEventDetailId_ShouldReturnBadRequest() {
        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("잘못된 zoomLevel (1 미만) 시 400 에러 반환")
    fun getRealtimeLocations_WithInvalidZoomLevelBelow1_ShouldReturnBadRequest() {
        // Given
        val eventDetailId = 456L
        val invalidZoomLevel = 0

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .param("zoomLevel", invalidZoomLevel.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("잘못된 zoomLevel (20 초과) 시 400 에러 반환")
    fun getRealtimeLocations_WithInvalidZoomLevelAbove20_ShouldReturnBadRequest() {
        // Given
        val eventDetailId = 456L
        val invalidZoomLevel = 21

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .param("zoomLevel", invalidZoomLevel.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("서비스에서 IllegalArgumentException 발생 시 400 에러 반환")
    fun getRealtimeLocations_WithIllegalArgumentException_ShouldReturnBadRequest() {
        // Given
        val eventDetailId = 456L
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, null))
            .thenThrow(IllegalArgumentException("잘못된 파라미터"))

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("서비스에서 NoSuchElementException 발생 시 404 에러 반환")
    fun getRealtimeLocations_WithNoSuchElementException_ShouldReturnNotFound() {
        // Given
        val eventDetailId = 999L
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, null))
            .thenThrow(NoSuchElementException("대회를 찾을 수 없음"))

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("서비스에서 일반 Exception 발생 시 500 에러 반환")
    fun getRealtimeLocations_WithGeneralException_ShouldReturnInternalServerError() {
        // Given
        val eventDetailId = 456L
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, null))
            .thenThrow(RuntimeException("예상치 못한 서버 오류"))

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isInternalServerError)
    }

    @Test
    @DisplayName("경계값 테스트 - zoomLevel 1 (최소값)")
    fun getRealtimeLocations_WithMinZoomLevel_ShouldReturnOk() {
        // Given
        val eventDetailId = 456L
        val zoomLevel = 1
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, zoomLevel))
            .thenReturn(mockResponse)

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .param("zoomLevel", zoomLevel.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("경계값 테스트 - zoomLevel 20 (최대값)")
    fun getRealtimeLocations_WithMaxZoomLevel_ShouldReturnOk() {
        // Given
        val eventDetailId = 456L
        val zoomLevel = 20
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, zoomLevel))
            .thenReturn(mockResponse)

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .param("zoomLevel", zoomLevel.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("JSON 응답 형식 검증")
    fun getRealtimeLocations_ShouldReturnValidJsonStructure() {
        // Given
        val eventDetailId = 456L
        whenever(realtimeLocationService.getParticipantsLocations(eventDetailId, null))
            .thenReturn(mockResponse)

        // When & Then
        mockMvc.perform(
            get("/api/realtime-locations")
                .param("eventDetailId", eventDetailId.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data.participants").isArray)
            .andExpect(jsonPath("$.data.participants[0].userId").exists())
            .andExpect(jsonPath("$.data.participants[0].nickname").exists())
            .andExpect(jsonPath("$.data.participants[0].lat").exists())
            .andExpect(jsonPath("$.data.participants[0].lng").exists())
            .andExpect(jsonPath("$.data.participants[0].timestamp").exists())
            .andExpect(jsonPath("$.data.top3").isArray)
            .andExpect(jsonPath("$.data.top3[0].rank").exists())
            .andExpect(jsonPath("$.data.top3[0].userId").exists())
            .andExpect(jsonPath("$.data.top3[0].nickname").exists())
    }

    private fun createMockResponse(): RealtimeLocationResponseDto {
        val participant1 = ParticipantLocationDto(
            userId = 1L,
            nickname = "김러너",
            lat = 37.5665,
            lng = 126.9780,
            alt = 10.5,
            heading = 45.0,
            speed = 15.2,
            timestamp = "2024-01-15T10:30:00"
        )

        val participant2 = ParticipantLocationDto(
            userId = 2L,
            nickname = "이마라톤",
            lat = 37.5661,
            lng = 126.9775,
            alt = 11.0,
            heading = 50.0,
            speed = 14.8,
            timestamp = "2024-01-15T10:30:00"
        )

        val top3Rankings = listOf(
            RankingDto(
                rank = 1,
                userId = 1L,
                nickname = "김러너",
                bibNumber = "001",
                elapsedTime = "01:25:30"
            ),
            RankingDto(
                rank = 2,
                userId = 2L,
                nickname = "이마라톤",
                bibNumber = "002",
                elapsedTime = "01:28:45"
            ),
            RankingDto(
                rank = 3,
                userId = 3L,
                nickname = "박러닝",
                bibNumber = "003",
                elapsedTime = "01:30:15"
            )
        )

        val data = RealtimeLocationDataDto(
            participants = listOf(participant1, participant2),
            top3 = top3Rankings
        )

        return RealtimeLocationResponseDto(data = data)
    }
} 