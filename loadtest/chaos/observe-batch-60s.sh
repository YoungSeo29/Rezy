#!/bin/bash
# 앱을 실행 인자 없이 올려서(application.yml 의 60초) 배치 보정 과정을 관찰
NEW="$(cd "$(dirname "$0")" && pwd)"
MDB="/c/Program Files/MariaDB 11.5/bin/mariadb.exe"
# 앱 전체 로그는 140MB 가 넘는다. 배치 줄만 걸러 임시파일에 담고 끝나면 지운다.
APPLOG=$(mktemp); trap 'rm -f "$APPLOG"' EXIT
CF() { "$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -N -B -e "SELECT COUNT(*) FROM reservations WHERE slot_capacity_id='chaos-cap-01' AND status='CONFIRMED';" 2>/dev/null; }
RV() { local v; v=$(docker exec rezy-redis redis-cli get slot:cap:chaos-cap-01 2>/dev/null); [ -z "$v" ] && v="-"; echo "$v"; }

echo "===== 재기동 후 배치 보정 관찰 (실행 인자 없음 = application.yml 60초) ====="
echo "재기동 전 : Redis=$(RV)  CONFIRMED=$(CF)  기대=400  누수=$((400-$(CF)-$(RV)))"
echo
cd /c/WorkSpace/Rezy/Rezy
java -jar build/libs/Rezy-0.0.1-SNAPSHOT.jar 2>&1 \
  | grep --line-buffered -a "StockReconciliationBatch" > "$APPLOG" &
echo "[app] 기동 시작 (인자 없음)"
START=$(date +%s)
CORRECTED=""
for i in $(seq 1 30); do
  sleep 10
  T=$(( $(date +%s) - START ))
  V=$(RV)
  echo "  +${T}s  Redis=$V"
  if [ "$V" = "400" ] && [ -z "$CORRECTED" ]; then CORRECTED=$T; fi
  [ -n "$CORRECTED" ] && [ $T -gt $((CORRECTED+40)) ] && break
done
echo
echo "--- 배치 로그 ---"
iconv -f CP949 -t UTF-8 -c "$APPLOG" 2>/dev/null || cat "$APPLOG"
echo
echo "--- 배치 완료 간격 (주기 확인용) ---"
iconv -f CP949 -t UTF-8 -c "$APPLOG" 2>/dev/null | grep "검사" | awk '{print "  "$1}'
echo
echo "--- 최종 ---"
echo "총정원 400 / CONFIRMED $(CF) / Redis $(RV) / 누수 $((400-$(CF)-$(RV)))"
echo "보정까지 경과: ${CORRECTED:-미도달}초 (앱 기동 시점 기준)"
