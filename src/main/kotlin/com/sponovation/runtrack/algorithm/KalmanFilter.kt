package com.sponovation.runtrack.algorithm

import kotlin.math.sqrt

/**
 * 2차원 위치 보정을 위한 칼만 필터
 * 
 * GPS 신호의 노이즈를 제거하고 더 정확한 위치를 추정합니다.
 * 위도(latitude)와 경도(longitude)를 독립적으로 필터링합니다.
 */
class KalmanFilter {
    
    // 상태 변수 (보정된 위치)
    private var stateLat: Double = 0.0
    private var stateLng: Double = 0.0
    
    // 오차 공분산 (추정 불확실성)
    private var pLat: Double = 1.0
    private var pLng: Double = 1.0
    
    // 프로세스 노이즈 (시스템 모델의 불확실성)
    private val q: Double = 0.001
    
    // 측정 노이즈 (GPS 측정의 불확실성)
    private val r: Double = 0.01
    
    // 초기화 여부
    private var initialized: Boolean = false
    
    /**
     * GPS 위치를 칼만 필터로 보정합니다.
     * 
     * @param lat 원본 위도
     * @param lng 원본 경도
     * @return 보정된 위도, 경도 쌍
     */
    fun filter(lat: Double, lng: Double): Pair<Double, Double> {
        if (!initialized) {
            // 첫 번째 측정값으로 초기화
            stateLat = lat
            stateLng = lng
            initialized = true
            return Pair(stateLat, stateLng)
        }
        
        // 위도 필터링
        val filteredLat = filterSingleDimension(lat, stateLat, pLat)
        stateLat = filteredLat.first
        pLat = filteredLat.second
        
        // 경도 필터링
        val filteredLng = filterSingleDimension(lng, stateLng, pLng)
        stateLng = filteredLng.first
        pLng = filteredLng.second
        
        return Pair(stateLat, stateLng)
    }
    
    /**
     * 1차원 칼만 필터 계산
     * 
     * @param measurement 측정값
     * @param state 이전 상태
     * @param p 이전 오차 공분산
     * @return 보정된 상태와 새로운 오차 공분산
     */
    private fun filterSingleDimension(
        measurement: Double, 
        state: Double, 
        p: Double
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
        pLat = 1.0
        pLng = 1.0
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
     * 현재 추정 불확실성을 반환합니다.
     * 
     * @return 위도, 경도의 오차 공분산 쌍
     */
    fun getUncertainty(): Pair<Double, Double> {
        return Pair(sqrt(pLat), sqrt(pLng))
    }
} 