---
description: 장애 재현(A Redis 재시작 / B 앱 강제 종료 / C Redis 지속 다운) 후 재고 정합성 확인. 수정 전후 비교용
---

시나리오 $ARGUMENTS 를 아래 절차 그대로 재현해줘. 절차를 바꾸지 마라.
src/ 코드는 수정하지 않는다.

## 공통
- 판정 기준 : reservations 의 CONFIRMED 행 수
    - 오버부킹 = CONFIRMED > total_teams
    - 정합성 = Redis 값 == total_teams - CONFIRMED
- 테스트 데이터 : chaos-* 픽스처 사용 (슬롯 chaos-slot-01, 버킷 chaos-cap-01, 사용자 chaos-0001~0400)
    - 슬롯 날짜는 내일로. 배치는 오늘~7일 후만 검사한다
    - 없으면 /api/auth/signup 으로 사용자와 토큰을 새로 만든다. loadtest/tokens.json 은 EC2 전용이라 로컬에서 못 쓴다
- 측정 전 대상 버킷의 예약과 Redis 키를 초기화한다
- 결과가 예상과 다르면 해석 전에 원본 출력부터 보여준다

## A. Redis 재시작
1. 부하 중 docker restart rezy-redis
2. 확인 : 오버부킹, 정합성, 재시작 직후 슬롯 조회 API 응답(remainingTeams, available)

## B. 앱 강제 종료 (재고 누수)
1. 배치 주기는 실행 인자로 낮춘다 : --app.stock-reconciliation.interval-ms=30000
2. Redis 키를 total_teams 로 미리 세팅
3. 별도 세션에서 대상 버킷 행에 X-lock 을 걸고 유지
   START TRANSACTION;
   SELECT slot_capacity_id FROM slot_capacities WHERE slot_capacity_id='chaos-cap-01' FOR UPDATE;
4. 400건 동시 요청 → DECR 은 통과하고 INSERT 에서 멈춘다
5. 그 상태에서 앱 강제 종료, 포트가 닫혔는지 확인
6. 락 해제 후 누수량 확인 (기대 : 커넥션 풀 크기만큼)
7. 앱 재기동 후 배치 2주기 이상 관찰, [재고검증] 로그와 보정까지 걸린 시간 기록

하지 마라
- 고정 시간 뒤 kill : 부하가 먼저 끝나서 누수가 안 생긴다
- 락 없이 동시 요청 후 kill : 요청들이 DECR 이전 단계에서 커넥션을 기다리고 있어서 누수가 안 생긴다
- 테이블 락 : DECR 이전 단계도 slot_capacities 를 읽어서 DECR 자체가 안 일어난다

## C. Redis 지속 다운
1. 부하 중 docker stop rezy-redis, 24초 유지 후 docker start
2. 다운 중 별도로 단건 호출해서 응답 시간과 코드 기록
    - 슬롯 조회 (Redis 사용)
    - 예약 (Redis 사용)
    - 로그인 (Redis 미사용, 대조군)
3. 확인 : 오버부킹, 정합성, 앱 로그 ERROR 건수

하지 마라
- 이미 예약한 토큰으로 예약 테스트 : 하루 1건 검사에서 Redis 전에 409로 끝난다
  → 예약 이력 없는 토큰을 쓰고, Redis 정상일 때 201이 나오는지 먼저 확인한다

## 보고 형식
| 항목 | 값 |
|---|---|
| 커밋 | |
| 시나리오 | |
| 오버부킹 | |
| 정합성 (Redis / 기대값) | |
| 시나리오별 핵심 수치 (A: 조회 응답 / B: 누수량, 보정 시간 / C: 다운 중 응답 시간, 코드) | |

수치는 실제 출력에서만 가져온다. 없으면 "미측정".