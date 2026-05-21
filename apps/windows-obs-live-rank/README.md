# Windows OBS Live Rank Prototype

목적: Windows OBS 방송자가 방송 중 본인 화면에서만 라이브 순위를 확인하는 1차 PC 버전 프로토타입이다.

이 폴더는 아직 정식 설치 파일이 아니다.  
Windows OBS Gate에서 통과한 `SetWindowDisplayAffinity` 방식을 실제 라이브 순위 조회에 연결하기 위한 개발용 기준 코드다.

## 동작 기준

```text
첫 설정 1회:
API 키
채널 주소
키워드

방송 시작:
OBS 방송 시작 버튼 클릭
가능하면 OBS 자동 실행
5분 후 첫 조회
이후 5분마다 5초짜리 노란 순위 팝업
```

표시 순위:

```text
1. 노필터 순위
2. 라이브 순위
```

종료 기준:

```text
고정한 라이브 영상 ID가 종료되면 자동 정지
같은 채널에 라이브가 2개 이상이면 동시 라이브 감지 후 정지
라이브가 계속 확인되지 않으면 최대 3번 확인 후 정지
```

## Windows에서 빌드

Windows PC에서는 반드시 `codex/work6-channel-analysis` 브랜치를 최신 원격 커밋까지 받은 뒤 빌드한다.

```text
git fetch origin
git switch codex/work6-channel-analysis
git pull --ff-only origin codex/work6-channel-analysis
```

그다음 이 폴더를 열고 아래 파일을 더블클릭한다.

```text
BUILD_ON_WINDOWS.cmd
```

빌드 스크립트는 다음 상태가 아니면 EXE 생성을 중단한다.

```text
현재 브랜치: codex/work6-channel-analysis
현재 커밋: origin/codex/work6-channel-analysis 와 동일
Windows 소스: 로컬 미커밋 변경 없음
Work6 최신 표식: 순위 videoId 고정, 7개 세부 항목, 타이머 안정화 포함
```

Git 없이 받은 단독 소스 묶음에서는 브랜치 확인 대신 Work6 최신 표식만 검사한다.

성공하면 아래 파일이 생긴다.

```text
dist\LiveHelperWindowsObsRank.exe
```

해시는 Windows PowerShell에서 아래 명령으로 확인한다.

```text
Get-FileHash .\dist\LiveHelperWindowsObsRank.exe -Algorithm SHA256
```

## Windows 테스트 순서

1. `BUILD_ON_WINDOWS.cmd` 실행
2. `dist\LiveHelperWindowsObsRank.exe` 실행
3. API 키, 채널 주소, 키워드 입력
4. `OBS 방송 시작` 클릭
5. 열린 OBS에서 라이브 시작
6. 시작 직후 순위 팝업이 뜨지 않는지 확인
7. 약 5분 후 노란 순위 팝업이 5초 뜨는지 확인
8. OBS 미리보기/녹화본에 노란 팝업이 찍히지 않는지 확인
9. 시크릿 모드 직접 검색 순위와 앱 순위가 근사치인지 확인
10. 라이브 종료 후 다음 조회 시점 이후 자동 정지되는지 확인

## 주의

이 프로토타입은 OBS Browser Source에 넣는 오버레이가 아니다.

```text
OBS 소스로 넣으면 시청자에게 보일 수 있다.
이 앱은 방송자 Windows 화면에만 띄우고 캡처에서 제외하는 방식이다.
```
