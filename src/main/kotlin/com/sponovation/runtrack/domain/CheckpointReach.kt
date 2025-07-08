package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "checkpoint_reaches")
data class CheckpointReach(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id")
    @JsonBackReference("checkpoint-reaches")
    val checkpoint: Checkpoint,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracking_session_id")
    @JsonBackReference("session-reaches")
    val trackingSession: TrackingSession,

    @Column(nullable = false)
    val reachedAt: LocalDateTime,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(nullable = false)
    val distanceToCheckpoint: Double // 실제 체크포인트까지의 거리 (미터)
) 
