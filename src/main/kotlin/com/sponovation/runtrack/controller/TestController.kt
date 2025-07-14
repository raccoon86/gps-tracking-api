package com.sponovation.runtrack.controller

import com.sponovation.runtrack.service.CourseDataService
import com.sponovation.runtrack.service.EventCourse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test")
class TestController(
    private val courseDataService: CourseDataService
) {}