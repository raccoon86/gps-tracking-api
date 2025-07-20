package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.OfficialRfidRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OfficialRfidRecordRepository : JpaRepository<OfficialRfidRecord, Long>