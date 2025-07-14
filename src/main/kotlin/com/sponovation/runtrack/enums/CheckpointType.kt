package com.sponovation.runtrack.enums

/**
 * 체크포인트 타입 열거형
 */
enum class CheckpointType {
    START,          // 시작
    SWIM,           // 수영
    TRANSITION_1,   // 전환구간 1 (수영→자전거)
    BIKE,           // 자전거
    TRANSITION_2,   // 전환구간 2 (자전거→달리기)
    RUN,            // 달리기
    FINISH,         // 완주
    INTERMEDIATE    // 중간 체크포인트
}

/**
 * 체크포인트 상세 정보 데이터 클래스
 */
data class CheckpointDetails(
    val type: CheckpointType,
    val number: String?,
    val sequence: Int?,
    val identifier: String,
    val originalCpId: String
) {
    /**
     * 체크포인트 표시명
     */
    fun getDisplayName(): String {
        return when (type) {
            CheckpointType.START -> "시작"
            CheckpointType.SWIM -> "수영" + (number?.let { " $it" } ?: "")
            CheckpointType.TRANSITION_1 -> "전환구간 1"
            CheckpointType.BIKE -> "자전거" + (number?.let { " $it" } ?: "")
            CheckpointType.TRANSITION_2 -> "전환구간 2"
            CheckpointType.RUN -> "달리기" + (number?.let { " $it" } ?: "")
            CheckpointType.FINISH -> "완주"
            CheckpointType.INTERMEDIATE -> "중간 체크포인트" + (number?.let { " $it" } ?: "")
        }
    }

    /**
     * 영문 표시명
     */
    fun getEnglishDisplayName(): String {
        return when (type) {
            CheckpointType.START -> "Start"
            CheckpointType.SWIM -> "Swim" + (number?.let { " $it" } ?: "")
            CheckpointType.TRANSITION_1 -> "Transition 1"
            CheckpointType.BIKE -> "Bike" + (number?.let { " $it" } ?: "")
            CheckpointType.TRANSITION_2 -> "Transition 2"
            CheckpointType.RUN -> "Run" + (number?.let { " $it" } ?: "")
            CheckpointType.FINISH -> "Finish"
            CheckpointType.INTERMEDIATE -> "Checkpoint" + (number?.let { " $it" } ?: "")
        }
    }
} 