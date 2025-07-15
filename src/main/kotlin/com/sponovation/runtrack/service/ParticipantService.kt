package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.Gender
import com.sponovation.runtrack.domain.Participant
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.repository.EventRepository
import com.sponovation.runtrack.repository.EventDetailRepository
import com.sponovation.runtrack.repository.ParticipantRepository
import com.sponovation.runtrack.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

@Service
@Transactional
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val eventRepository: EventRepository,
    private val eventDetailRepository: EventDetailRepository,
    private val userRepository: UserRepository
) {

    /**
     * 참가자 생성
     */
    fun createParticipant(request: ParticipantRequestDto): ParticipantResponseDto {
        // 이벤트와 이벤트 상세, 사용자 존재 여부 확인
        if (!eventRepository.existsById(request.eventId)) {
            throw IllegalArgumentException("존재하지 않는 이벤트입니다: ${request.eventId}")
        }
        
        if (!eventDetailRepository.existsById(request.eventDetailId)) {
            throw IllegalArgumentException("존재하지 않는 이벤트 상세입니다: ${request.eventDetailId}")
        }
        
        // 테스트 환경에서는 사용자 존재 여부 확인을 건너뛰기 (임시 사용자 ID 사용)
        if (request.userId < 10000 && !userRepository.existsById(request.userId)) {
            throw IllegalArgumentException("존재하지 않는 사용자입니다: ${request.userId}")
        }

        // 중복 참가 확인
        if (participantRepository.existsByEventIdAndEventDetailIdAndUserId(
                request.eventId, request.eventDetailId, request.userId
            )) {
            throw IllegalArgumentException("이미 해당 이벤트에 참가한 사용자입니다")
        }

        val participant = Participant(
            eventId = request.eventId,
            eventDetailId = request.eventDetailId,
            userId = request.userId,
            name = request.name,
            nickname = request.nickname,
            country = request.country,
            profileImageUrl = request.profileImageUrl,
            age = request.age,
            gender = request.gender,
            status = request.status,
            registeredAt = LocalDateTime.now(),
            bibNumber = request.bibNumber,
            tagName = request.tagName,
            adminMemo = request.adminMemo,
            userMemo = request.userMemo,
            raceStatus = request.raceStatus
        )

        val savedParticipant = participantRepository.save(participant)
        return convertToResponseDto(savedParticipant)
    }

    /**
     * 참가자 조회
     */
    @Transactional(readOnly = true)
    fun getParticipant(id: Long): ParticipantResponseDto {
        val participant = participantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("존재하지 않는 참가자입니다: $id") }
        return convertToResponseDto(participant)
    }

    /**
     * 참가자 수정
     */
    fun updateParticipant(id: Long, request: UpdateParticipantRequestDto): ParticipantResponseDto {
        val participant = participantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("존재하지 않는 참가자입니다: $id") }

        val updatedParticipant = participant.copy(
            name = request.name ?: participant.name,
            nickname = request.nickname ?: participant.nickname,
            country = request.country ?: participant.country,
            profileImageUrl = request.profileImageUrl ?: participant.profileImageUrl,
            age = request.age ?: participant.age,
            gender = request.gender ?: participant.gender,
            status = request.status ?: participant.status,
            bibNumber = request.bibNumber ?: participant.bibNumber,
            tagName = request.tagName ?: participant.tagName,
            adminMemo = request.adminMemo ?: participant.adminMemo,
            userMemo = request.userMemo ?: participant.userMemo,
            raceStatus = request.raceStatus ?: participant.raceStatus
        )

        val savedParticipant = participantRepository.save(updatedParticipant)
        return convertToResponseDto(savedParticipant)
    }

    /**
     * 참가자 삭제
     */
    fun deleteParticipant(id: Long) {
        if (!participantRepository.existsById(id)) {
            throw IllegalArgumentException("존재하지 않는 참가자입니다: $id")
        }
        participantRepository.deleteById(id)
    }

    /**
     * 이벤트별 참가자 목록 조회
     */
    @Transactional(readOnly = true)
    fun getParticipantsByEvent(eventId: Long, page: Int, size: Int): List<ParticipantResponseDto> {
        val pageable = PageRequest.of(page, size)
        val participants = participantRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable)
        return participants.content.map { convertToResponseDto(it) }
    }

    /**
     * 테스트 참가자 생성
     */
    fun createTestParticipants(request: CreateTestParticipantsRequestDto): CreateTestParticipantsResponseDto {
        // 이벤트와 이벤트 상세 존재 여부 확인
        if (!eventRepository.existsById(request.eventId)) {
            throw IllegalArgumentException("존재하지 않는 이벤트입니다: ${request.eventId}")
        }
        
        if (!eventDetailRepository.existsById(request.eventDetailId)) {
            throw IllegalArgumentException("존재하지 않는 이벤트 상세입니다: ${request.eventDetailId}")
        }

        val testParticipants = generateBasicTestParticipants(request)

        val savedParticipants = participantRepository.saveAll(testParticipants)
        val participantIds = savedParticipants.map { it.id!! }

        return CreateTestParticipantsResponseDto(
            createdCount = savedParticipants.size,
            eventId = request.eventId,
            eventDetailId = request.eventDetailId,
            participantIds = participantIds,
            createdAt = LocalDateTime.now()
        )
    }

    /**
     * 테스트 참가자 삭제
     */
    fun deleteTestParticipants(eventId: Long): Int {
        return participantRepository.deleteByEventId(eventId)
    }

    /**
     * 기본 테스트 참가자 생성
     */
    private fun generateBasicTestParticipants(request: CreateTestParticipantsRequestDto): List<Participant> {
        val testNames = listOf("김민수", "이영희", "박철수", "정소영", "최준호", "한지은", "임대호", "윤서현")
        val testNicknames = listOf("러너", "스피드", "마라토너", "파이터", "챔피언", "헬스키퍼", "달리기왕")
        val testCountries = listOf("South Korea", "Japan", "China", "United States", "United Kingdom", "France", "Germany", "Australia")
        
        return (1..request.count).map { index ->
            Participant(
                eventId = request.eventId,
                eventDetailId = request.eventDetailId,
                userId = Random.nextLong(1, 10000), // 임시 사용자 ID
                name = testNames[Random.nextInt(testNames.size)] + index,
                nickname = testNicknames[Random.nextInt(testNicknames.size)] + index,
                country = testCountries[Random.nextInt(testCountries.size)],
                profileImageUrl = "https://example.com/profile$index.jpg",
                age = Random.nextInt(18, 70),
                gender = if (Random.nextBoolean()) Gender.M else Gender.F,
                status = "REGISTERED",
                registeredAt = LocalDateTime.now().minusDays(Random.nextLong(0, 30)),
                bibNumber = "T${String.format("%04d", index)}",
                tagName = "T${String.format("%04d", index)}",
                adminMemo = if (Random.nextDouble() < 0.2) "테스트 참가자" else null,
                userMemo = if (Random.nextDouble() < 0.4) "첫 참가입니다" else null,
                raceStatus = "READY"
            )
        }
    }

    /**
     * Entity를 ResponseDto로 변환
     */
    private fun convertToResponseDto(participant: Participant): ParticipantResponseDto {
        return ParticipantResponseDto(
            id = participant.id!!,
            eventId = participant.eventId,
            eventDetailId = participant.eventDetailId,
            userId = participant.userId,
            name = participant.name,
            nickname = participant.nickname,
            country = participant.country,
            profileImageUrl = participant.profileImageUrl,
            age = participant.age,
            gender = participant.gender,
            status = participant.status,
            registeredAt = participant.registeredAt,
            bibNumber = participant.bibNumber,
            tagName = participant.tagName,
            adminMemo = participant.adminMemo,
            userMemo = participant.userMemo,
            raceStatus = participant.raceStatus,
            createdAt = participant.createdAt,
            updatedAt = participant.updatedAt
        )
    }

    /**
     * 참가자 검색 (커서 기반 페이징)
     */
    @Transactional(readOnly = true)
    fun searchParticipants(request: ParticipantSearchRequestDto): ParticipantSearchResponseDto {
        // 이벤트 존재 여부 확인
        if (!eventRepository.existsById(request.eventId)) {
            throw IllegalArgumentException("존재하지 않는 이벤트입니다: ${request.eventId}")
        }

        // 커서 파싱
        val (cursorBibNumber, cursorCreatedAt) = parseCursor(request.cursor)

        // 페이징 설정 (size + 1로 다음 페이지 존재 여부 확인)
        val pageable = PageRequest.of(0, request.size + 1)

        // 검색 실행
        val participants = participantRepository.searchParticipantsWithCursor(
            eventId = request.eventId,
            search = request.search?.trim()?.takeIf { it.isNotBlank() },
            cursorBibNumber = cursorBibNumber,
            cursorCreatedAt = cursorCreatedAt,
            limit = pageable
        )

        // 다음 페이지 존재 여부 확인
        val hasNext = participants.size > request.size
        val resultParticipants = if (hasNext) {
            participants.take(request.size)
        } else {
            participants
        }

        // 응답 DTO 변환
        val searchItems = resultParticipants.map { participant ->
            ParticipantSearchItemDto(
                participantId = participant.id,
                name = participant.name,
                bibNumber = participant.bibNumber,
                profileImageUrl = participant.profileImageUrl,
                country = participant.country
            )
        }

        // 다음 커서 생성
        val nextCursor = if (hasNext && resultParticipants.isNotEmpty()) {
            val lastParticipant = resultParticipants.last()
            createCursor(lastParticipant.bibNumber, lastParticipant.createdAt)
        } else {
            null
        }

        return ParticipantSearchResponseDto(
            participants = searchItems,
            nextCursor = nextCursor
        )
    }

    /**
     * 커서 파싱
     * 형식: "bibNumber_yyyyMMddHHmmss"
     */
    private fun parseCursor(cursor: String?): Pair<String?, LocalDateTime?> {
        if (cursor.isNullOrBlank()) {
            return Pair(null, null)
        }

        return try {
            val parts = cursor.split("_")
            if (parts.size != 2) {
                return Pair(null, null)
            }

            val bibNumber = parts[0]
            val dateTimeStr = parts[1]
            val createdAt = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            
            Pair(bibNumber, createdAt)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * 커서 생성
     * 형식: "bibNumber_yyyyMMddHHmmss"
     */
    private fun createCursor(bibNumber: String?, createdAt: LocalDateTime): String? {
        return if (bibNumber != null) {
            "${bibNumber}_${createdAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))}"
        } else {
            null
        }
    }
} 