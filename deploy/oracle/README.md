# 클라우드 서버 설정 가이드 (Google Cloud)

SkinCare AI의 백엔드, AI 서버, MySQL, 관리자 웹, 문진 LLM(qwen3:4b)을 클라우드 VM 한 대에서 실행하는 방법을 정리한 문서입니다.

> 폴더 이름이 `oracle`인 이유: 처음에는 Oracle Cloud 무료 VM에 배포하려고 만들었습니다. 지금은 **Google Cloud VM**에서 운영하고 있습니다. 스크립트는 두 환경에서 모두 동작하고, 여러 스크립트가 이 경로를 쓰고 있어서 폴더 이름은 그대로 두었습니다.

> ⚠️ 이 저장소는 **공개 저장소**입니다. 서버 IP, SSH 키, 비밀번호, OAuth 시크릿, DB 백업, 서명 키는 절대 커밋하지 마세요. 자세한 내용은 [Git에 올리면 안 되는 것](#git에-올리면-안-되는-것)을 보세요.

---

## 목차
1. [현재 구성](#현재-구성)
2. [PC 준비 (팀원 공통)](#pc-준비-팀원-공통)
3. [처음 서버를 만들 때](#처음-서버를-만들-때)
4. [코드 수정 후 서버에 반영](#코드-수정-후-서버에-반영)
5. [비밀값 바꾸기 (소셜 로그인, 공공데이터 키)](#비밀값-바꾸기)
6. [앱(APK) 새 버전 배포](#앱apk-새-버전-배포)
7. [DB 자동 백업과 복원](#db-자동-백업과-복원)
8. [서버 상태 확인](#서버-상태-확인)
9. [문제 해결](#문제-해결)
10. [비용과 주의사항](#비용과-주의사항)

---

## 현재 구성

```
 휴대폰 앱 / 관리자 브라우저
          │  https://skincare-ai.skincare-server.workers.dev   (고정 주소)
          ▼
 Cloudflare Worker ──(Quick Tunnel, 서버에서 바깥으로만 연결)──▶ Google Cloud VM (서울)
                                                                   └ /srv/skincare  (Docker Compose)
                                                                      ├ edge (Caddy)     ├ backend (Spring)
                                                                      ├ ai (YOLO+ViT)    ├ llm (Ollama, CPU)
                                                                      ├ db (MySQL 8.4)   ├ maintenance
                                                                      └ connector (cloudflared)

 APK 다운로드 버튼 ──▶ Google Cloud Storage 공개 버킷 (us-west1, 무료 한도)
```

| 항목 | 값 |
|---|---|
| 공개 주소 | https://skincare-ai.skincare-server.workers.dev |
| 관리자 페이지 | `/admin/` |
| 앱 설치 페이지 | `/download/` (QR 코드도 이 주소) |
| VM | Google Cloud `asia-northeast3`(서울), **e2-standard-4** (4 vCPU / 16 GB), Ubuntu 24.04 x86_64, 디스크 50 GB |
| 서버 계정 | `skincare` (SSH 키로만 로그인) |
| 실행 폴더 | `/srv/skincare` |
| 인바운드 포트 | **22(SSH)만** 열려 있음. 웹 트래픽은 Cloudflare 터널로 들어오므로 80/443을 열지 않습니다 |
| 자동 실행 | `skincare-ensure.timer`(5분마다 주소 점검·복구), `skincare-backup.timer`(매일 04:00 DB 백업), `skincare-autodeploy.timer`(5분마다 GitHub 확인·자동 반영), `skincare-cleanup.timer`(매일 05:00 Docker 정리) |

서버 IP는 저장소에 적지 않습니다. 팀 내부 채널에서 받아 아래 환경변수에 넣어 쓰세요.

---

## PC 준비 (팀원 공통)

### 1. 필요한 프로그램
- Git, Python 3.11 이상, OpenSSH(Windows 10/11에 기본 포함)
- 백엔드나 앱을 빌드하려면 JDK 17, Node.js 24, Android SDK도 필요합니다. 설치 방법은 각 폴더의 README를 보세요.

### 2. SSH 키
- 서버 관리자에게 받은 개인키를 `C:\Users\<사용자>\.ssh\oracle-skincare.key`에 저장합니다. 파일 이름은 기본값일 뿐이고, 다른 이름이면 아래 `SKINCARE_SSH_KEY`로 지정하면 됩니다.
- 새 팀원을 추가할 때는 그 팀원이 `ssh-keygen -t ed25519 -C skincare`로 키를 만들고, **공개키(.pub)만** 관리자에게 보냅니다. 관리자는 Compute Engine → VM 인스턴스 → 수정 → **SSH 키**에 그 공개키를 추가합니다.

### 3. 환경변수 (한 번만 설정)
스크립트는 서버 주소를 코드에 적어 두지 않고 환경변수에서 읽습니다. PowerShell에서 실행하세요.

```powershell
[Environment]::SetEnvironmentVariable('SKINCARE_SERVER', 'skincare@<서버-IP>', 'User')
# 선택: 키 파일 이름이 다르거나 백업 저장 위치를 바꿀 때
[Environment]::SetEnvironmentVariable('SKINCARE_SSH_KEY', "$HOME\.ssh\<키파일>", 'User')
[Environment]::SetEnvironmentVariable('SKINCARE_BACKUP_DIR', 'D:\SkinCare-Backup\db', 'User')
```

설정한 뒤에는 **새 터미널을 열어야** 반영됩니다. 접속을 확인합니다.

```powershell
ssh -i "$HOME\.ssh\oracle-skincare.key" $env:SKINCARE_SERVER "hostname"
```

| 변수 | 필수 | 기본값 | 사용하는 스크립트 |
|---|---|---|---|
| `SKINCARE_SERVER` | ✅ | 없음 (`skincare@<IP>`) | `pull_db_backup.ps1`, `set_social_oauth.ps1`, `set_public_data_key.ps1` |
| `SKINCARE_SSH_KEY` |  | `~\.ssh\oracle-skincare.key` | 위와 같음 |
| `SKINCARE_BACKUP_DIR` |  | 저장소를 담은 폴더의 `Backup\db` | `pull_db_backup.ps1` |

---

## 처음 서버를 만들 때

서버가 이미 실행 중이라면 이 단계는 건너뛰세요. 서버를 새로 만들거나 다른 계정으로 옮길 때만 필요합니다.

### 1. VM 만들기 (Google Cloud 콘솔)
Compute Engine → VM 인스턴스 → **인스턴스 만들기**

| 항목 | 값 |
|---|---|
| 이름 | `skincare-server` |
| 리전 / 영역 | `asia-northeast3`(서울) / 아무 영역 |
| 머신 | E2 → **e2-standard-4** (4 vCPU, 16 GB). LLM과 AI 모델이 함께 돌아가므로 메모리는 16 GB를 권장합니다 |
| 부팅 디스크 | **Ubuntu 24.04 LTS (x86/64)**, 균형 있는 영구 디스크 **50 GB** |
| 방화벽 | HTTP/HTTPS 허용은 **체크하지 않음** (22번만 열림) |
| 보안 → SSH 키 | 수동으로 생성된 SSH 키 추가 → 공개키 한 줄을 붙여 넣습니다. 키 끝의 주석(`skincare`)이 서버 계정 이름이 됩니다 |

만든 뒤 **외부 IP**를 확인해서 `SKINCARE_SERVER`에 넣습니다. 고정 IP가 필요하면 VPC 네트워크 → IP 주소에서 **고정 외부 주소로 승격**하세요.

### 2. PC에서 업로드 폴더 만들기
저장소 루트에서 실행합니다. `--transfer`에는 이전 서버 패키지(DB·모델·비밀값 백업이 들어 있는 폴더)를 지정합니다.

```powershell
python deploy\oracle\pack_for_cloud.py --transfer <이전-패키지-폴더> --output <업로드-폴더>
scp -i "$HOME\.ssh\oracle-skincare.key" -r <업로드-폴더> "${env:SKINCARE_SERVER}:/home/skincare/skincare-cloud-upload"
```

- 포함되는 것: 런타임 설정, AI 코드, 백엔드 JAR, 비전 모델, 비밀값(LF로 변환됨), 설치 페이지, 볼륨 백업(DB, maintenance, local_llm)
- 업로드 폴더에는 **비밀값과 개인정보**가 들어 있습니다. 서버로 옮긴 뒤 PC에서 지우거나 안전한 곳에 보관하세요.

### 3. 서버 설정 (SSH 접속 후)

```bash
sudo mv ~/skincare-cloud-upload /srv/skincare && sudo chown -R skincare:skincare /srv/skincare
cd /srv/skincare
sudo bash setup_server.sh          # Docker, Compose, 4 GB 스왑 설치
exit                               # docker 그룹 권한을 적용하려면 다시 접속
```

```bash
cd /srv/skincare
bash restore_volumes.sh            # "complete-volume-restore"가 나오면 성공
sudo python3 connect_cloud.py --cpu   # 이미지 빌드와 실행(첫 실행은 10~20분), "complete-cloud-fixed-route-ready"가 나오면 성공
sudo bash install_service.sh       # 재부팅하거나 터널이 끊겨도 5분 안에 자동 복구
```

DB 자동 백업과 디스크 정리도 설치합니다. 저장소의 `backup_db.sh`, `cleanup_docker.sh`, `skincare-backup.*`, `skincare-cleanup.*`를 `/srv/skincare`에 복사한 뒤 실행하세요.

```bash
chmod 755 backup_db.sh cleanup_docker.sh
sudo cp skincare-backup.service skincare-backup.timer skincare-cleanup.service skincare-cleanup.timer /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now skincare-backup.timer skincare-cleanup.timer
sudo systemctl start skincare-backup.service && journalctl -u skincare-backup -n 3   # "backup-ok" 확인
```

### 4. 소셜 로그인 콜백 주소 (각 개발자 콘솔에 등록)
고정 주소를 쓰기 때문에 서버를 옮겨도 아래 값은 바뀌지 않습니다.

| 서비스 | Redirect URI |
|---|---|
| Google | `https://skincare-ai.skincare-server.workers.dev/login/oauth2/code/google` |
| Kakao | `https://skincare-ai.skincare-server.workers.dev/login/oauth2/code/kakao` |
| Naver | `https://skincare-ai.skincare-server.workers.dev/login/oauth2/code/naver` |

### 5. 확인
- 관리자 페이지 https://skincare-ai.skincare-server.workers.dev/admin/ 와 설치 페이지 `/download/`가 열리는지 확인합니다.
- 실제 휴대폰으로 **소셜 로그인 → 사진 업로드 → 문진 → 결과 조회 → 병원 찾기**까지 확인합니다.
- 예전 PC의 로컬 서버는 **꺼 두세요.** 두 서버가 같은 고정 주소에 번갈아 등록됩니다.

---

## 코드 수정 후 서버에 반영

### 자동 반영 (팀원은 PR만 병합하면 됩니다)
서버가 **5분마다 GitHub의 배포 브랜치를 확인하고**, 새 커밋이 있으면 알아서 반영합니다. 서버 접속 권한이나 GitHub 비밀값은 필요 없습니다.

1. 기능 브랜치에서 작업하고 PC에서 테스트합니다.
2. PR을 **배포 브랜치**에 병합합니다. 배포 브랜치는 서버의 `/srv/skincare/auto-deploy.env`(`DEPLOY_BRANCH=`)에 적혀 있습니다.
3. **5~25분 안에** 반영됩니다. 백엔드처럼 다시 빌드할 것이 많으면 더 오래 걸립니다. 반영하는 동안 마지막 1~2분은 앱이 잠시 연결되지 않습니다.
4. 결과는 누구나 아래 주소에서 확인할 수 있습니다.
   **https://skincare-ai.skincare-server.workers.dev/api/wc/deploy-status**
   - `deployedCommit`: 지금 서버에서 돌아가는 커밋
   - `lastAttempt.result`: `running`(반영 중) / `success` / `skipped`(앱·문서만 바뀜) / `failed`

| 바뀐 곳 | 서버가 하는 일 |
|---|---|
| `backend/`, `admin-web/` | 관리자 웹과 JAR를 빌드한 뒤 반영 |
| `ai/`, `deploy/`, DB 마이그레이션, 그 밖의 파일 | 바뀐 컨테이너만 다시 빌드 |
| `mobile/`, `docs/`, `*.md`만 | 서버에서 할 일이 없으므로 건너뜀(`skipped`). 앱은 [APK 배포](#앱apk-새-버전-배포)를 따로 해야 합니다 |

**반영이 실패하면** 서버가 자동으로 **바로 전 성공 버전으로 되돌리고**, 같은 커밋은 다시 시도하지 않습니다. 문제를 고친 새 커밋을 병합하면 다시 시도합니다. 실패 원인은 관리자가 서버에서 확인합니다.

```bash
journalctl -u skincare-autodeploy -n 100 --no-pager      # 반영 로그
cat /srv/skincare/auto-deploy/deployed                     # 마지막 성공 커밋
bash /srv/skincare/auto_deploy.sh --force                  # 지금 바로 다시 반영(실패 기록도 지움)
sudo systemctl disable --now skincare-autodeploy.timer     # 자동 반영 잠시 끄기 (시연 직전 등)
sudo systemctl enable --now skincare-autodeploy.timer      # 다시 켜기
```

#### 배포 브랜치 바꾸기 (예: develop → main)
지금 배포 브랜치는 **`develop`**입니다. 나중에 `main`으로 바꾸려면:

1. **먼저 `develop` → `main` PR을 병합합니다.** `main`이 `develop`보다 뒤처진 상태에서 바꾸면, 서버가 `main`의 **예전 코드로 되돌아갑니다.** 지금 `main`에는 자동 반영 스크립트도 없습니다.
2. 서버에서 브랜치를 바꿉니다. 다음 5분 확인 때부터 적용됩니다.
   ```bash
   sed -i 's/^DEPLOY_BRANCH=.*/DEPLOY_BRANCH=main/' /srv/skincare/auto-deploy.env
   ```
3. 그다음부터 팀원은 `develop`에서 개발하고, 시연에 올릴 때만 `develop` → `main` PR을 병합합니다.

> ⚠️ 배포 브랜치에 병합하는 순간 **시연 서버에 그대로 반영됩니다.** PC에서 테스트한 뒤에 병합하세요. 서버에서는 백엔드 테스트를 실행하지 않습니다.

#### 처음 설치할 때 (관리자, 서버에서 한 번)
`auto_deploy.sh`, `install_autodeploy.sh`, `skincare-autodeploy.service/.timer`가 있는 폴더에서 실행합니다.

```bash
bash install_autodeploy.sh <배포-브랜치> --enable    # ~/SkinCare-AI clone, 스크립트·타이머 설치
```

`auto_deploy.sh`를 고쳤다면 서버에서 `install_autodeploy.sh`를 다시 실행해야 반영됩니다. 실행 중인 스크립트가 반영 도중에 바뀌지 않도록 `/srv/skincare`에 복사본을 두고 그 복사본을 실행하기 때문입니다.

### 수동 반영 (자동 반영을 껐을 때)
서버에서 바뀐 곳에 맞는 명령을 실행합니다.

| 바꾼 곳 | 서버 명령 | 앱 재설치 |
|---|---|---|
| AI(`ai/`), 질문 은행·규칙, compose 설정, DB 마이그레이션 | `bash ~/SkinCare-AI/deploy/oracle/update_server.sh` | 필요 없음 |
| 백엔드(`backend/`), 관리자 페이지(`admin-web/`) | `bash ~/SkinCare-AI/deploy/oracle/update_server.sh --jar` | 필요 없음 |
| 모바일 앱(`mobile/`) | [앱(APK) 새 버전 배포](#앱apk-새-버전-배포) | **필요함** |

- `update_server.sh`는 `git pull` → (필요하면 JAR·관리자 웹 빌드) → `/srv/skincare`로 코드 동기화 → 바뀐 컨테이너만 다시 빌드하는 순서로 동작합니다.
- 자동 반영이 켜져 있으면 이 checkout은 자동 반영 스크립트가 관리하므로 직접 `git pull`하지 마세요.

### 공통
- `private/`, `models/`, `distribution/`, `.env`, DB·LLM 볼륨은 **건드리지 않습니다.**
- 백엔드 테스트는 서버에서 건너뜁니다. PC에서 `cd backend && ./gradlew test`로 먼저 확인하세요.
- DB 구조를 바꿀 때는 `backend/src/main/resources/db/mysql/`에 `V009__설명.sql`처럼 **새 파일을 추가**합니다. 기존 V001~V008 파일은 수정하면 안 됩니다.

---

## 비밀값 바꾸기

비밀값은 서버의 `/srv/skincare/private/application-secrets.properties`에만 있습니다. 아래 스크립트는 값을 화면에 보여 주지 않고 입력받아, 서버에 저장한 뒤 백엔드만 재시작합니다. 실행하는 데 1~2분 걸립니다.

```powershell
# 소셜 로그인 (google | kakao | naver)
powershell -ExecutionPolicy Bypass -File deploy\oracle\set_social_oauth.ps1 -Provider kakao

# 공공데이터포털(data.go.kr) 병원·약국 API 키 — 저장하기 전에 세 API로 먼저 테스트합니다
powershell -ExecutionPolicy Bypass -File deploy\oracle\set_public_data_key.ps1
```

---

## 앱(APK) 새 버전 배포

Cloudflare 무료 Worker로 들어온 한국 요청은 미국 LA를 거쳐 처리되기 때문에, 큰 파일을 받으면 100 KB/s 안팎으로 느려집니다. 그래서 설치 페이지의 버튼은 **Google Cloud Storage 공개 버킷 `skincare-ai-apk-1170`**(us-west1)에서 APK를 바로 받게 해 두었습니다. 한국에서 약 18 MB/s가 나오고, 무료 한도는 저장 5 GB, 한 달 다운로드 100 GB입니다.

1. **같은 서명 키**로 빌드하고 `versionCode`를 올립니다(`deploy/public-demo/Build-Public-Android.ps1`). 서명 키는 저장소에 없으니 관리자에게 받아야 하고, 키가 다르면 기존 사용자가 업데이트할 수 없습니다.
2. 콘솔의 Cloud Storage → `skincare-ai-apk-1170`에 `SkinCare-AI-public-<버전>.apk`를 업로드합니다. 파일 이름에 버전을 넣어야 캐시 문제가 생기지 않습니다.
3. 서버 `/srv/skincare/distribution/`에서 다음을 바꿉니다.
   - `index.html`: 버튼 링크와 "버전 N" 문구
   - `release.json`: `versionCode`, `sha256`, `bytes`, `downloadUrl`
   - `SkinCare-AI.apk`: 같은 파일로 교체(예비 다운로드 주소, `fallbackDownloadUrl`)
4. 바꾸기 전에 `distribution/`을 `distribution-backup-v<이전버전>`으로 복사해 두세요.

> `deploy/public-demo/publish_apk.py`로 설치 페이지를 다시 만들면 버튼이 서버 파일 링크로 돌아갑니다. 그런 경우에는 3단계를 다시 해 주세요.

팀원에게는 설치 페이지 주소 **https://skincare-ai.skincare-server.workers.dev/download/** 만 공유하면 됩니다.

---

## DB 자동 백업과 복원

- 서버의 `skincare-backup.timer`가 **매일 04:00(KST)** `backup_db.sh`를 실행합니다.
- `mysqldump --single-transaction` 방식이라 서비스를 멈추지 않습니다. 사진도 DB 안에 저장되어 있어서 함께 백업됩니다.
- 파일 위치: `/srv/skincare/backups/db/skincare_dev-YYYYmmdd-HHMMSS.sql.gz`. **14일**이 지난 파일은 자동으로 지워집니다.
- `maintenance`(약 22 MB)와 `local_llm`(LLM 모델, 2.5 GB) 볼륨은 백업에 포함되지 않습니다. LLM 모델은 다시 받을 수 있습니다.

PC로 받아 오기(새 파일만 받습니다):

```powershell
powershell -ExecutionPolicy Bypass -File deploy\oracle\pull_db_backup.ps1
```

복원할 때 주의할 점이 있습니다. 덤프에 `CREATE DATABASE`와 `USE skincare_dev`가 들어 있어서, 그대로 넣으면 **운영 DB를 덮어씁니다.** 먼저 최신 백업을 하나 더 만든 뒤 실행하세요.

```bash
gzip -dc backups/db/<파일>.sql.gz | sudo docker exec -i skincare-public-demo-db-1 sh -c 'mysql -uroot -p"$(cat $MYSQL_ROOT_PASSWORD_FILE)"'
```

---

## 서버 상태 확인

```bash
cd /srv/skincare
COMPOSE="docker compose --env-file .env -f compose.yaml -f compose.cloud.yaml -f compose.llm.yaml -f compose.llm-cpu.yaml --profile cloud-connector"
sudo $COMPOSE ps                                   # 컨테이너 7개가 Up(healthy)인지
sudo docker logs --since 1h skincare-public-demo-backend-1 | grep -iE "error|exception"
systemctl list-timers 'skincare-*'                 # 자동 복구·백업·자동 반영·디스크 정리 타이머
df -h / && free -h                                 # 디스크, 메모리
```

### 서버의 `/srv/skincare` 구조

| 경로 | 내용 | Git |
|---|---|---|
| `compose*.yaml`, `Dockerfile.*`, `Caddyfile*`, `*.py` | 실행 설정과 스크립트 (`update_server.sh`가 동기화) | 저장소에서 옴 |
| `.env` | 공개 호스트, 토큰 만료 시간 같은 값 | ❌ |
| `private/` | DB 비밀번호, JWT 키, OAuth 시크릿, AI 토큰, Worker 토큰 | ❌ **절대 금지** |
| `models/` | 비전 모델 가중치 | ❌ |
| `distribution/` | 설치 페이지, `release.json`, 예비 APK | ❌ |
| `backups/db/` | DB 덤프 (개인정보 포함) | ❌ |

---

## 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 고정 주소가 "서버 준비 중"으로 나옴 | `journalctl -u skincare-ensure -n 50`을 확인하고, `sudo python3 connect_cloud.py --cpu`로 다시 연결합니다 |
| 스크립트가 `Set the SKINCARE_SERVER ...` 오류를 냄 | [환경변수](#3-환경변수-한-번만-설정)를 설정하고 새 터미널을 엽니다 |
| `Permission denied (publickey)` | 계정 이름이 `skincare`인지, 키 경로가 맞는지, 공개키가 VM에 등록되어 있는지 확인합니다 |
| 문진이 느림 | CPU로 추론하기 때문에 질문 하나에 몇 초에서 몇십 초가 걸릴 수 있습니다. 40초가 넘으면 규칙 기반 질문으로 자동 대체됩니다 |
| 소셜 로그인 오류 | 개발자 콘솔의 Redirect URI가 [위 표](#4-소셜-로그인-콜백-주소-각-개발자-콘솔에-등록)와 같은지 확인하고, `set_social_oauth.ps1`로 키를 다시 넣습니다 |
| 병원 검색 결과가 없음 | `set_public_data_key.ps1`로 키를 다시 테스트합니다. data.go.kr에서 승인한 뒤 반영되기까지 1~2시간 걸릴 수 있습니다 |
| DB 로그인 실패 | `private/db-*.txt`에 CR(`\r`)이 들어 있는지 확인합니다 |
| 메모리 부족 | `free -h`로 확인합니다. 4 GB 스왑은 `setup_server.sh`가 설정합니다 |
| 디스크 부족 | `df -h /`, `sudo docker system df`로 확인하고, `sudo systemctl start skincare-cleanup`으로 바로 정리합니다. 그래도 부족하면 `docker builder prune -af`로 빌드 캐시를 모두 지웁니다. 다음 반영은 처음부터 빌드하므로 조금 느려집니다 |
| APK 다운로드가 느림 | 설치 페이지 버튼이 GCS 주소(`storage.googleapis.com`)를 가리키는지 확인합니다 |

---

## 비용과 주의사항

- **VM은 무료가 아닙니다.** e2-standard-4(서울)는 한 달에 약 $130~150이고, 지금은 Google Cloud **무료 체험 크레딧($300)**에서 빠지고 있습니다. 체험 기간이 끝났는데 유료 계정으로 전환하지 않으면 VM과 버킷이 **정지**되고, 정지된 뒤 일정 기간이 지나면 삭제됩니다. 기간을 연장해서 계속 쓰려면 미리 백업을 받아 두고 계획을 세우세요.
- **APK 버킷**(us-west1)은 Google Cloud Free Tier 한도(저장 5 GB, 한 달 다운로드 100 GB) 안에서 무료입니다. 버킷은 공개이므로 **APK 말고 다른 파일은 올리지 마세요.**
- **Quick Tunnel**은 Cloudflare가 개발·테스트용으로 안내하는 기능입니다. 졸업 프로젝트 데모에는 충분하지만, 상용 서비스로 운영하려면 정식 도메인과 Named Tunnel로 바꾸는 것을 검토하세요.

### Git에 올리면 안 되는 것

| 항목 | 보관 위치 |
|---|---|
| 서버 IP, SSH 개인키 | 각자 PC의 환경변수, `~/.ssh` |
| `private/` 비밀값, `.env` | 서버 `/srv/skincare` |
| 앱 서명 키(`.p12`, `signing.json`) | 관리자 PC와 오프라인 백업 |
| DB 덤프(`*.sql.gz`), 업로드 패키지, APK | 저장소 밖의 `Backup/` 폴더, GCS 버킷 |

---

## 이 폴더의 파일

| 파일 | 실행 위치 | 하는 일 |
|---|---|---|
| `pack_for_cloud.py` | PC | 이전 패키지로 서버 업로드 폴더 만들기 |
| `setup_server.sh` | 서버(최초 1회) | Docker, Compose, 스왑 설치 |
| `restore_volumes.sh` | 서버(최초 1회) | DB, maintenance, LLM 볼륨 복원 |
| `install_service.sh`, `skincare-ensure.*` | 서버 | 5분마다 고정 주소를 점검하고 자동 복구 |
| `update_server.sh`, `sync_code.py` | 서버 | 최신 코드 반영(수동) |
| `auto_deploy.sh`, `install_autodeploy.sh`, `skincare-autodeploy.*` | 서버 | GitHub 배포 브랜치를 5분마다 확인해 자동 반영, 실패하면 되돌림 |
| `cleanup_docker.sh`, `skincare-cleanup.*` | 서버 | 매일 05:00 반영 후 남은 예전 이미지와 2일 넘은 빌드 캐시 정리 |
| `backup_db.sh`, `skincare-backup.*` | 서버 | 매일 DB 백업 |
| `pull_db_backup.ps1` | PC | 서버 백업을 PC로 받기 |
| `set_social_oauth.ps1`, `set_public_data_key.ps1`, `update_secret.py`, `check_public_data.py` | PC → 서버 | 비밀값 교체 |
| `retry_create_instance.py` | PC | (예전) Oracle VM 생성 재시도. Google Cloud에서는 쓰지 않습니다 |
