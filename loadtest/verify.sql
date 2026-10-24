-- 결과가 1행이라도 나오면 초과 예약 발생
SELECT sc.slot_capacity_id,
       sc.total_teams,
       COUNT(r.reservation_id) AS confirmed
FROM slot_capacities sc
         JOIN reservations r
              ON r.slot_capacity_id = sc.slot_capacity_id
                  AND r.status = 'CONFIRMED'
GROUP BY sc.slot_capacity_id, sc.total_teams
HAVING COUNT(r.reservation_id) > sc.total_teams;