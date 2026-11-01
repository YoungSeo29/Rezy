#!/bin/bash
MDB="/c/Program Files/MariaDB 11.5/bin/mariadb.exe"
read TOTAL CONFIRMED <<< $("$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -N -B -e "
SELECT (SELECT total_teams FROM slot_capacities WHERE slot_capacity_id='chaos-cap-01'),
       (SELECT COUNT(*) FROM reservations WHERE slot_capacity_id='chaos-cap-01' AND status='CONFIRMED');" 2>/dev/null)
RV=$(docker exec rezy-redis redis-cli get slot:cap:chaos-cap-01 2>/dev/null)
[ -z "$RV" ] && RV="(키없음)"
EXPECT=$((TOTAL - CONFIRMED))
echo "총정원(total_teams)      : $TOTAL"
echo "확정예약(CONFIRMED 행수) : $CONFIRMED      <= 정답의 기준"
echo "기대 재고(total-CONFIRMED): $EXPECT"
echo "Redis 실제 값            : $RV"
if [ "$CONFIRMED" -gt "$TOTAL" ]; then echo "판정: ** 오버부킹 $((CONFIRMED-TOTAL))건 **"; else echo "판정: 오버부킹 없음"; fi
if [ "$RV" = "(키없음)" ]; then echo "정합성: 키 없음 (판정 불가)"
elif [ "$RV" -eq "$EXPECT" ]; then echo "정합성: Redis = 기대값  (일치)"
else echo "정합성: Redis($RV) != 기대값($EXPECT)   차이 $((RV-EXPECT))"; fi
