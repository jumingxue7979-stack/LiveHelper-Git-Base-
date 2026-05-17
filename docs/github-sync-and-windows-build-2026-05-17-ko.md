# GitHub 기준 공유 및 Windows 빌드 방식

작성일: 2026-05-17

## 결론

맥과 윈도우 사이에서 ZIP을 직접 주고받거나 로컬 Build Agent에 의존하는 방식은 임시 테스트용이다. 앞으로의 기준은 GitHub를 단일 원본 저장소로 두고, Windows EXE는 GitHub Actions에서 자동 빌드하는 방식이다.

이유는 단순하다.

- 맥과 윈도우가 같은 소스를 본다.
- 누가 어떤 파일을 고쳤는지 기록이 남는다.
- Windows PC가 켜져 있지 않아도 EXE를 만들 수 있다.
- 로컬 서버, SMB, 방화벽, PowerShell 인증서 문제에 덜 흔들린다.
- 오늘 같은 전달 실패를 반복하지 않는다.

## 확정 GitHub 저장소

```text
https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-.git
```

주의: 저장소 이름 끝에 하이픈 `-`이 붙어 있다. 명령에 입력할 때 빠뜨리지 않는다.

## 새 기준 흐름

```text
Mac Codex 작업
  -> git commit
  -> GitHub push
  -> GitHub Actions Windows 빌드
  -> Actions artifact에서 LiveHelperWindowsObsRank.exe 다운로드
  -> Windows PC에서 실행 테스트
```

## 이번에 추가한 파일

```text
.github/workflows/windows-obs-live-rank-build.yml
```

역할:

- GitHub Actions의 `windows-latest` 환경에서 C# 소스를 컴파일한다.
- 결과물 `LiveHelperWindowsObsRank.exe`를 만든다.
- `BUILD_RESULT_KO.md`와 함께 artifact로 올린다.

## 현재 윈도우 앱에 포함된 기능

- 라이브 순위 5분 간격 기록
- 노필터 검색에서 내 라이브가 보이면 노필터 순위도 기록
- 내 채널과 노필터 라이브 1위 채널 비교
- 긴 분석문 대신 짧은 도표형 리포트 출력

## 처음 한 번만 할 일

Windows 쪽에 이미 깨끗한 기준 저장소가 만들어져 있다.

```text
C:\Users\A\Documents\Codex\LiveHelper-Git-Base
기준 커밋: 087d29e Initialize LiveHelper shared source baseline
```

따라서 GitHub 원격 저장소는 Windows 기준 저장소에서 먼저 연결하고 push한다. 맥에서 별도로 `git init` 후 먼저 push하지 않는다. 그래야 서로 다른 첫 커밋이 생기지 않는다.

Windows에서 GitHub 원격 저장소 URL을 연결한다.

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git branch -M main
git remote add origin https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-.git
git push -u origin main
```

그 다음 맥은 같은 저장소를 clone하거나 pull한다.

```bash
cd /Users/opeunkeullo/Desktop
git clone https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-.git LiveHelper-Git-Base
cd LiveHelper-Git-Base
```

맥에서 추가할 표준 파일은 아래다.

```text
apps/windows-obs-live-rank/
.github/workflows/windows-obs-live-rank-build.yml
docs/github-sync-and-windows-build-2026-05-17-ko.md
```

맥에서 변경 후에는 일반적인 Git 흐름만 사용한다.

```bash
git status
git add apps .github docs README.md .gitignore
git commit -m "Add Mac-side Windows build workflow"
git push
```

## Windows EXE 받는 법

GitHub 저장소 화면에서:

1. `Actions` 탭을 연다.
2. `Build Windows OBS Live Rank` workflow를 누른다.
3. `Run workflow`를 누르거나, main 브랜치에 push해서 자동 실행되게 한다.
4. 빌드가 끝나면 아래쪽 `Artifacts`에서 `LiveHelper_WindowsObsLiveRank_번호`를 다운로드한다.
5. 압축을 풀고 `LiveHelperWindowsObsRank.exe`를 실행한다.

## 테스트 체크리스트

1. API 키, 채널 주소, 키워드를 입력한다.
2. 라이브 시작 후 5~7분 사이 첫 순위가 찍히는지 본다.
3. 이후 5분 간격으로 순위가 찍히는지 본다.
4. YouTube 노필터 검색 화면에 내 방송이 보이면 앱의 노필터 순위도 기록되는지 본다.
5. 비교 리포트가 점수표, TOP 3, 오늘 할 일 형태로 짧게 나오는지 본다.

## 로컬 Build Agent의 위치

로컬 Windows Build Agent는 완전히 버리는 것이 아니라 보조 수단으로 둔다.

- 빠른 실험: Windows Build Agent
- 정식 공유/빌드: GitHub Actions

오늘 발생한 `Cert:\CurrentUser\My` 오류처럼 Windows 로컬 환경에서만 생기는 문제는 GitHub Actions 방식으로 피한다.
