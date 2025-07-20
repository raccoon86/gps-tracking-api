package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.Tracker
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.repository.ParticipantRepository
import com.sponovation.runtrack.repository.TrackerRepository
import com.sponovation.runtrack.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TrackerService(
    private val trackerRepository: TrackerRepository,
    private val participantRepository: ParticipantRepository,
) {

    /**
     * 트래킹 추가
     */
    fun addTracker(request: AddTrackerRequestDto): String {
        // 사용자와 참가자 존재 여부 확인
//        if (request.userId < 10000 && !userRepository.existsById(request.userId)) {
//            throw IllegalArgumentException("존재하지 않는 사용자입니다: ${request.userId}")
//        }
//
        if (!participantRepository.existsByUserId(request.participantId)) {
            throw IllegalArgumentException("존재하지 않는 참가자입니다: ${request.participantId}")
        }

        // 이미 트래킹 중인지 확인 (이벤트별로 체크)
        if (trackerRepository.existsByUserIdAndEventIdAndEventDetailIdAndParticipantId(
                request.userId, 
                request.eventId, 
                request.eventDetailId, 
                request.participantId
            )) {
            throw IllegalArgumentException("이미 트래킹 중인 참가자입니다")
        }

        val tracker = Tracker(
            userId = request.userId,
            eventId = request.eventId,
            eventDetailId = request.eventDetailId,
            participantId = request.participantId
        )

        trackerRepository.save(tracker)
        return "트래킹이 추가되었습니다"
    }

    /**
     * 사용자의 트래킹 목록 조회
     */
    @Transactional(readOnly = true)
    fun getTrackerList(userId: Long): TrackerListResponseDto {

        // 사용자가 트래킹하는 참가자 목록 조회 (Projection 사용)
        val trackedParticipants = trackerRepository.findTrackedParticipantsByUserId(userId)

        val participants = trackedParticipants.map { projection ->
            TrackedParticipantDto(
                participantId = projection.getParticipantId(),
                name = projection.getName(),
                nickname = projection.getNickname(),
                bibNumber = projection.getBibNumber(),
                country = projection.getCountry(),
                profileImage = projection.getProfileImage(),
                trackedAt = projection.getTrackedAt().toString()
            )
        }

        return TrackerListResponseDto(
            participants = participants,
            totalCount = participants.size
        )
    }

    /**
     * 특정 이벤트에서 사용자의 트래킹 목록 조회
     */
    @Transactional(readOnly = true)
    fun getTrackerList(userId: Long, eventId: Long, eventDetailId: Long): TrackerListResponseDto {
//        // 사용자 존재 여부 확인
//        if (userId < 10000 && !userRepository.existsById(userId)) {
//            throw IllegalArgumentException("존재하지 않는 사용자입니다: $userId")
//        }

        // 특정 이벤트에서 사용자가 트래킹하는 참가자 목록 조회 (Projection 사용)
        val trackedParticipants = trackerRepository.findTrackedParticipantsByUserIdAndEvent(
            userId, 
            eventId, 
            eventDetailId
        )

        val participants = trackedParticipants.map { projection ->
            TrackedParticipantDto(
                participantId = projection.getParticipantId(),
                name = projection.getName(),
                bibNumber = projection.getBibNumber(),
                country = projection.getCountry(),
                profileImage = projection.getProfileImage(),
                trackedAt = projection.getTrackedAt().toString()
            )
        }

        return TrackerListResponseDto(
            participants = participants,
            totalCount = participants.size
        )
    }

    /**
     * 트래킹 삭제
     */
    fun removeTracker(request: RemoveTrackerRequestDto): String {
//        // 사용자와 참가자 존재 여부 확인
//        if (request.userId < 10000 && !userRepository.existsById(request.userId)) {
//            throw IllegalArgumentException("존재하지 않는 사용자입니다: ${request.userId}")
//        }
        
        if (!participantRepository.existsById(request.participantId)) {
            throw IllegalArgumentException("존재하지 않는 참가자입니다: ${request.participantId}")
        }

        // 트래킹 관계 존재 여부 확인 (이벤트별로 체크)
        if (!trackerRepository.existsByUserIdAndEventIdAndEventDetailIdAndParticipantId(
                request.userId, 
                request.eventId, 
                request.eventDetailId, 
                request.participantId
            )) {
            throw IllegalArgumentException("트래킹하지 않는 참가자입니다")
        }

        val deletedCount = trackerRepository.deleteByUserIdAndEventIdAndEventDetailIdAndParticipantId(
            request.userId, 
            request.eventId, 
            request.eventDetailId, 
            request.participantId
        )
        
        return if (deletedCount > 0) {
            "트래킹이 삭제되었습니다"
        } else {
            "트래킹 삭제에 실패했습니다"
        }
    }
} 