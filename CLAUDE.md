# Rezy

레스토랑 예약 플랫폼. 백엔드 포트폴리오 프로젝트. 개발자 1인(본인).

## 스택
- Spring Boot 3.4.5 / Java 17 / Gradle
- MariaDB 11.5 / Redis 7.4
- Docker Compose / AWS EC2 / GitHub Actions

## 환경변수
`.env` + spring-dotenv 로 주입한다.
`application-local.yml` 같은 프로파일 yml을 만들지 마라.

Spring Boot 4.x 로 올리지 마라. spring-dotenv가 동작하지 않아
`Driver claims to not accept jdbcUrl, ${DB_URL}` 로 터진다.

## 빌드 설정
`build.gradle`의 `ext['testcontainers.version']`을 낮추거나 지우지 마라.
Spring Boot BOM이 고정하는 버전은 최신 Docker Engine과 프로토콜이 맞지 않아
통합 테스트가 `Could not find a valid Docker environment` 로 전부 실패한다.

## 도메인 설계 규칙
- 모든 PK는 VARCHAR(36) UUID. `@PrePersist`에서 생성.
- 엔티티에 setter 금지. 정적 팩토리(`create...`)와 의미 있는 메서드만 둔다.
- 연관관계는 `FetchType.LAZY` 고정.
- 예약 하루 1건 제한은 `(user_id, active_date)` 유니크 제약으로 구현.
  취소 시 `active_date`를 NULL로 만든다 (MariaDB는 NULL 중복을 허용).

## 재고 관리 — 가장 중요한 영역

예약 재고의 실시간 값은 **Redis**다. 키는 `slot:cap:{slotCapacityId}`.

### 예약 흐름
- `DECR` → 음수면 `INCR`로 되돌리고 예외
- 롤백 보상은 `afterCompletion(STATUS_ROLLED_BACK)`.
  `afterCommit`은 커밋 실패를 못 잡으므로 쓰지 마라.
- 취소 시 복구는 `afterCommit`. (취소는 커밋 성공 후에만 복구해야 한다)

### 정답의 기준
팔린 자리 수의 유일한 근거는 **`reservations` 테이블의 CONFIRMED 행 수**다.

- `slot_capacities.total_teams` — 변하지 않는 설정값. 참조만 한다.
- `slot_capacities.remaining_teams` — **신뢰하지 마라.**
  X-lock 회피를 위해 예약 시 갱신하지 않기로 한 컬럼이다.
  이 값을 재고로 쓰면 Redis 소실 후 재고가 총정원으로 리셋되어
  오버부킹이 발생한다. (실제로 발생했던 버그)

### Redis 키 복구
`loadStockIfAbsent`는 반드시 `total_teams - CONFIRMED 예약 수`로 계산한다.
`remaining_teams`를 읽도록 되돌리지 마라.

`set`이 아니라 `setIfAbsent`를 쓴다. 동시에 두 요청이 복구를 시도할 때
나중 것이 앞선 요청의 DECR을 덮어쓰는 것을 막는다.

### DB 동기화
"DB와 Redis가 안 맞는다"며 `remaining_teams`를 갱신하는 코드를 추가하지 마라.
의도된 설계다. 불일치는 정합성 검증 배치로 수렴시킨다.

### 조회
응답은 Redis 값 우선, 키가 없으면 DB 값으로 폴백.
슬롯 조회에 Redis 캐시를 붙이지 마라. 이미 13ms로 캐싱할 문제가 없다.

## 배포
EC2에서:
docker compose -f docker-compose.prod.yml up -d --build

`-f` 를 빼면 로컬 dev용 `docker-compose.yml`(redis만 있음)이 떠서
app과 mariadb가 올라오지 않는다. 반드시 붙여라.

Redis는 현재 `--save ""` + `--appendonly no` 로 영속성이 꺼져 있다.
부하 테스트 측정 순수성을 위한 의도적 설정이다.
재시작하면 재고 키가 전부 사라지므로, 복구 로직의 정확성이 중요하다.

## 테스트
- 통합 테스트는 `IntegrationTestSupport` 상속. Testcontainers(MariaDB + Redis).
  정리는 부모의 `@AfterEach cleanUp()`이 처리한다. 따로 만들지 마라.
- 동시성 테스트는 `ExecutorService` + `CountDownLatch`.
- `ReservationServiceTest`는 Mockito 단위 테스트다.
  활성 트랜잭션이 없어 `TransactionSynchronizationManager.registerSynchronization`
  에서 터지므로, 거기 도달하기 전에 끝나는 **예외 경로만** 검증한다.
  성공 경로가 필요하면 통합 테스트로 작성하라.

## 성능 관련 결정 사항
- 슬롯 조회는 fetch join + distinct 로 N+1을 해결했다. 되돌리지 마라.
- `(store_id, slot_datetime)` 복합 인덱스가 있다.
- 커넥션 풀은 API와 배치가 공유한다. 배치 작업은 트랜잭션을 길게 잡지 마라.

## 커밋
커밋은 직접 하지 않는다. 변경 사항을 설명하고 멈춰라.