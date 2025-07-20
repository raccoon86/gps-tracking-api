package com.sponovation.runtrack.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "official_rfid_records"
)
data class OfficialRfidRecord(
    /**
     * 기본키 (Primary Key)
     * 자동 증가 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    /**
     * 이벤트 ID
     * 해당 RFID 기록이 속한 이벤트의 식별자
     */
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 체크포인트 ID
     * RFID가 감지된 체크포인트의 식별자
     */
    @Column(name = "checkpoint_id", length = 50, nullable = false)
    val checkpointId: String,

    /**
     * RFID 태그 ID
     * 참가자가 착용한 RFID 태그의 고유 식별자
     */
    @Column(name = "tag_id", length = 64, nullable = false)
    val tagId: String,

    /**
     * 통과 시간
     * 참가자가 해당 체크포인트를 통과한 시간
     */
    @Column(name = "pass_time", nullable = false)
    val passedTime: LocalDateTime,

    /**
     * 생성 일시
     * 시스템에 등록된 시간 (자동 생성)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 마지막으로 수정된 시간 (자동 업데이트)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)