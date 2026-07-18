# 쿼리 튜닝 환경 — 사용 가이드

스케일 스윕(x1/x10/x100) DB + 요청 트레이스 + 통합 진단 대시보드를 재현·운용하는 실행 가이드.
원리·설계는 `../../docs/query-tuning/STEP1~4` 참조. 이 문서는 "무엇을 어떻게 실행하나"만 다룬다.

## 구성 (127.0.0.1 바인딩 + 모니터링 네트워크)

| 레벨 | 컨테이너 | 포트 | 크기(problem) | 용도 |
|---|---|---|--:|---|
| **x1** | `local-mysql-orig` | 3308 | 71,244 | 원본 마스킹본 — **재시딩 소스** |
| **x10** | `local-mysql-x10` | 3309 | 726,876 | 중간 스케일 |
| **x100** | `local-mysql-prod` | 3307 | 7,283,196 | 최대 스케일(분석 대상) |

- 전부 `root`/`password`, `qaskerdb`. 각 테이블 = x1 실측 × 배수(전 테이블 FK 정합).
- 컨테이너는 **`provision-level.sh`로 127.0.0.1 바인딩 + `local_local-monitoring` 네트워크**에 생성.
  포트를 `0.0.0.0`으로 열면 공개 MySQL 랜섬 스캔의 표적이 되므로 **절대 열지 않는다**.

---

## 1. 컨테이너 만들기 / 재생성

```bash
cd api
# 새 볼륨(빈 DB → 복원·시딩 대상)
bash infra/scripts/query-tuning/provision-level.sh local-mysql-prod 3307
# 기존 볼륨 보존(데이터 유지 재생성) — 볼륨명은 docker volume ls / inspect 로 확인
bash infra/scripts/query-tuning/provision-level.sh local-mysql-x10 3309 <볼륨명>
```

## 2. 재시딩 (배수 변경 / 복구)

x1 원본을 대상에 복원한 뒤 배수 시딩한다. `seed-scale.sh <컨테이너> <배수>` — 배수만 바꾸면 어떤 스케일도 재현.

```bash
# (1) x1 → 대상 복원
docker exec -e MYSQL_PWD=password local-mysql-prod mysql -uroot -e "DROP DATABASE IF EXISTS qaskerdb; CREATE DATABASE qaskerdb;"
docker exec -e MYSQL_PWD=password local-mysql-orig mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -uroot qaskerdb \
  | docker exec -i -e MYSQL_PWD=password local-mysql-prod mysql -uroot qaskerdb
# (2) 전 테이블 배수 시딩 (검증 표 자동 출력)
bash infra/scripts/perf-seed/seed-scale.sh local-mysql-prod 100
```

시딩 스크립트(`infra/scripts/perf-seed/`):
- `seed-scale-small.sql` — user·problem_set·quiz_history·refresh_token·essay_grade_log·board·feedback_board (단일 SQL)
- `seed-scale-problem.sql` — problem (배치, 세트당 16)
- `seed-scale.sh` — 오케스트레이션(작은 테이블 → problem 배치 → 검증)

> `reply`는 x1 실측 0이라 시딩 대상 없음. `problem`은 세트당 16 고정이라 원본(15.65) 대비 근사.

## 3. 트레이스 캡처 → 대시보드 §②③ (`trace_snapshot`)

```bash
# 앱을 분석 DB(3307)에 붙여 기동 (mock: Gemini 없이 생성·채점 재현)
export JASYPT_ENCRYPTOR_PASSWORD="$(grep '^JASYPT_ENCRYPTOR_PASSWORD=' app/gradle.properties | cut -d= -f2-)"
SPRING_PROFILES_ACTIVE=local,loadtest,mock ./gradlew :app:bootRun
# 다른 터미널에서 트레이스
bash infra/scripts/query-tuning/trace-capture.sh
```

→ `trace_snapshot` 생성. 대시보드 **§②**(엔드포인트별 요청당 쿼리·스캔행)·**§③**(레포·메서드·SQL 시퀀스)가 채워진다.
`repoMethod=` 주석(RepoMethodAspect)에서 레포·메서드를 파싱하고, 레포를 안 거친 SQL(lazy-load·dirty-check)은
테이블명 복원 + `method='Hibernate query'`로 표시. 재캡처하면 갱신.

## 4. 스케일 스윕 → 대시보드 §① (seed 축)

각 레벨에 앱을 붙여 부하+스캔측정(seed 라벨 자동 부여):

```bash
bash infra/scripts/query-tuning/run-level.sh 3308 local-mysql-orig x1
bash infra/scripts/query-tuning/run-level.sh 3309 local-mysql-x10  x10
bash infra/scripts/query-tuning/run-level.sh 3307 local-mysql-prod x100
```

→ §① 막대가 `x1→x10→x100`로 **계단 상승**하면 O(n)(무인덱스 풀스캔). `findByRtHash`가 `155→1,550→15,500`
정확히 비례하는 게 그 예. 평평하면 인덱스가 먹는 정상 룩업.

---

## 대시보드

Grafana → **[쿼리튜닝] 통합 진단**(`q-asker-unified-diagnosis`). `$repo` 하나로 4섹션 관통:

| 섹션 | 내용 | 채우는 법 |
|---|---|---|
| **§①** 스케일 축 | Micrometer seed 지연 곡선 | 4번(run-level.sh) |
| **§②③** 귀속·시퀀스 | trace_snapshot | 3번(trace-capture.sh) |
| **§④** 읽기/쓰기 비율 | 프로덕션 실측(springboot-oci) | 상시(Prometheus) |

---

## 흔한 함정

- **`Table 'qaskerdb.trace_snapshot' doesn't exist`** → 재시딩(DB 재생성)으로 날아간 것. **3번(trace-capture) 재실행**하면 복구(분석 파생 테이블이라 Flyway 밖, 정상 동작).
- **§① No data** → seed 데이터 없음. **4번(run-level.sh)**을 각 레벨로 돌려야 seed 축이 생긴다.
- **exporter mysql 메트릭 끊김** → 컨테이너가 `local_local-monitoring` 네트워크에 없거나 127.0.0.1 바인딩 후 `host.docker.internal`로 붙는 옛 설정. `provision-level.sh`로 생성 + exporter는 `<컨테이너명>:3306` 접근(run-level.sh가 처리).
- **포트를 `0.0.0.0`으로 열지 말 것** — 항상 `provision-level.sh`(127.0.0.1). 공개 MySQL은 랜섬 스캔 표적이다.
