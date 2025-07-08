package com.sponovation.runtrack.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.dto.CorrectLocationRequestDto
import com.sponovation.runtrack.dto.CorrectLocationResponseDto
import com.sponovation.runtrack.dto.CorrectedLocationDataDto
import com.sponovation.runtrack.service.GpxService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.NoSuchElementException

@WebMvcTest(GpxController::class)
class GpxControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var gpxService: GpxService

    @Test
    fun `정상적인 GPX 보정 위치 조회 요청 시 성공 응답을 반환한다`() {
        // Given
        val request = CorrectLocationRequestDto(
            userId = 123L,
            eventDetailId = 456L,
            gpsData = CorrectLocationRequestDto.GpsData(
                lat = 37.123456,
                lng = 127.123456,
                timestamp = "2025-07-06T05:00:00Z"
            )
        )
        
        val expectedResponse = CorrectLocationResponseDto(
            data = CorrectedLocationDataDto(
                correctedLat = 37.123567,
                correctedLng = 127.123789
            )
        )

        given(gpxService.correctLocation(request)).willReturn(expectedResponse)

        // When & Then
        mockMvc.perform(
            post("/api/gpx/correct-location")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json;charset=UTF-8"))
            .andExpect(jsonPath("$.data.correctedLat").value(37.123567))
            .andExpect(jsonPath("$.data.correctedLng").value(127.123789))
    }

    @Test
    fun `필수 파라미터 누락 시 400 에러를 반환한다`() {
        // Given
        val invalidRequest = """
            {
                "eventDetailId": 456,
                "gpsData": {
                    "lat": 37.123456,
                    "lng": 127.123456,
                    "timestamp": "2025-07-06T05:00:00Z"
                }
            }
        """.trimIndent()

        // When & Then
        mockMvc.perform(
            post("/api/gpx/correct-location")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("필수 파라미터 누락"))
    }

    @Test
    fun `대회 정보 또는 GPX 파일이 없을 때 404 에러를 반환한다`() {
        // Given
        val request = CorrectLocationRequestDto(
            userId = 123L,
            eventDetailId = 999L, // 존재하지 않는 이벤트 ID
            gpsData = CorrectLocationRequestDto.GpsData(
                lat = 37.123456,
                lng = 127.123456,
                timestamp = "2025-07-06T05:00:00Z"
            )
        )

        given(gpxService.correctLocation(request))
            .willThrow(NoSuchElementException("대회 정보 또는 GPX 파일 없음"))

        // When & Then
        mockMvc.perform(
            post("/api/gpx/correct-location")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("대회 정보 또는 GPX 파일 없음"))
    }

    @Test
    fun `보정 로직 오류 시 500 에러를 반환한다`() {
        // Given
        val request = CorrectLocationRequestDto(
            userId = 123L,
            eventDetailId = 456L,
            gpsData = CorrectLocationRequestDto.GpsData(
                lat = 37.123456,
                lng = 127.123456,
                timestamp = "2025-07-06T05:00:00Z"
            )
        )

        given(gpxService.correctLocation(request))
            .willThrow(RuntimeException("보정 로직 오류"))

        // When & Then
        mockMvc.perform(
            post("/api/gpx/correct-location")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("보정 로직 오류"))
    }
} 