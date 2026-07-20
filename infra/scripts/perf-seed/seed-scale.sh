#!/usr/bin/env bash
# 전 테이블 스케일업 시딩 — 각 테이블을 x1 × scale 로 채운다(원본 유지 + seed 추가).
# 전제: 대상 컨테이너의 qaskerdb 에 x1 순수 원본이 복원돼 있어야 한다(seed 없는 상태).
# 사용: seed-scale.sh <container> <scale>   예) seed-scale.sh local-mysql-x10 10
set -euo pipefail
C="${1:?container}"; SCALE="${2:?scale}"
DIR="$(cd "$(dirname "$0")" && pwd)"
MY(){ docker exec -i -e MYSQL_PWD=password "$C" mysql --default-character-set=utf8mb4 -uroot qaskerdb; }

echo "[seed] $C scale=$SCALE — 작은 테이블(user·problem_set·quiz_history·essay·refresh·board·feedback)"
{ echo "SET @scale=$SCALE;"; cat "$DIR/seed-scale-small.sql"; } | MY

PS_TOTAL=$(( 4553 * (SCALE - 1) ))   # seed problem_set 총수
STEP=40000                            # 배치당 40,000 세트 × 16 = 640,000 문항(< 100만 시퀀스 상한)
echo "[seed] problem 배치 — problem_set 0~$PS_TOTAL, step $STEP (배치당 $((STEP*16)) 문항)"
from=0
while [ "$from" -lt "$PS_TOTAL" ]; do
  to=$(( from + STEP )); [ "$to" -gt "$PS_TOTAL" ] && to=$PS_TOTAL
  { echo "SET @ps_from=$from; SET @ps_to=$to;"; cat "$DIR/seed-scale-problem.sql"; } | MY
  echo "  problem_set [$from,$to)  문항 +$(( (to-from)*16 ))"
  from=$to
done

echo "[seed] 검증 — 각 테이블 총량(원본+seed) vs 기대(x1×$SCALE)"
MY <<SQL
SELECT 'user' t, COUNT(*) actual, 166*$SCALE expect FROM user
UNION ALL SELECT 'problem_set', COUNT(*), 4553*$SCALE FROM problem_set
UNION ALL SELECT 'problem(세트당16근사)', COUNT(*), 71244 + 4553*($SCALE-1)*16 FROM problem
UNION ALL SELECT 'quiz_history', COUNT(*), 1828*$SCALE FROM quiz_history
UNION ALL SELECT 'refresh_token', COUNT(*), 155*$SCALE FROM refresh_token
UNION ALL SELECT 'essay_grade_log', COUNT(*), 642*$SCALE FROM essay_grade_log
UNION ALL SELECT 'board', COUNT(*), 11*$SCALE FROM board
UNION ALL SELECT 'feedback_board', COUNT(*), 21*$SCALE FROM feedback_board;
SQL
echo "[seed] done ($C x$SCALE)"
