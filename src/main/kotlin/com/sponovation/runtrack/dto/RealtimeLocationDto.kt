package com.sponovation.runtrack.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 실시간 위치 조회 응답 최상위 DTO
 * 
 * 대회 참가자들의 실시간 위치 조회 API의 응답 구조를 정의합니다.
 * 표준화된 응답 형식을 제공하여 클라이언트에서 일관된 데이터 처리가 가능합니다.
 * 
 * 응답 구조:
 * - data: 실제 위치 데이터를 포함하는 컨테이너
 * - 향후 meta, pagination 등의 정보 추가 가능
 * 
 * 사용 시나리오:
 * - 대회 실시간 중계 화면
 * - 참가자 가족/친구들의 위치 추적
 * - 대회 운영진의 현황 모니터링
 */
data class RealtimeLocationResponseDto(
    /** 실시간 위치 데이터 컨테이너 */
    @JsonProperty("data")
    val data: RealtimeLocationDataDto
)

/**
 * 실시간 위치 데이터 컨테이너 DTO
 * 
 * 참가자들의 위치 정보와 상위 순위자 정보를 함께 제공하는 데이터 구조입니다.
 * 지도 표시용 전체 참가자 위치와 리더보드용 상위 순위를 분리하여 관리합니다.
 * 
 * 구성 요소:
 * - participants: 모든 참가자의 현재 위치 (지도 표시용)
 * - top3: 상위 3명의 순위 정보 (리더보드 표시용)
 */
data class RealtimeLocationDataDto(
    /** 
     * 전체 참가자 위치 목록
     * 지도에 표시할 모든 참가자들의 현재 위치 정보
     * 실시간 업데이트되며, 일정 시간 이상 위치 갱신이 없는 참가자는 제외
     */
    @JsonProperty("participants")
    val participants: List<ParticipantLocationDto>,
    
    /** 
     * 상위 3명 순위 정보
     * 현재 진행률 기준 상위 3명의 순위, 기록, 경과 시간 등
     * 경쟁 요소 제공 및 스포츠 중계 효과 증대
     */
    @JsonProperty("top3")
    val top3: List<RankingDto>
)

/**
 * 참가자 위치 정보 DTO
 * 
 * 개별 참가자의 실시간 위치와 현재 상태 정보를 담는 핵심 데이터 객체입니다.
 * 지도상의 마커 표시 및 참가자 식별에 필요한 모든 정보를 포함합니다.
 * 
 * 위치 정보:
 * - lat, lng: 지도 표시를 위한 기본 좌표
 * - alt: 고도 정보 (3D 지도 표시 시 활용)
 * - heading: 이동 방향 (화살표 방향 표시)
 * - speed: 현재 속도 (실시간 상태 표시)
 * 
 * 식별 정보:
 * - userId: 시스템 내부 식별자
 * - nickname: 화면 표시용 참가자 이름
 * - timestamp: 위치 갱신 시간 (최신성 확인)
 */
data class ParticipantLocationDto(
    /** 
     * 사용자 고유 ID
     * 시스템 내부에서 참가자를 식별하는 유일한 키
     */
    @JsonProperty("userId")
    val userId: Long,
    
    /** 
     * 참가자 닉네임
     * 지도상 마커나 목록에 표시될 참가자 식별명
     * 개인정보 보호를 위해 실명 대신 닉네임 사용 권장
     */
    @JsonProperty("nickname")
    val nickname: String,
    
    /** 
     * 현재 위도
     * 지도상 마커 표시를 위한 위도 좌표
     * 보정된 위치 또는 원본 GPS 위치
     */
    @JsonProperty("lat")
    val lat: Double,
    
    /** 
     * 현재 경도
     * 지도상 마커 표시를 위한 경도 좌표
     * 보정된 위치 또는 원본 GPS 위치
     */
    @JsonProperty("lng")
    val lng: Double,
    
    /** 
     * 현재 고도 (선택적)
     * 3D 지도 표시나 고도 차트 그리기에 활용
     * null인 경우 고도 정보 미제공
     */
    @JsonProperty("alt")
    val alt: Double?,
    
    /** 
     * 이동 방향 (선택적)
     * 북쪽 기준 시계방향 각도 (0-360도)
     * 지도상에서 참가자 이동 방향 화살표 표시에 활용
     */
    @JsonProperty("heading")
    val heading: Double?,
    
    /** 
     * 현재 속도 (선택적)
     * 미터/초 단위의 이동 속도
     * 실시간 상태 표시 및 페이스 계산에 활용
     */
    @JsonProperty("speed")
    val speed: Double?,
    
    /** 
     * 위치 갱신 시간
     * 마지막으로 위치가 업데이트된 시간
     * ISO-8601 형식 문자열 (예: "2024-01-01T12:30:45")
     */
    @JsonProperty("timestamp")
    val timestamp: String
)

/**
 * 순위 정보 DTO
 * 
 * 대회 상위 순위자들의 현재 순위와 기록 정보를 담는 데이터 객체입니다.
 * 리더보드 표시 및 경쟁 요소 제공을 위한 핵심 정보를 포함합니다.
 * 
 * 순위 산정 기준:
 * - 기본: 경로 진행률 (시작점으로부터의 거리)
 * - 추가: 경과 시간, 평균 속도 등 고려 가능
 * 
 * 표시 정보:
 * - rank: 현재 순위 (1, 2, 3등)
 * - 참가자 정보: 이름, 배번 등
 * - 기록 정보: 경과 시간 등
 */
data class RankingDto(
    /** 
     * 현재 순위
     * 1등부터 시작하는 순차적 순위
     * 동점자 처리 규칙에 따라 동일 순위 가능
     */
    @JsonProperty("rank")
    val rank: Int,
    
    /** 
     * 참가자 사용자 ID
     * 상세 정보 조회를 위한 식별자
     */
    @JsonProperty("userId")
    val userId: Long,
    
    /** 
     * 참가자 닉네임
     * 리더보드에 표시될 참가자 이름
     */
    @JsonProperty("nickname")
    val nickname: String,
    
    /** 
     * 배번 (Bib Number)
     * 대회 참가자에게 부여되는 고유 번호
     * 공식 기록 및 신원 확인에 활용
     */
    @JsonProperty("bibNumber")
    val bibNumber: String,
    
    /** 
     * 경과 시간
     * 대회 시작부터 현재까지의 소요 시간
     * "HH:MM:SS" 형식으로 표시 (예: "01:23:45")
     */
    @JsonProperty("elapsedTime")
    val elapsedTime: String
)

/**
 * Redis 캐시용 참가자 위치 데이터
 * 
 * Redis에 저장되는 참가자 위치 정보의 완전한 데이터 구조입니다.
 * API 응답용 DTO보다 더 많은 내부 처리 정보를 포함합니다.
 * 
 * 용도:
 * - Redis 캐시 저장/조회
 * - 내부 계산 및 처리
 * - API 응답 DTO 생성의 원본 데이터
 * 
 * 확장 정보:
 * - 원본 GPS 좌표와 보정된 좌표 분리
 * - 진행률 계산을 위한 추가 메트릭
 * - 캐시 효율성을 위한 플랫 구조
 */
data class ParticipantLocationCache(
    /** 참가자 사용자 ID */
    val userId: Long,
    
    /** 대회/이벤트 상세 ID */
    val eventDetailId: Long,
    
    /** 참가자 닉네임 */
    val nickname: String,
    
    /** 원본 GPS 위도 */
    val lat: Double,
    
    /** 원본 GPS 경도 */
    val lng: Double,
    
    /** 고도 정보 (선택적) */
    val alt: Double? = null,
    
    /** 이동 방향 (선택적) */
    val heading: Double? = null,
    
    /** 이동 속도 (선택적) */
    val speed: Double? = null,
    
    /** 위치 갱신 시간 (ISO-8601 문자열) */
    val timestamp: String,
    
    /** 
     * 보정된 위도
     * 칼만 필터 및 맵 매칭을 통해 보정된 정확한 위도
     * API 응답 시 lat 대신 이 값을 사용 권장
     */
    val correctedLat: Double,
    
    /** 
     * 보정된 경도
     * 칼만 필터 및 맵 매칭을 통해 보정된 정확한 경도
     * API 응답 시 lng 대신 이 값을 사용 권장
     */
    val correctedLng: Double,
    
    /** 
     * 시작점으로부터의 거리 (미터 단위)
     * 진행률 계산 및 순위 산정에 활용
     * 경로를 따라 측정한 실제 이동 거리
     */
    val distanceFromStart: Double = 0.0,
    
    /** 
     * 경과 시간 (초 단위)
     * 개인별 시작 시간 기준 현재까지의 소요 시간
     * 페이스 계산 및 기록 관리에 활용
     */
    val elapsedTimeSeconds: Long = 0
) 

/**
 * 실시간 위치 정보 DTO
 * 
 * Redis Hash에 저장되는 실시간 위치 정보의 완전한 데이터 구조입니다.
 * RealtimeLocationHashService에서 사용되며, 참가자의 실시간 위치 추적에 필요한 
 * 모든 정보를 포함합니다.
 * 
 * 주요 기능:
 * - 원본 GPS 데이터와 보정된 위치 정보 분리 저장
 * - 누적 거리 및 시간 계산 정보 포함
 * - 체크포인트 도달 여부 추적
 * - Redis Hash 저장/조회 최적화
 */
data class RealtimeLocationDto(
    /** 사용자 ID */
    val userId: String,
    
    /** 이벤트 ID */
    val eventId: String,
    
    /** 이벤트 상세 ID */
    val eventDetailId: String,
    
    /** 원본 GPS 위도 */
    val rawLatitude: Double,
    
    /** 원본 GPS 경도 */
    val rawLongitude: Double,
    
    /** 원본 GPS 고도 (선택적) */
    val rawAltitude: Double? = null,
    
    /** 원본 GPS 정확도 (선택적) */
    val rawAccuracy: Double? = null,
    
    /** 원본 GPS 시간 (Unix 타임스탬프) */
    val rawTime: Long,
    
    /** 원본 GPS 속도 (선택적) */
    val rawSpeed: Double? = null,
    
    /** 보정된 위도 */
    val correctedLatitude: Double,
    
    /** 보정된 경도 */
    val correctedLongitude: Double,
    
    /** 보정된 고도 (선택적) */
    val correctedAltitude: Double? = null,
    
    /** 마지막 업데이트 시간 (Unix 타임스탬프) */
    val lastUpdated: Long,
    
    /** 이동 방향 (선택적) */
    val heading: Double? = null,
    
    /** 누적 이동 거리 (미터) */
    val distanceCovered: Double? = null,
    
    /** 누적 시간 (초) */
    val cumulativeTime: Long? = null,
    
    /** 가장 멀리 도달한 체크포인트 ID */
    val farthestCpId: String? = null,
    
    /** 가장 멀리 도달한 체크포인트 인덱스 */
    val farthestCpIndex: Int? = null,
    
    /** 가장 멀리 도달한 체크포인트에서의 누적 시간 */
    val cumulativeTimeAtFarthestCp: Long? = null
) 