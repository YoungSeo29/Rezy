#!/bin/bash
# 부모 행 X-lock → FK 때문에 reservations INSERT 만 막힌다 (DECR 이후 단계)
S="$(cd "$(dirname "$0")" && pwd)"
MDB="/c/Program Files/MariaDB 11.5/bin/mariadb.exe"
APP_PID=$1
K6LOG=$(mktemp); trap 'rm -f "$K6LOG"' EXIT
snap() {
  local RV CF
  RV=$(docker exec rezy-redis redis-cli get slot:cap:chaos-cap-01 2>/dev/null); [ -z "$RV" ] && RV="(키없음)"
  CF=$("$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -N -B -e "SELECT COUNT(*) FROM reservations WHERE slot_capacity_id='chaos-cap-01' AND status='CONFIRMED';" 2>/dev/null)
  echo "[$1] $(date +%T.%N | cut -c1-12)  Redis=$RV  CONFIRMED=$CF"
}
echo "===== 시나리오 B-5 : INSERT 지연 중 앱 강제 종료 (DECR~커밋 구간 강제 확장) ====="
bash "$S/reset.sh" 400 >/dev/null
docker exec rezy-redis redis-cli set slot:cap:chaos-cap-01 400 >/dev/null 2>&1
echo "[reset] 예약 삭제 / 정원 400 / Redis 키 400 으로 사전 세팅"
snap "시작전"

echo "[stall] $(date +%T.%N | cut -c1-12)  부모 행 X-lock 획득 (14초 유지) → INSERT 만 차단"
"$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -e "
START TRANSACTION;
SELECT slot_capacity_id FROM slot_capacities WHERE slot_capacity_id='chaos-cap-01' FOR UPDATE;
SELECT SLEEP(14);
COMMIT;" >/dev/null 2>&1 &
LOCKER=$!
sleep 1

cd "$S"
k6 run -e TOKENS=./tokens-chaos.json -e CAP=chaos-cap-01 -e VUS=400 \
   --quiet reserve-burst.js > "$K6LOG" 2>&1 &
K6=$!
sleep 5
snap "락유지중(INSERT 대기)"
echo "[chaos] $(date +%T.%N | cut -c1-12)  앱 강제 종료"
taskkill //F //PID "$APP_PID" 2>&1
snap "kill직후"
echo "[port] 8080 LISTEN: $(netstat -ano 2>/dev/null | grep ':8080' | grep -c LISTENING)"
wait $LOCKER 2>/dev/null; echo "[stall] 락 해제"
wait $K6 2>/dev/null; echo "[k6] 종료"
sleep 4
snap "최종"
echo; echo "--- k6 결과 ---"
grep -E "ok_201|soldout_409|dup_409|other_4xx|server_5xx|conn_error_0|http_reqs" "$K6LOG"
echo; echo "--- 판정 ---"
read TOTAL CONFIRMED <<< $("$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -N -B -e "
SELECT (SELECT total_teams FROM slot_capacities WHERE slot_capacity_id='chaos-cap-01'),
       (SELECT COUNT(*) FROM reservations WHERE slot_capacity_id='chaos-cap-01' AND status='CONFIRMED');" 2>/dev/null)
RV=$(docker exec rezy-redis redis-cli get slot:cap:chaos-cap-01 2>/dev/null)
echo "총정원 $TOTAL / CONFIRMED $CONFIRMED / 기대재고 $((TOTAL-CONFIRMED)) / Redis $RV"
echo "누수(기대-실제) = $((TOTAL-CONFIRMED-RV))  <= 양수면 팔 수 있는 자리가 증발"
