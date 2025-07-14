package com.sponovation.runtrack.common

data class ApiResponse<T : Any>(val data: T?, val meta: Any? = null)