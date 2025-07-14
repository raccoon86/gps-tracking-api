package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * 대회 상세 조회 요청 DTO
 */
@Schema(description = "대회 상세 조회 요청")
data class EventDetailRequestDto(
    @Schema(description = "이벤트 ID", example = "1", required = true)
    @JsonProperty("eventId")
    @field:NotNull(message = "이벤트 ID는 필수입니다")
    @field:Positive(message = "이벤트 ID는 양수여야 합니다")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1", required = true)
    @JsonProperty("eventDetailId")
    @field:NotNull(message = "이벤트 상세 ID는 필수입니다")
    @field:Positive(message = "이벤트 상세 ID는 양수여야 합니다")
    val eventDetailId: Long,

    @Schema(description = "현재 로그인한 사용자 ID", example = "user123")
    @JsonProperty("currentUserId")
    val currentUserId: Long
)

/**
 * 대회 상세 조회 응답 DTO
 */
@Schema(description = "대회 상세 조회 응답")
data class EventDetailResponseDto(
    @Schema(description = "이벤트 ID", example = "eventA")
    @JsonProperty("eventId")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "eventDetail456")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,

    @Schema(description = "대회 이름", example = "서울 마라톤 2025")
    @JsonProperty("competitionName")
    val competitionName: String,

    @Schema(description = "코스 카테고리 목록")
    @JsonProperty("courseCategory")
    val courseCategory: List<CourseCategoryDto>,

    @Schema(description = "GPX 파일 S3 URL", example = "http://s3.amazon.com/path/to/gpx_file_for_eventDetail456.gpx")
    @JsonProperty("gpxFile")
    val gpxFile: String,

    @Schema(description = "참가자 위치 데이터 (1~3위 유저 + 트래커 목록 유저)")
    @JsonProperty("participantsLocations")
    val participantsLocations: List<EventParticipantLocationDto>,

    @Schema(description = "상위 랭커 정보 (맵 하단 표시용)")
    @JsonProperty("topRankers")
    val topRankers: List<TopRankerDto>
)

/**
 * 코스 카테고리 DTO
 */
@Schema(description = "코스 카테고리 정보")
data class CourseCategoryDto(
    @Schema(description = "코스 거리 (km)", example = "10.0")
    @JsonProperty("course")
    val course: Double,

    @Schema(description = "이벤트 상세 ID", example = "eventDetail123")
    @JsonProperty("eventDetailId")
    val eventDetailId: String
)

/**
 * 이벤트 참가자 위치 DTO
 */
@Schema(description = "이벤트 참가자 위치 정보")
data class EventParticipantLocationDto(
    @Schema(description = "사용자 ID", example = "userXYZ")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "보정된 위도", example = "37.5668")
    @JsonProperty("correctedLatitude")
    val correctedLatitude: Double,

    @Schema(description = "보정된 경도", example = "126.9786")
    @JsonProperty("correctedLongitude")
    val correctedLongitude: Double,

    @Schema(description = "보정된 고도", example = "50.5")
    @JsonProperty("correctedAltitude")
    val correctedAltitude: Double? = null,

    @Schema(description = "진행 방향", example = "45.2")
    @JsonProperty("heading")
    val heading: Double? = null,

    @Schema(description = "주행 거리 (미터)", example = "1200.5")
    @JsonProperty("distanceCovered")
    val distanceCovered: Double? = null,

    @Schema(description = "누적 시간 (포맷)", example = "00:25:30")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: String? = null
)

/**
 * 상위 랭커 DTO
 */
@Schema(description = "상위 랭커 정보")
data class TopRankerDto(
    @Schema(description = "순위", example = "1")
    @JsonProperty("rank")
    val rank: Int,

    @Schema(description = "사용자 ID", example = "userOfficialFinished")
    @JsonProperty("userId")
    val userId: Long,

    @Schema(description = "참가자 이름", example = "공식완주자1")
    @JsonProperty("name")
    val name: String,

    @Schema(description = "참가자 번호", example = "F001")
    @JsonProperty("bibNumber")
    val bibNumber: String,

    @Schema(description = "프로필 이미지 URL", example = "http://example.com/profile/official1.jpg")
    @JsonProperty("profileImageUrl")
    val profileImageUrl: String? = null,

    @Schema(description = "가장 최근 체크포인트 ID", example = "FINISH")
    @JsonProperty("farthestCpId")
    val farthestCpId: String? = null,

    @Schema(description = "가장 최근 체크포인트 인덱스", example = "5")
    @JsonProperty("farthestCpIndex")
    val farthestCpIndex: Int? = null,

    @Schema(description = "가장 최근 체크포인트까지의 누적 시간", example = "00:40:15")
    @JsonProperty("cumulativeTimeAtFarthestCp")
    val cumulativeTimeAtFarthestCp: String? = null,

    @Schema(description = "누적 거리 (미터)", example = "10000.0")
    @JsonProperty("cumulativeDistance")
    val cumulativeDistance: Double? = null,

    @Schema(description = "평균 속도 (m/min)", example = "248.8")
    @JsonProperty("averageSpeed")
    val averageSpeed: Double? = null,

    @Schema(description = "완주 여부", example = "true")
    @JsonProperty("isFinished")
    val isFinished: Boolean = false
)

/**
 * 에러 응답 DTO
 */
@Schema(description = "에러 응답")
data class EventDetailErrorResponseDto(
    @Schema(description = "에러 코드", example = "EVENT_NOT_FOUND")
    @JsonProperty("errorCode")
    val errorCode: String,

    @Schema(description = "에러 메시지", example = "해당 대회를 찾을 수 없습니다")
    @JsonProperty("errorMessage")
    val errorMessage: String,

    @Schema(description = "에러 시간", example = "2024-03-15T10:30:00Z")
    @JsonProperty("errorTime")
    val errorTime: String
)

// 기존 DTO들은 유지 (다른 API에서 사용될 수 있음)
/**
 * 대회 기본 정보 DTO (기존)
 */
@Schema(description = "대회 기본 정보")
data class EventInfoDto(
    @Schema(description = "이벤트 ID", example = "1")
    @JsonProperty("eventId")
    val eventId: Long,

    @Schema(description = "이벤트 상세 ID", example = "1")
    @JsonProperty("eventDetailId")
    val eventDetailId: Long,

    @Schema(description = "대회 이름", example = "2024 서울 마라톤")
    @JsonProperty("eventName")
    val eventName: String,

    @Schema(description = "대회 날짜", example = "2024-03-15")
    @JsonProperty("eventDate")
    val eventDate: String,

    @Schema(description = "대회 상태", example = "SCHEDULED")
    @JsonProperty("eventStatus")
    val eventStatus: String,

    @Schema(description = "대회 설명")
    @JsonProperty("description")
    val description: String? = null
)

/**
 * 코스 정보 DTO (기존)
 */
@Schema(description = "코스 정보")
data class CourseInfoDto(
    @Schema(description = "코스 ID", example = "course123")
    @JsonProperty("courseId")
    val courseId: String,

    @Schema(description = "GPX 파일 S3 URL", example = "https://s3.amazonaws.com/bucket/course.gpx")
    @JsonProperty("gpxFileUrl")
    val gpxFileUrl: String,

    @Schema(description = "코스 총 거리 (km)", example = "42.195")
    @JsonProperty("totalDistance")
    val totalDistance: Double,

    @Schema(description = "코스 시작점 위도", example = "37.5665")
    @JsonProperty("startLatitude")
    val startLatitude: Double,

    @Schema(description = "코스 시작점 경도", example = "126.9780")
    @JsonProperty("startLongitude")
    val startLongitude: Double,

    @Schema(description = "코스 종료점 위도", example = "37.5665")
    @JsonProperty("endLatitude")
    val endLatitude: Double,

    @Schema(description = "코스 종료점 경도", example = "126.9780")
    @JsonProperty("endLongitude")
    val endLongitude: Double,

    @Schema(description = "코스 포인트 수", example = "4219")
    @JsonProperty("totalPoints")
    val totalPoints: Int
)

/**
 * 참가자 순위 DTO (기존)
 */
@Schema(description = "참가자 순위 정보")
data class ParticipantRankingDto(
    @Schema(description = "순위", example = "1")
    @JsonProperty("rank")
    val rank: Long,

    @Schema(description = "사용자 ID", example = "user123")
    @JsonProperty("userId")
    val userId: String,

    @Schema(description = "점수", example = "1000001400.0")
    @JsonProperty("score")
    val score: Double,

    @Schema(description = "체크포인트 인덱스", example = "3")
    @JsonProperty("checkpointIndex")
    val checkpointIndex: Int,

    @Schema(description = "누적 시간 (초)", example = "1800")
    @JsonProperty("cumulativeTime")
    val cumulativeTime: Long,

    @Schema(description = "누적 시간 (포맷)", example = "30:00")
    @JsonProperty("cumulativeTimeFormatted")
    val cumulativeTimeFormatted: String,

    @Schema(description = "위치 정보")
    @JsonProperty("location")
    val location: ParticipantLocationDto
) 