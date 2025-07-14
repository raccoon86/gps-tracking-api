package com.sponovation.runtrack.algorithm

import kotlin.math.sqrt

/**
 * 3차원 위치 보정을 위한 칼만 필터
 * 
 * GPS 신호의 노이즈를 제거하고 더 정확한 위치를 추정합니다.
 * 위도(latitude), 경도(longitude), 고도(altitude)를 독립적으로 필터링합니다.
 */
class KalmanFilter {
    
    // 상태 변수 (보정된 위치)
    private var stateLat: Double = 0.0
    private var stateLng: Double = 0.0
    private var stateAlt: Double = 0.0
    
    // 오차 공분산 (추정 불확실성)
    private var pLat: Double = 1.0
    private var pLng: Double = 1.0
    private var pAlt: Double = 5.0  // 고도는 더 큰 초기 불확실성
    
    // 프로세스 노이즈 (시스템 모델의 불확실성)
    private val qPosition: Double = 0.001  // 위도, 경도
    private val qAltitude: Double = 0.01   // 고도 (더 큰 노이즈)
    
    // 측정 노이즈 (GPS 측정의 불확실성)
    private val rPosition: Double = 0.01   // 위도, 경도
    private val rAltitude: Double = 2.0    // 고도 (일반적으로 더 부정확)
    
    // 초기화 여부
    private var initialized: Boolean = false
    
    /**
     * GPS 위치를 칼만 필터로 보정합니다.
     * 
     * @param lat 원본 위도
     * @param lng 원본 경도
     * @param alt 원본 고도 (null인 경우 기존 값 유지)
     * @return 보정된 위도, 경도, 고도
     */
    fun filter(lat: Double, lng: Double, alt: Double? = null): Triple<Double, Double, Double?> {
        if (!initialized) {
            // 첫 번째 측정값으로 초기화
            stateLat = lat
            stateLng = lng
            stateAlt = alt ?: 0.0
            initialized = true
            return Triple(stateLat, stateLng, if (alt != null) stateAlt else null)
        }
        
        // 위도 필터링
        val filteredLat = filterSingleDimension(lat, stateLat, pLat, qPosition, rPosition)
        stateLat = filteredLat.first
        pLat = filteredLat.second
        
        // 경도 필터링
        val filteredLng = filterSingleDimension(lng, stateLng, pLng, qPosition, rPosition)
        stateLng = filteredLng.first
        pLng = filteredLng.second
        
        // 고도 필터링 (고도 값이 제공된 경우에만)
        val filteredAlt = if (alt != null) {
            val result = filterSingleDimension(alt, stateAlt, pAlt, qAltitude, rAltitude)
            stateAlt = result.first
            pAlt = result.second
            stateAlt
        } else {
            null
        }
        
        return Triple(stateLat, stateLng, filteredAlt)
    }
    
    /**
     * 2차원 GPS 위치를 칼만 필터로 보정합니다 (하위 호환성)
     * 
     * @param lat 원본 위도
     * @param lng 원본 경도
     * @return 보정된 위도, 경도 쌍
     */
    fun filter(lat: Double, lng: Double): Pair<Double, Double> {
        val result = filter(lat, lng, null)
        return Pair(result.first, result.second)
    }
    
    /**
     * 1차원 칼만 필터 계산
     * 
     * @param measurement 측정값
     * @param state 이전 상태
     * @param p 이전 오차 공분산
     * @param q 프로세스 노이즈
     * @param r 측정 노이즈
     * @return 보정된 상태와 새로운 오차 공분산
     */
    private fun filterSingleDimension(
        measurement: Double, 
        state: Double, 
        p: Double,
        q: Double,
        r: Double
    ): Pair<Double, Double> {
        
        // 예측 단계 (Prediction)
        val predictedState = state  // 등속 모델 가정
        val predictedP = p + q      // 프로세스 노이즈 추가
        
        // 업데이트 단계 (Update)
        val kalmanGain = predictedP / (predictedP + r)  // 칼만 게인 계산
        val updatedState = predictedState + kalmanGain * (measurement - predictedState)
        val updatedP = (1 - kalmanGain) * predictedP
        
        return Pair(updatedState, updatedP)
    }
    
    /**
     * 필터 상태를 초기화합니다.
     */
    fun reset() {
        stateLat = 0.0
        stateLng = 0.0
        stateAlt = 0.0
        pLat = 1.0
        pLng = 1.0
        pAlt = 5.0
        initialized = false
    }
    
    /**
     * 현재 필터링된 위치를 반환합니다.
     * 
     * @return 현재 보정된 위도, 경도 쌍 (초기화되지 않았으면 null)
     */
    fun getCurrentPosition(): Pair<Double, Double>? {
        return if (initialized) {
            Pair(stateLat, stateLng)
        } else {
            null
        }
    }
    
    /**
     * 현재 필터링된 3차원 위치를 반환합니다.
     * 
     * @return 현재 보정된 위도, 경도, 고도 (초기화되지 않았으면 null)
     */
    fun getCurrentPosition3D(): Triple<Double, Double, Double>? {
        return if (initialized) {
            Triple(stateLat, stateLng, stateAlt)
        } else {
            null
        }
    }
    
    /**
     * 현재 추정 불확실성을 반환합니다.
     * 
     * @return 위도, 경도의 오차 공분산 쌍
     */
    fun getUncertainty(): Pair<Double, Double> {
        return Pair(sqrt(pLat), sqrt(pLng))
    }
    
    /**
     * 현재 3차원 추정 불확실성을 반환합니다.
     * 
     * @return 위도, 경도, 고도의 오차 공분산
     */
    fun getUncertainty3D(): Triple<Double, Double, Double> {
        return Triple(sqrt(pLat), sqrt(pLng), sqrt(pAlt))
    }
    
    /**
     * 가중치 기반 위치 보정을 수행합니다.
     * 
     * GPS 정확도와 신뢰도를 고려하여 더 정교한 보정을 수행합니다.
     * 
     * @param lat 원본 위도
     * @param lng 원본 경도
     * @param alt 원본 고도
     * @param accuracy GPS 정확도 (미터)
     * @param confidence 신뢰도 (0.0 ~ 1.0)
     * @return 가중치 적용된 보정 결과
     */
    fun filterWithWeights(
        lat: Double, 
        lng: Double, 
        alt: Double? = null, 
        accuracy: Float? = null,
        confidence: Double = 1.0
    ): Triple<Double, Double, Double?> {
        
        // 정확도를 기반으로 측정 노이즈 조정
        val adjustedRPosition = if (accuracy != null && accuracy > 0) {
            maxOf(accuracy.toDouble() / 10.0, rPosition) // 정확도가 낮을수록 노이즈 증가
        } else {
            rPosition
        }
        
        val adjustedRAltitude = if (accuracy != null && accuracy > 0) {
            maxOf(accuracy.toDouble() / 5.0, rAltitude) // 고도는 상대적으로 부정확
        } else {
            rAltitude
        }
        
        // 신뢰도를 기반으로 칼만 게인 조정
        val confidenceWeight = confidence.coerceIn(0.1, 1.0)
        
        if (!initialized) {
            stateLat = lat
            stateLng = lng
            stateAlt = alt ?: 0.0
            initialized = true
            return Triple(stateLat, stateLng, if (alt != null) stateAlt else null)
        }
        
        // 신뢰도를 반영한 필터링
        val filteredLat = filterWithConfidence(lat, stateLat, pLat, qPosition, adjustedRPosition, confidenceWeight)
        stateLat = filteredLat.first
        pLat = filteredLat.second
        
        val filteredLng = filterWithConfidence(lng, stateLng, pLng, qPosition, adjustedRPosition, confidenceWeight)
        stateLng = filteredLng.first
        pLng = filteredLng.second
        
        val filteredAlt = if (alt != null) {
            val result = filterWithConfidence(alt, stateAlt, pAlt, qAltitude, adjustedRAltitude, confidenceWeight)
            stateAlt = result.first
            pAlt = result.second
            stateAlt
        } else {
            null
        }
        
        return Triple(stateLat, stateLng, filteredAlt)
    }
    
    /**
     * 신뢰도를 반영한 1차원 칼만 필터 계산
     */
    private fun filterWithConfidence(
        measurement: Double,
        state: Double,
        p: Double,
        q: Double,
        r: Double,
        confidence: Double
    ): Pair<Double, Double> {
        
        // 예측 단계
        val predictedState = state
        val predictedP = p + q
        
        // 신뢰도 반영된 측정 노이즈
        val adjustedR = r / confidence
        
        // 업데이트 단계
        val kalmanGain = predictedP / (predictedP + adjustedR)
        val updatedState = predictedState + kalmanGain * (measurement - predictedState)
        val updatedP = (1 - kalmanGain) * predictedP
        
        return Pair(updatedState, updatedP)
    }
} 