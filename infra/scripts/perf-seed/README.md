# perf-seed — 성능 실험용 합성 시드 도구

Hibernate enhancement before/after 측정(specs/001-quiz-read-optimization)을 위해,
**실측 분포를 재현한** 대규모 퀴즈 데이터를 로컬 MySQL에 채운다. (research D9 / FR-011 / SC-007)

> ⚠️ **로컬 전용.** `seed.sh`는 접속 호스트가 허용 목록(localhost/127.0.0.1/q-asker-db/mysql)이
> 아니면 즉시 중단한다. 운영 DB에는 실행할 수 없다. 실데이터 덤프 복제도 하지 않는다(개인 학습자료 보호).

## 구성

| 파일 | 역할 |
|---|---|
| `seed.sh` | 래퍼 — 환경 가드 → 예상 용량 출력 → `seed.sql` 실행 → `verify.sql` 자동 검증 |
| `seed.sql` | 순수 SQL 생성기. `@scale` 배수만큼 실측 분포로 문항 생성 |
| `verify.sql` | 유형 비율·explanation_content 크기가 실측 대비 ±10% 이내인지 검증 |
| `cleanup.sql` | `session_id LIKE 'seed-%'` 세트·문항 일괄 삭제(원상 복구) |

## 사용

```bash
# 접속 정보(환경변수; 기본값은 로컬 도커 infra/mysql)
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root \
       MYSQL_PASSWORD=... MYSQL_DATABASE=test

./seed.sh --scale 10     # 실측 ×10 (problem ~66.7만 행) 시드 + 자동 검증
./seed.sh --verify       # 분포 검증만
./seed.sh --cleanup      # seed- 데이터 전부 삭제
```

호스트에 `mysql` 클라이언트가 없으면 도커로:
```bash
{ echo "SET @scale := 10;"; cat seed.sql; } \
  | docker exec -i q-asker-db mysql --default-character-set=utf8mb4 -uroot -p"$PW" test
```

## 쓰기 축 부하·측정 (dirty tracking, before/after)

읽기 축(`load.js`/`measure.sh`)의 쌍둥이. **실제 Gemini 호출 없이** 순수 DB 쓰기 경로(Problem 재로드 →
`updateExplanation` → flush, dirty-tracking)에 부하를 걸어 인핸스먼트 적용 전/후를 비교한다.
기존 JUnit 벤치(`DirtyTrackingWriteBenchmark`/`DirtyCheckScanBenchmark`)를 부하 형태로 옮긴 것.

| 파일 | 역할 |
|---|---|
| `write-load.js` | k6 쓰기 부하. `DevWriteBenchController`(`local` 프로파일, RateLimit=NONE)를 때린다 |
| `measure-write.sh` | 콜드 재시작 → 부하 → 앱/DB CPU-time·InnoDB write 델타 캡처 → Grafana annotation → 요약 |
| `dashboards/q-asker-enhancement-write-before-after.json` | 쓰기 축 Grafana 대시보드(uid `qasker-enh-write-ba`) — import용 |

대상 엔드포인트(둘 다 Gemini 호출 없음):
- `POST /dev/bench/explanation?setId=&number=` — 단건 dirty-tracking 경로(기본)
- `POST /dev/bench/explanations?setId=&count=` — N건 단일 트랜잭션(커밋·fsync N→1, `WRITE_MODE=batch`)

대상 세트는 시드 세트(`id = SET_ID_BASE + [0, N_SETS)`, raw id라 hashid 인코딩 불필요).
`N_SETS = CEIL(scale × 66684 / 16)` (scale 10 → **41678**).

인핸스먼트 on/off는 Gradle 프로퍼티로 토글한다(루트 `build.gradle`). 플러그인은 항상 적용돼
의존성·`gradle.lockfile`은 고정되고 계측(`enhancement {}`)만 켜지므로, **끈 빌드도 lock 정합성 유지**.
`Problem.class`의 `$$_hibernate_*` 멤버 유무로 계측 여부 확인 가능(ON=계측됨, OFF=없음).

```bash
# 사전: q-asker-db 기동, ./seed.sh --scale 10 완료
# 1) Grafana에 dashboards/q-asker-enhancement-write-before-after.json import (1회)

# 2) [after=인핸스먼트 켠 빌드] — clean 필수(계측 클래스 캐시 격리)
#    인핸스먼트는 기본 OFF이므로 A/B의 'after' 빌드에서만 명시적으로 켠다.
./gradlew clean :app:bootJar -PenableHibernateEnhancement
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar &   # DevWriteBenchController 등록
./measure-write.sh after

# 3) [before=인핸스먼트 끈 빌드] — 프로젝트 기본값(계측 없음)
./gradlew clean :app:bootJar
SPRING_PROFILES_ACTIVE=local java -jar app/build/libs/*.jar &
./measure-write.sh before

# 조절 env: RATE(1000) DURATION(60s) WRITE_MODE(single|batch) N_SETS(41678)
```

before/after 부하당 핵심 수치(appCPU/write, dbCPU/write, `Innodb_rows_updated`/write,
`Innodb_data_written`/write, write p95)는 대시보드의 `quiz-write-opt` 어노테이션 **구간 텍스트**에 실린다.

> 참고: dirty-tracking 특성상 같은 값으로 반복 쓰면 미변경 감지로 UPDATE가 스킵될 수 있다
> (`Innodb_rows_updated`/write → 0 수렴). 그래도 **flush 시 dirty-check 스캔 비용**은 매 요청 발생하므로
> 인핸스먼트 이득(스냅샷 deep-compare 회피) 측정 신호는 유지된다.

## 분포(실측 2026-06 고정값)

- **유형 비율**: MULTIPLE 43% / BLANK 34% / OX 17% / ESSAY 4.5% / REAL_BLANK 1.5%
- **explanation_content 바이트**(lazy 대상·데모 핵심): M 2396 / B 1719 / O 1127 / E 1703 / R 1709 (±10% 지터)
- **세트당 문항 수**: 16 고정(실측 평균 15.6 근사)
- `selections`는 유효 JSON(`List<Selection{content,explanation,correct}>`, 정답 정확히 1개),
  `referenced_pages`는 JSON int 배열 — 앱이 그대로 파싱·조회 가능

## 식별·복구

- 시드 세트: `id = 1000000 + seq`(실데이터 max ~4587과 분리), `session_id = 'seed-<id>'`
- 스키마 변경 0. `cleanup.sql`로 완전 원상 복구.

## 제약

- 6자리 시퀀스 생성기 상한 = **100만 행** → `scale ≤ 15`. 초과 시 `seed.sh`가 거부.
- before/after 부하 테스트(Q3/Q10)는 이 시드 세트의 `id`(1000000~)를 hashids로 인코딩해 대상 URL 구성.
