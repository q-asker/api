# `restore.sh` 사용법

L2 백업 객체를 Docker 격리 환경에 복구하고 T7 헬스체크까지 자동 수행하는 단일 명령 스크립트.

## 5분 안의 첫 명령 (spec SC-006)

```bash
sudo /opt/oci-mysql-backup/restore.sh --latest
```

가장 최근 백업을 자동 선택 → Docker mysql 컨테이너 생성 → 데이터 적재 → 헬스체크 → RTO 출력.

성공 시 exit 0, 격리 컨테이너 이름·호스트 포트·헬스체크 JSON 경로가 stdout에 표시된다.

## 사용법

```
restore.sh <OBJECT_KEY> [--env docker|schema]
restore.sh --latest [--env docker|schema]
restore.sh --list
restore.sh -h | --help
```

### 옵션

| 옵션 | 설명 |
|---|---|
| `<OBJECT_KEY>` | 명시적 백업 객체 지정 (예: `2026/07/01/qasker-mysql-20260701T134701Z.sql.gz`) |
| `--latest` | 버킷의 가장 최근 `sql.gz` 자동 선택 (`time-created` 내림차순) |
| `--list` | 사용 가능한 백업 목록 표시 후 종료 |
| `--env docker` | (기본) Docker mysql 컨테이너에 복구 |
| `--env schema` | 원본 서버 격리 스키마에 복구 (미구현, spec 대안) |
| `-h`, `--help` | 사용법 |

### 예시

```bash
# 최신 백업 복구
sudo /opt/oci-mysql-backup/restore.sh --latest

# 백업 목록 조회 후 명시적 지정
sudo /opt/oci-mysql-backup/restore.sh --list
sudo /opt/oci-mysql-backup/restore.sh 2026/06/29/qasker-mysql-20260629T060003Z.sql.gz

# 도움말
/opt/oci-mysql-backup/restore.sh -h
```

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BUCKET` | `qasker-mysql-backup` | Object Storage 버킷 |
| `OCI_PROFILE` | `BACKUP_READER` | ~/.oci/config 프로필 (읽기 전용) |
| `DOCKER_IMAGE` | `mysql:8.0` | 격리 컨테이너 이미지 |
| `LOCK_FILE` | `/var/lock/oci-mysql-backup.lock` | flock 공유 락 (backup과 동일) |
| `WORK_BASE_DIR` | `/tmp` | 임시 작업 디렉터리 위치 |
| `BASELINE_FILE` | `/etc/oci-mysql-backup/healthcheck.baseline.yml` | T7 헬스체크 규칙 |
| `HEALTHCHECK_SCRIPT` | `/opt/oci-mysql-backup/healthcheck.sh` | T7 스크립트 |

특별한 조정이 필요 없다면 기본값 그대로 사용.

## 사전 준비

### 1. Docker 설치 확인
```bash
docker --version
# 없으면: sudo apt install -y docker.io
```

### 2. Docker 이미지 사전 캐시 (권장)
```bash
sudo docker pull mysql:8.0
```
안 하면 첫 실행에서 pull이 발생 (RTO에서는 START_TS 보정으로 제외되나 훈련 시간이 늘어남).

### 3. BACKUP_READER 프로필 확인
```bash
oci --profile BACKUP_READER os ns get
# → {"data": "axluufujp1xz"} 반환하면 OK
```

### 4. healthcheck.baseline.yml 배포 확인
```bash
sudo cat /etc/oci-mysql-backup/healthcheck.baseline.yml | jq .schemas.expected
# → 4 반환하면 OK
```

## 종료 코드

| 코드 | 뜻 | 운영 인스턴스 영향 |
|---|---|---|
| 0 | PASS + RTO 기록 | 무 |
| 1 | 사용법·환경변수 오류 | 무 |
| 3 | sha256 불일치 | **무** (격리 진입 전 중단) |
| 4 | OCI 다운로드 실패 | 무 |
| 6 | 백업 객체 없음 (`--latest` 조회 실패) | 무 |
| 10 | 헬스체크 FAIL | 무 (격리 컨테이너 안에만) |
| 12 | Docker 실패 (pull/run/health) | 무 |
| 13 | dump 적재 실패 | 무 (격리 컨테이너 안에만) |
| 14 | healthcheck 스크립트 없음 | 무 |

**어떤 실패든 원본 MySQL 인스턴스는 무영향** (spec Edge Cases 명시).

## RTO 측정 정책 (FR-019)

- **시작 (START_TS)**: 다운로드 직전
- **종료 (END_TS)**: 헬스체크 PASS 시점
- **제외**: `docker pull` 시간 (START_TS 자동 보정)
- **제외**: 격리 환경 정리 시간 (컨테이너 삭제는 훈련 후 수동)

**SC-001 목표**: RTO ≤ 900초 (15분)

## 격리 컨테이너 관리 (FR-020)

### 컨테이너 이름 규칙
```
mysql-restore-<백업시각>-<유닉스타임스탬프>
예: mysql-restore-20260701T134701Z-1782913900
```
- 첫 번째 시각: 백업 자체의 생성 시각 (UTC)
- 두 번째 타임스탬프: 복구 훈련 시작 시각

**여러 훈련을 병행 실행해도 이름 충돌 없음**.

### 컨테이너 목록 조회
```bash
docker ps -a --filter "name=mysql-restore-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 훈련 완료 후 수동 정리
```bash
# 특정 컨테이너
docker rm -f mysql-restore-20260701T134701Z-1782913900

# 모든 restore 컨테이너 (신중히)
docker ps -aq --filter "name=mysql-restore-" | xargs docker rm -f
```

**주의**: 다음 GameDay 훈련 시작 전에 이전 컨테이너를 정리해야 리소스 낭비 없음. spec FR-020에 따라 자동 삭제는 하지 않는다.

## 실행 흐름 (내부 5단계)

```
[1/5] downloading dump + meta + sha256   (BACKUP_READER)
[2/5] verifying sha256                    (불일치 시 exit 3)
[3/5] starting isolated container         (Docker health 대기 90s)
[4/5] loading dump.sql.gz                 (zcat | docker exec mysql)
[5/5] running healthcheck (T7)            (환경변수로 격리 접속 정보 주입)
    ↓
═══════════════ 복구 완료 ═══════════════
  RTO: <초>  (SC-001 target ≤ 900s)
```

## 예상 출력 (성공 케이스)

```
[2026-07-01T14:30:12Z] [START] object_key=2026/07/01/qasker-mysql-20260701T134701Z.sql.gz container=mysql-restore-20260701T134701Z-1782913812
[2026-07-01T14:30:12Z] [step 1/5] downloading dump + meta + sha256...
[2026-07-01T14:30:17Z] [step 1/5] downloaded
[2026-07-01T14:30:17Z] [step 2/5] verifying sha256...
[2026-07-01T14:30:17Z] [step 2/5] sha256 OK
[2026-07-01T14:30:17Z] [step 3/5] starting isolated container (mysql:8.0)...
[2026-07-01T14:30:17Z] [step 3/5] container=mysql-restore-..., waiting for healthy...
[2026-07-01T14:30:45Z] [step 3/5] container healthy, host_port=32789
[2026-07-01T14:30:45Z] [step 4/5] loading dump.sql.gz...
[2026-07-01T14:31:00Z] [step 4/5] dump loaded (15s)
[2026-07-01T14:31:00Z] [step 5/5] running healthcheck (T7)...
──── healthcheck 결과 ────
{
  "status": "PASS",
  "checks": [
    {"check": "schemas", "expected": 4, "actual": 4, "tolerance": 0, "status": "PASS"},
    {"check": "tables", "expected": 21, "actual": 21, "tolerance": 2, "status": "PASS"},
    {"check": "user", "expected": 1000, "actual": 1000, "tolerance": 100, "status": "PASS"},
    {"check": "problem_set", "expected": 4500, "actual": 4500, "tolerance": 500, "status": "PASS"},
    {"check": "quiz_history", "expected": 5000, "actual": 5000, "tolerance": 5000, "status": "PASS"}
  ]
}
──────────────────────

═══════════════ 복구 완료 ═══════════════
  object_key:   2026/07/01/qasker-mysql-20260701T134701Z.sql.gz
  container:    mysql-restore-20260701T134701Z-1782913812
  host_port:    32789
  dump_size:    39022013 bytes
  load_time:    15s
  RTO:          48s  (SC-001 target ≤ 900s = 15분)
  healthcheck:  PASS
  hc_result:    /tmp/healthcheck-mysql-restore-20260701T134701Z-1782913812.json

▶ 격리 컨테이너는 유지됨 (FR-020). 분석 후 수동 정리:
    docker rm -f mysql-restore-20260701T134701Z-1782913812
```

## 격리 환경 접근 (복구 후)

성공 후 컨테이너 안 MySQL에 접속해서 데이터 검사:

```bash
# 컨테이너 이름은 restore.sh 출력에서 확인
CONTAINER=mysql-restore-20260701T134701Z-1782913812

# root pwd는 restore.sh가 랜덤 생성했지만 exec로 접속 가능
docker exec -it $CONTAINER mysql -uroot qaskerdb -e 'SHOW TABLES;'

# 또는 호스트 포트 통해 접속
HOST_PORT=$(docker port $CONTAINER 3306 | awk -F: '{print $NF}' | head -1)
mysql -h 127.0.0.1 -P $HOST_PORT -uroot qaskerdb -e 'SELECT COUNT(*) FROM user;'
# password 필요 시 restore.sh 로그에서 확인 or 컨테이너 재검사
```

**Tip**: restore.sh 로그·헬스체크 JSON을 GameDay 기록에 첨부 시 함께 남길 것.

## 트러블슈팅

### exit 3 (sha256 불일치)
- **원인**: 다운로드 중 데이터 변조·저장소 손상
- **조치**: 다른 백업 객체(더 이전 시각)로 재시도. 반복되면 버킷 무결성 조사 필요.
- **운영 영향**: 없음 (격리 진입 전)

### exit 12 (컨테이너 unhealthy)
- **원인**: Docker 이미지 손상, 리소스 부족, 포트 충돌
- **조치**:
  ```bash
  docker logs mysql-restore-...   # 원인 파악
  docker ps -a                     # 이전 컨테이너 잔재 확인
  docker rm -f mysql-restore-...   # 문제 컨테이너 정리
  ```
- 재실행 전 `docker system df`로 여유 공간 확인

### exit 10 (헬스체크 FAIL)
- **원인**: 실제 복구 데이터가 기대값과 tolerance 초과 차이
- **조치**:
  ```bash
  cat /tmp/healthcheck-<컨테이너>.json | jq
  # 어느 check가 FAIL인지 확인
  # baseline.yml의 tolerance 조정 필요할지 판단
  ```
- 컨테이너 유지되므로 수동 SQL 조사 가능

### exit 4 (다운로드 실패)
- **원인**: BACKUP_READER 프로필 문제, 네트워크, 객체 키 오탈자
- **조치**:
  ```bash
  oci --profile BACKUP_READER os ns get   # 프로필 동작 확인
  oci --profile BACKUP_READER os object list -bn qasker-mysql-backup --limit 5
  ```

### exit 0인데 flock으로 skip 됨
- **원인**: backup.sh나 다른 restore.sh가 실행 중
- **조치**:
  ```bash
  systemctl status oci-mysql-backup.service
  # 실행 중이면 완료 대기 (수 분) 후 재시도
  ```

## 관련 문서

- `spec.md` — 요구사항 (FR-007, FR-013, FR-017~FR-020, SC-001)
- `FLOW.md` — backup.sh 흐름도 (참고: restore.sh도 유사 구조)
- `RESTORE.md` (본 문서) — restore.sh 사용법
- `RUNBOOK` (T9 예정) — 운영자·GameDay 완전한 절차

## 명령 요약

| 상황 | 명령 |
|---|---|
| 최신 백업 복구 (권장) | `sudo /opt/oci-mysql-backup/restore.sh --latest` |
| 특정 백업 복구 | `sudo /opt/oci-mysql-backup/restore.sh <OBJECT_KEY>` |
| 백업 목록 | `sudo /opt/oci-mysql-backup/restore.sh --list` |
| 컨테이너 목록 | `docker ps -a --filter name=mysql-restore-` |
| 컨테이너 정리 | `docker rm -f <컨테이너명>` |
| 헬스체크 재실행 | (직접 healthcheck.sh 환경변수 주입) |
| 이미지 사전 캐시 | `sudo docker pull mysql:8.0` |
