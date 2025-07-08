package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*

@Entity
@Table(name = "gpx_waypoints")
data class GpxWaypoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(nullable = false)
    val elevation: Double,

    @Column(nullable = false)
    val sequence: Int,

    @Column
    val distanceFromStart: Double = 0.0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpx_route_id")
    @JsonBackReference("route-waypoints")
    val gpxRoute: GpxRoute? = null
) 
