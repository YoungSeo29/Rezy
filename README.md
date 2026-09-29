# Rezy

> 원하는 시간대에 식당을 예약하는 서비스
> 경합이 몰리는 상황의 동시성 제어와 조회 성능 개선에 집중한 백엔드 프로젝트

📄 **[상세 측정 기록 (포트폴리오)](https://scarce-viburnum-a2c.notion.site/REZY-_-3bbd1b8b4499805dba2ce9ade5ffdecd?source=copy_link)**

---

## 🛠 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Java 17 / Spring Boot 3.4.5 / Spring Data JPA / Spring Security / OAuth2 / JWT |
| Database | MariaDB / Redis |
| Infra | AWS EC2 (c5.xlarge 2대) / Docker Compose / GitHub Actions |
| Test | JUnit 5 / Mockito / AssertJ / Testcontainers / k6 |

---

## 🗂 ERD

<img width="1481" alt="Rezy ERD" src="https://github.com/user-attachments/assets/ceec94ee-9950-4949-a3c7-76d3fcf627a4" />

---

## 🔐 인증 / 인가

### 소셜 로그인 사용자 식별 — 이메일 대신 `(provider, provider_id)`

카카오는 이메일이 선택 동의 항목이라 사용자 식별자로 쓸 수 없다.
이메일로 식별하면 미동의 사용자가 재로그인할 때 계정을 찾지 못하는 문제가 생긴다.

→ `User` 엔티티에 소셜 플랫폼이 발급하는 고유 식별자 `providerId`를 저장하고,
`(provider, provider_id)` 복합 유니크 제약으로 계정을 식별하도록 설계

### Strategy 패턴으로 플랫폼별 응답 구조 통합

카카오와 네이버는 사용자 정보 응답 JSON 구조가 다르다.

→ `OAuth2UserInfo` 인터페이스로 추상화하고 `KakaoUserInfo` / `NaverUserInfo` 구현체 + 팩토리로 분리
→ 신규 플랫폼 추가 시 `CustomOAuth2UserService` 등 기존 로직 수정 없이 구현체만 추가하면 됨

---

## 🍽 예약 도메인 설계

### 인원별 정원 분리 (1 : N)

한 시간대에 "2인 3팀, 4인 2팀"처럼 인원수별로 정원이 나뉘므로 단일 컬럼으로 표현할 수 없다.

```
ReservationSlot (시간대)
├── SlotCapacity (2인 · 남은 3팀)
├── SlotCapacity (4인 · 남은 2팀)
└── SlotCapacity (6인 · 남은 1팀)
```

→ 인원 버킷마다 `remaining_teams`를 두고, 예약 시 해당 버킷만 차감

---

## ⚡ 성능 개선

> 측정 환경 : 부하 생성기와 서버를 EC2 2대로 분리 (같은 VPC)
> 로컬 1대 측정 시 CPU 93~100% 포화로 측정값 신뢰 불가 판단

### 1. 동시 예약 정합성 — 락 방식 4가지 비교

**문제** 200VU가 정원 3팀인 슬롯 하나에 동시 예약 → 실제 32건 생성 (29건 초과)
**원인** slow query 로그 9건에 모두 `remaining_teams=2` 기록 — Race Condition (Lost Update)

| 방식 | 정합성 | 집중형 median | 분산형 median |
|---|---|---|---|
| 락 없음 | ❌ 173건 유실 | 199ms | 130ms |
| synchronized | ⭕ | 976ms | 753ms |
| 비관적 락 | ⭕ | 429ms | 153ms |
| 원자적 UPDATE | ⭕ | 490ms | 140ms |

**최종 선택 : 비관적 락**
- `synchronized`는 무관한 요청까지 직렬화되고, JVM 단위 락이라 서버 확장 시 보장 불가
- 원자적 UPDATE는 왕복이 1회 줄었으나, 저장에 필요한 값을 차감 후 다시 조회해야 해 이점이 상쇄
- 두 방식의 성능이 대등해 확장성과 검증 조건 추가 가능성을 기준으로 판단

### 2. Redis 원자적 차감으로 락 경합 제거

**문제** 동시 사용자를 500명까지 늘려도 처리량이 540 TPS에서 증가하지 않음

**원인 분리**
- 커넥션 풀 10 → 50 으로 늘려도 537 → 540 TPS. 커넥션은 병목이 아님
- 비관적 락은 슬롯당 한 건씩만 처리 → 처리량 상한이 슬롯 수에 묶임
- 집중형(슬롯 1개) 측정 시 **CPU는 13%p 낮아졌는데 처리량은 18% 감소**
  → 자원이 남는데 처리량이 줄었다는 것은 병목이 락이라는 근거

**개선** 경합이 생기는 지점을 DB에서 Redis로 이동

```
[기존] 요청 → 유저 확인 → DB 락 획득 → 재고 차감 → INSERT → 커밋
[개선] 요청 → 유저 확인 → Redis DECR  → INSERT → 커밋
```

- `slot:cap:{버킷ID}` → 잔여 팀 수. 예약 시점에만 생성
- TTL 미설정 — 키가 사라지면 DB의 낡은 값을 읽어오기 때문
- DB 잔여 수량은 갱신하지 않음. 같이 UPDATE하면 락이 다시 걸리므로,
  DB 값이 실시간 값이 아니게 되는 것을 감수한 선택
- 조회 API도 Redis 값을 읽도록 변경, `MultiGet`으로 일괄 조회

**결과**

| | 비관적 락 | Redis |
|---|---|---|
| 분산형 (슬롯 10개) | 941 TPS / 490ms | 942 TPS / 489ms |
| 집중형 (슬롯 1개) | 774 TPS / 627ms | **955 TPS / 482ms** |
| 슬롯 감소 영향 | **-18%** | **+1.4%** |

→ 비관적 락의 처리량 상한은 슬롯 수에 묶여 있고, Redis는 슬롯 수와 무관하게 일정
→ 실제 서비스에서 문제가 되는 건 오픈 직후처럼 한 곳에 몰릴 때이므로 이 구간의 차이가 의미 있다고 판단

### 3. 조회 성능 개선 — N+1 제거 + 복합 인덱스

**발견** 요청당 쿼리 수를 측정하는 Custom Filter로 모니터링하던 중,
22개 시간대 조회에 쿼리 24개가 나가고 **시간대 수에 비례해 증가**하는 것을 확인

**원인**
- `slot.getCapacities()` 호출마다 버킷 조회 쿼리가 개별 발생 (N+1)
- EXPLAIN 확인 중 하루치 22행만 필요한데 660행을 스캔하는 것도 발견
  — `store_id` 단일 인덱스만 존재해 날짜 조건이 인덱스로 걸러지지 않음

**해결** fetch join + `(store_id, slot_datetime)` 복합 인덱스

| 구분 | 개선 전 | 개선 후 |
|---|---|---|
| 22시간대 조회 | 쿼리 24개 / 50ms | **쿼리 2개 / 13ms** |
| 12시간대 조회 | 쿼리 14개 / 30ms | **쿼리 2개 / 13ms** |
| 스캔 행 수 | 660 | **22** |
| EXPLAIN type | ref (Using filesort) | **range (Using index)** |

---

## 🧪 테스트

단위 4개 / 통합 8개 — GitHub Actions에서 push마다 자동 수행
Testcontainers로 실제 MariaDB · Redis 컨테이너를 띄워 검증

| 구분 | 검증 항목 |
|---|---|
| 단위 (Mockito) | 하루 1건 예약 제한, 권한 검사, 재고 음수 시 복구, 슬롯 생성 경계값 |
| 통합 (Testcontainers) | 정원 3팀에 100스레드 동시 요청, 같은 유저 동시 2건, 취소 시 재고 복구, 조회 쿼리 수 고정 |

### 테스트로 발견한 동시성 결함 2건

**결함 1 — 같은 유저가 같은 날 2건 예약 가능** `expected: 1 but was: 2`
예약 여부 확인과 저장 사이에 잠금이 없어 중복 요청이 통과

→ `(user_id, active_date)` 유니크 제약으로 DB가 차단
→ 취소 시 `active_date`를 NULL로 변경. MariaDB는 NULL 중복을 허용하므로 재예약은 가능
→ 애플리케이션 로직과 무관하게 보장되고, 서버가 여러 대여도 DB는 1개라 유효

**결함 2 — 실패한 예약의 재고가 복구되지 않음** `expected: 5 but was: 4`
`try/catch`가 메서드 안만 감싸고 있어, 메서드 종료 후 실행되는 커밋 시점의 실패를 잡지 못함

→ `afterCompletion`으로 트랜잭션 롤백 시 Redis 재고를 복구하도록 변경

---

## 🚀 개발 환경 및 배포

**Docker Compose**
EC2에 MariaDB, Redis를 각각 설치하는 대신 Compose 파일로 정의해 실행.
인스턴스를 새로 만들거나 재시작해도 동일한 환경을 재현할 수 있다.

**GitHub Actions**
- push 시 테스트 자동 실행
- 배포는 `workflow_dispatch` 수동 트리거로 분리
- `needs`로 의존을 설정해 테스트 job이 통과해야 배포 job이 시작
- 측정용 서버를 상시 가동하지 않아, push마다 배포를 시도하면 불필요한 실패가 누적되므로 분리

---

## ▶️ 실행 방법

```bash
# 1. 환경 변수 파일 생성 (.env.example 참고)
cp .env.example .env

# 2. 실행
docker compose -f docker-compose.prod.yml up -d --build
```

필요 환경 변수

```
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
```

---

## 📌 남은 과제

- DB `remaining_teams` 동기화 배치 (Redis 도입으로 실시간 값이 아니게 됨)
- 지난 날짜 슬롯의 Redis 키 정리 배치
- Redis 왕복 2회(`hasKey` + `DECR`)를 Lua 스크립트로 1회 단축
- 서버 2대 + nginx 구성으로 스케일 아웃 시 정합성 검증