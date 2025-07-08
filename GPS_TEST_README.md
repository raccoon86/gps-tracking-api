# GPS 테스트 데이터 생성 가이드

## 개요

제공해주신 GPX 데이터를 기반으로 `sendGpsData` API를 테스트할 수 있는 테스트 데이터 생성 도구를 만들었습니다.

## GPX 원본 데이터

```xml
<trk>
    <trkseg>
      <trkpt lat="37.5378907753666" lon="127.13999569416">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5344324221177" lon="127.138659954071">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5342239799585" lon="127.138155698776">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5342622652968" lon="127.137823104858">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5346281019819" lon="127.136685848236">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5350705067387" lon="127.13517844677">
        <ele>-1</ele>
      </trkpt>
      <trkpt lat="37.5359893390014" lon="127.132308483124">
        <ele>-1</ele>
      </trkpt>
    </trkseg>
</trk>
```

## 생성된 파일들

### 1. `GpsTestDataGenerator.kt`

- GPX 좌표를 기반으로 현실적인 GPS 테스트 데이터를 생성
- GPS 노이즈, 정확도, 속도, 방향 등을 시뮬레이션
- 연속적인 GPS 포인트 생성 가능

### 2. `TrackingControllerTest.kt`

- `sendGpsData` API에 대한 포괄적인 통합 테스트
- 단일 포인트, 전체 경로, 연속 데이터 테스트
- 전체 트래킹 플로우 테스트 (시작 → GPS 데이터 → 일시정지 → 재개 → 종료)

### 3. `run_gps_test.kt`

- 실제 서버 없이도 API 테스트 가능한 스크립트
- HTTP 클라이언트를 통한 실제 API 호출 시뮬레이션

## 사용 방법

### 방법 1: JUnit 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "TrackingControllerTest"

# GPS 테스트 데이터 출력 테스트만 실행
./gradlew test --tests "TrackingControllerTest.GPS 테스트 데이터 출력"
```

### 방법 2: 프로그래밍 방식 사용

```kotlin
import com.sponovation.runtrack.GpsTestDataGenerator

fun main() {
    // 기본 GPS 테스트 데이터 생성
    val sessionId = 1L
    val testData = GpsTestDataGenerator.generateGpsTestData(sessionId)

    // 콘솔에 출력
    GpsTestDataGenerator.printGpsTestData(sessionId)

    // 연속적인 GPS 데이터 생성 (더 세밀함)
    val continuousData = GpsTestDataGenerator.generateContinuousGpsData(
        sessionId = sessionId,
        pointsPerSegment = 5
    )

    // 각 GPS 데이터로 API 호출
    testData.forEach { gpsData ->
        // trackingService.processGpsData(gpsData)
        println("GPS: ${gpsData.latitude}, ${gpsData.longitude}")
    }
}
```

### 방법 3: HTTP API 직접 테스트

서버를 실행한 후:

```bash
# 스크립트에 실행 권한 부여
chmod +x run_gps_test.kt

# 스크립트 실행 (Kotlin 스크립팅 필요)
kotlin run_gps_test.kt
```

또는 `curl`을 사용한 수동 테스트:

```bash
# 1. 트래킹 세션 시작
curl -X POST http://localhost:8080/api/tracking/start \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-user","gpxRouteId":null}'

# 2. GPS 데이터 전송 (세션 ID를 위에서 받은 값으로 변경)
curl -X POST http://localhost:8080/api/tracking/gps \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": 1,
    "latitude": 37.5378907753666,
    "longitude": 127.13999569416,
    "altitude": 0.0,
    "accuracy": 10.0,
    "speed": 3.5,
    "bearing": 45.0,
    "timestamp": "2024-01-01T10:00:00"
  }'
```

## 생성되는 테스트 데이터 특징

### 실제 GPS 환경 시뮬레이션

- **위치 노이즈**: ±5미터 오차 추가
- **정확도**: 5-15미터 범위의 랜덤 값
- **속도**: 2-5 m/s (러닝 속도)
- **방향**: 다음 포인트로의 실제 방위각 계산
- **시간 간격**: 5-10초 간격 (실제 GPS 수신 주기)

### 고도 데이터 처리

- 원본 GPX의 `-1` 고도값을 `0.0`으로 변환
- 고도 변화 계산 및 상승량 추적

### 연속 데이터 생성

- 원본 포인트 사이에 중간 포인트 생성
- 보간법을 통한 자연스러운 경로 생성
- 더 세밀한 트래킹 시뮬레이션

## 테스트 시나리오

1. **단일 GPS 포인트 전송 테스트**
2. **전체 경로 순차 전송 테스트**
3. **연속적인 GPS 데이터 테스트**
4. **오류 시나리오 테스트** (잘못된 세션 ID 등)
5. **전체 트래킹 플로우 테스트** (시작 → 데이터 전송 → 일시정지 → 재개 → 종료)

## 주의사항

- 테스트 실행 전 서버가 실행 중이어야 합니다
- H2 인메모리 데이터베이스를 사용하므로 데이터는 테스트 후 삭제됩니다
- 실제 GPS 하드웨어 없이도 완전한 테스트가 가능합니다

## 확장 가능한 기능

- 다른 GPX 파일 데이터 추가
- 다양한 운동 타입별 속도 프로필
- GPS 신호 손실 시뮬레이션
- 배터리 최적화 시나리오 테스트

이제 제공해주신 GPX 데이터를 기반으로 `sendGpsData` API를 완전히 테스트할 수 있습니다! 🚀
