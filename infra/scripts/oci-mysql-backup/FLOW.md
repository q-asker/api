# `systemctl start oci-mysql-backup.service` 실행 흐름

Timer 자동 트리거·수동 `systemctl start` 모두 동일한 이 흐름을 탄다.

## 전체 시퀀스

```
[0] 사용자·Timer   ──systemctl start──→
                                        [1] systemd PID 1
                                              │
                                        [2] unit 파일 파싱·EnvironmentFile 로드
                                              │
                                        [3] fork + setuid(oci-mysql-backup)
                                              │
                                              ↓
                                        [4] /opt/oci-mysql-backup/backup.sh 실행
                                              │
                                        ┌─────┴─────────────────────────────┐
                                        │                                    │
                                     사전 검증                          [5] 도구·env 검증
                                     실패시 exit 1                           │
                                        │                                    │
                                     flock 시도                        [6] /var/lock/... 획득
                                     실패시 skip+1, exit 0                   │
                                        │                                    │
                                     작업 디렉터리                     [7] mktemp /tmp/oci-...
                                     생성 + trap cleanup                     │
                                        │                                    │
                                     Step 1 mysqldump ────────────────→ [8] MySQL → gzip
                                     실패시 fail+1, exit 2                   │
                                        │                                    │
                                     Step 2 sha256sum ────────────────→ [9] 로컬 계산
                                     실패시 fail+1, exit 3                   │
                                        │                                    │
                                     Step 3 collect_metadata ──────────→ [10] MySQL 정보 조회
                                     + table_counts 병합                     │
                                     실패시 fail+1, exit 5                   │
                                        │                                    │
                                     Step 4 finalize_metadata ─────────→ [11] size/sha/dur 머지
                                        │                                    │
                                     Step 5 OCI PUT ×3 ───────────────→ [12] Object Storage
                                     실패시 fail+1, exit 4                   │
                                        │                                    │
                                     성공 메트릭                       [13] state.json 갱신
                                                                             │
                                                                       [14] .prom 파일 갱신
                                        │                                    │
                                        └─────┬─────────────────────────────┘
                                              │
                                        trap 발동 → /tmp/oci-... 삭제
                                              │
                                        systemd에게 exit 0 전달
                                              │
                                        [15] flock 자동 해제 (프로세스 종료 = fd 200 close)
                                              │
                                        [16] journalctl에 stdout/stderr 기록
                                              │
                                        [17] Deactivated successfully
```

## 단계별 상세

### [0] 트리거
```bash
sudo systemctl start oci-mysql-backup.service
# = Timer 트리거와 동일한 진입점
```
Timer는 `oci-mysql-backup.timer`의 `OnCalendar=*-*-* 00,06,12,18:00:00` 조건으로 자동 발동.

### [1~3] systemd 사전 처리
- **unit 파일 파싱**: `/etc/systemd/system/oci-mysql-backup.service`
- **EnvironmentFile 로드**: `/etc/oci-mysql-backup/env` (root:root 600)
  - `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE`, `BUCKET`, `OCI_PROFILE`, `BASELINE_FILE` 등 프로세스 환경변수로 주입
- **보안 강화 적용**:
  - `NoNewPrivileges`, `PrivateTmp`, `ProtectSystem=strict`, `ProtectHome`
  - `ReadWritePaths`: `/var/lib/oci-mysql-backup`, `/var/lib/node_exporter/textfile_collector`, `/var/lock`
  - `CPUQuota=50%`, `MemoryMax=512M`
- **사용자 전환**: `fork` → `setuid(oci-mysql-backup)`, `HOME=/var/lib/oci-mysql-backup`

### [4] backup.sh 실행
- ExecStart로 지정된 `/opt/oci-mysql-backup/backup.sh` 실행
- shebang: `#!/usr/bin/env bash`
- `set -uo pipefail` (참고: `-e` 미사용, 종료 코드 수동 관리)

### [5] 사전 검증
| 검사 | 실패 시 |
|---|---|
| `flock, mysqldump, mysql, sha256sum, jq, oci` 설치 여부 | `[ERR] xxx 미설치` + exit 1 |
| `MYSQL_HOST, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE` 존재 | `[ERR] 필수 환경변수 xxx 누락` + exit 1 |

### [6] flock 획득 (FR-017)
```bash
exec 200>"/var/lock/oci-mysql-backup.lock"
flock -n 200 || { metric_increment_skip; exit 0; }
```
- 논블로킹 (`-n`) → 이미 잠겨있으면 즉시 실패
- 실패 시 `state.json.skip_total +1`, `[SKIP] lock ... held by another process` 로그, exit 0 (정상 종료)
- 성공 시 fd 200이 락을 잡고 있음 → 프로세스 종료 시 자동 해제

**공유 대상**: backup.sh, restore.sh(T8), GameDay 실행 모두 동일 락 파일. 3자 상호 배제.

### [7] 작업 디렉터리
```bash
WORK_DIR=$(mktemp -d "/tmp/oci-mysql-backup.XXXXXX")
trap 'rm -rf "$WORK_DIR"' EXIT
```
- `PrivateTmp=true`로 인해 실제 경로는 systemd가 격리한 개인 /tmp
- trap으로 프로세스 종료 시 반드시 정리

### [8] Step 1 — mysqldump → gzip
```bash
mysqldump --single-transaction --routines --triggers --hex-blob \
  --set-gtid-purged=OFF \
  -h "$MYSQL_HOST" -u "$MYSQL_USER" "$MYSQL_DATABASE" 2>"$WORK_DIR/dump.err" \
  | gzip -9 > "$WORK_DIR/dump.sql.gz"
```
- 성공 시 `[step 1/5] dump complete (N bytes)` (약 39 MB, 18~25초)
- 실패 시 `fail("dump", 2)` → `state.json.fail_total +1` + exit 2 → systemd에게 `4/NOPERMISSION` 등 노출

**주요 옵션**:
- `--single-transaction`: InnoDB MVCC 스냅샷으로 락 없이 일관성
- `--set-gtid-purged=OFF`: 격리 환경 신원 도용 방지 (dump에 GTID_PURGED SET 문 미포함)
- `--hex-blob`: BLOB을 16진수 리터럴로 → 문자 인코딩 안전

### [9] Step 2 — SHA256
```bash
sha256sum "$DUMP_FILE" | awk '{print $1}' > "$SHA_FILE"
```
- 로컬 계산, MySQL·OCI 접근 없음, ~0.5초
- 실패 시 `fail("checksum", 3)` → exit 3

### [10] Step 3 — 메타데이터 수집
```bash
collect_metadata > "$META_FILE"
```
`lib/metadata.sh`의 함수. `information_schema` 조회로:
- `schemas`, `tables`, `approx_row_count`
- `source_id`, `source_host`, `dump_tool_version`, `created_at`

이어서 baseline yml 있으면 **대표 테이블 정확 카운트 병합**:
```bash
TABLE_COUNTS=$(collect_table_counts)
jq --argjson tc "$TABLE_COUNTS" '. + {table_counts: $tc}' "$META_FILE" > tmp && mv tmp "$META_FILE"
```
결과 예: `{"user": 1000, "problem_set": 200, "quiz_history": 5000}` (실측값이 아닌 형태 예시)

### [11] Step 4 — metadata finalize
`size_bytes`, `sha256`, `duration_seconds`, `object_key` 필드를 병합.

### [12] Step 5 — OCI Object Storage PUT (×3)
```bash
oci --profile BACKUP_WRITER os object put -bn qasker-mysql-backup --name <KEY>.sql.gz --file $DUMP_FILE --force
oci --profile BACKUP_WRITER os object put -bn qasker-mysql-backup --name <KEY>.meta.json --file $META_FILE --force
oci --profile BACKUP_WRITER os object put -bn qasker-mysql-backup --name <KEY>.sha256 --file $SHA_FILE --force
```
- **자격증명**: `/var/lib/oci-mysql-backup/.oci/config` + `backup_writer.pem`
- **HOME=/var/lib/oci-mysql-backup** 덕분에 oci CLI가 자동 참조
- **BACKUP_WRITER 권한**: `OBJECT_CREATE`, `OBJECT_OVERWRITE`, `OBJECT_INSPECT` (GET·DELETE 차단)
- 실패 시 `fail("upload-*", 4)` → exit 4

### [13] state.json 갱신
```json
{
  "fail_total": 2,
  "skip_total": 0,
  "last_success_ts": 1782913646,
  "last_duration": 25,
  "last_size": 39022013,
  "last_object_key": "2026/07/01/qasker-mysql-20260701T134701Z.sql.gz"
}
```
- 위치: `/var/lib/oci-mysql-backup/state.json`
- 원자적 쓰기: `tmp` → `mv` rename

### [14] Prometheus textfile 갱신
```
/var/lib/node_exporter/textfile_collector/oci_mysql_backup.prom
```
6개 지표:
- `oci_mysql_backup_fail_total` (counter)
- `oci_mysql_backup_skip_total` (counter)
- `oci_mysql_backup_last_success_timestamp_seconds` (gauge)
- `oci_mysql_backup_last_duration_seconds` (gauge)
- `oci_mysql_backup_last_size_bytes` (gauge)
- `oci_mysql_backup_last_object_info{key="..."}` (gauge)

### [15~17] 종료 및 관측 파이프라인
- **trap 발동**: 임시 디렉터리 삭제
- **flock 자동 해제**: 프로세스 종료 = fd 200 close
- **journalctl에 로그 기록**: stdout·stderr가 journal로
- **systemd**: `Deactivated successfully` + `Finished` 로그

### [비동기] 관측 파이프라인 (backup.sh와 독립 실행)
```
[14] textfile 갱신
       │
       │ (별도 프로세스, Alloy가 주기 스크래프)
       ↓
Alloy unix exporter (textfile block)
       │
       │ prometheus.remote_write
       ↓
원격 Prometheus
       │
       ↓
Grafana 대시보드
Grafana Alerting (Rule 3종):
  - L2BackupFailure (fail_total 증가)
  - L2BackupStale (last_success > 7h)
  - L2BackupSkipSpike (skip 3회/30분)
```

## 종료 코드 매핑

| 코드 | 뜻 | state.json 갱신 |
|---|---|---|
| 0 | 성공 or 락 획득 실패 (skip) | 성공 시 last_* 갱신, skip 시 skip_total +1 |
| 1 | 사용법·환경변수 오류 | 없음 |
| 2 | mysqldump 실패 | fail_total +1 |
| 3 | sha256sum 실패 | fail_total +1 |
| 4 | OCI upload 실패 | fail_total +1 |
| 5 | metadata 수집 실패 | fail_total +1 |

## 파일 경로 정리

| 경로 | 내용 |
|---|---|
| `/etc/systemd/system/oci-mysql-backup.service` | systemd unit |
| `/etc/systemd/system/oci-mysql-backup.timer` | timer (6h 주기) |
| `/etc/oci-mysql-backup/env` | EnvironmentFile (secrets) |
| `/etc/oci-mysql-backup/healthcheck.baseline.yml` | 헬스체크 규칙 |
| `/opt/oci-mysql-backup/backup.sh` | 메인 스크립트 |
| `/opt/oci-mysql-backup/lib/metrics.sh` | 메트릭 헬퍼 |
| `/opt/oci-mysql-backup/lib/metadata.sh` | 메타 수집 헬퍼 |
| `/opt/oci-mysql-backup/healthcheck.sh` | 헬스체크 (T8이 호출) |
| `/var/lib/oci-mysql-backup/.oci/config` | OCI CLI 프로필 (BACKUP_WRITER) |
| `/var/lib/oci-mysql-backup/.oci/backup_writer.pem` | private key |
| `/var/lib/oci-mysql-backup/state.json` | 누적 카운터·마지막 성공 |
| `/var/lib/node_exporter/textfile_collector/oci_mysql_backup.prom` | Prometheus 메트릭 |
| `/var/lock/oci-mysql-backup.lock` | flock 공용 락 |
| `/tmp/oci-mysql-backup.XXX/` | 작업 임시 (PrivateTmp 격리) |

## 확인 명령 (실행 후)

```bash
# 서비스 상태
sudo systemctl status oci-mysql-backup.service --no-pager

# 로그 (최근 실행)
sudo journalctl -u oci-mysql-backup.service -n 100 --no-pager

# 상태 파일
sudo cat /var/lib/oci-mysql-backup/state.json | jq
sudo cat /var/lib/node_exporter/textfile_collector/oci_mysql_backup.prom

# 버킷 실제 업로드 확인
oci --profile BACKUP_READER os object list -bn qasker-mysql-backup \
  --prefix "$(date -u +%Y/%m/%d)" --output table

# 다음 timer 실행 시각
systemctl list-timers | grep oci-mysql-backup
```

## 실패 시 재실행

자동 재시도 없음 (FR-018 - 자연 재실행 정책). 다음 timer 시각에 자동 재시도. 즉시 재실행하려면:
```bash
sudo systemctl start oci-mysql-backup.service
```
