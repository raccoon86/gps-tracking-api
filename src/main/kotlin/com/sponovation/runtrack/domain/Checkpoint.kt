package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*

@Entity
@Table(name = "checkpoints")
data class Checkpoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(nullable = false)
    val radius: Double, // meters

    @Column(nullable = false)
    val sequence: Int,

    @Column
    val description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpx_route_id")
    @JsonBackReference("route-checkpoints")
    val gpxRoute: GpxRoute? = null,

    @OneToMany(mappedBy = "checkpoint", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("checkpoint-reaches")
    val checkpointReaches: List<CheckpointReach> = emptyList()
) 
