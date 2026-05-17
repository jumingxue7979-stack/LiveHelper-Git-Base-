# LiveHelper 작업일지 - 2026-05-17

## 오늘 결론

오늘은 LiveHelper의 맥/윈도우 공유 구조를 수동 ZIP/공유 폴더 중심에서 GitHub 기준으로 전환했고, Windows 실방송 테스트에서 핵심 기능을 추가 확인했다.

현재 기준 저장소:

```text
https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-.git
```

Windows 로컬 기준 폴더:

```text
C:\Users\A\Documents\Codex\LiveHelper-Git-Base
```

현재 브랜치:

```text
main
```

현재 최신 커밋:

```text
f69ef08 Record Windows no-filter rank live test pass
```

## 오늘 정리된 작업 흐름

앞으로 역할 분담은 아래처럼 정리한다.

```text
Mac: 소스 수정, 기능 개발, GitHub push
Windows Codex: git pull, Windows EXE 빌드, 바탕화면 테스트 EXE 준비, 실방송 테스트 기록, GitHub push
```

사용자는 더 이상 수동으로 ZIP을 계속 옮기는 방식이 아니라, 맥에서 수정 후 GitHub에 올렸다고 알려주면 Windows Codex가 받아서 빌드/테스트한다.

## GitHub 공유 구조 구축

Windows에서 새 기준 저장소를 만들고 GitHub에 연결했다.

저장소 구조:

```text
apps/windows-obs-live-rank/
  Windows C# 소스와 BUILD_ON_WINDOWS.cmd

tools/windows-build-agent/
  Windows 빌드 에이전트와 로컬 서명 도구

docs/
  테스트 기록, 작업 기록, 인수인계 문서
```

초기 커밋:

```text
087d29e Initialize LiveHelper shared source baseline
```

맥에서 추가한 정리 커밋:

```text
b8a0ffc Add Mac sync workflow and no-filter rank fallback
36e21c8 Document Mac cleanup and current workspace
```

오늘 추가 확인/기록 커밋:

```text
8ae649a Fix Windows rank popup clipping
f69ef08 Record Windows no-filter rank live test pass
```

## Windows 빌드 서버 점검

문제:

- Windows 빌드 서버 `8790`이 꺼져 있었음.
- 시작 스크립트가 Codex 내부 Node를 잡으면서 Windows가 액세스 거부로 막았음.

조치:

- 일반 설치 Node 경로를 사용하도록 수정.

```text
C:\Program Files\nodejs\node.exe
```

- `START_LIVEHELPER_WINDOWS_BUILD_AGENT.ps1` 수정.
- Windows 시작프로그램에 빌드 에이전트 자동 시작 등록.

확인된 서버 주소:

```text
http://192.168.1.86:8790/
http://192.168.1.86:8790/latest.zip
```

## Windows EXE 테스트 상태

이전 실방송 테스트에서 이미 통과된 항목:

- 방송 시작 후 5분마다 순위 팝업 표시.
- 방송 시작 후 정확히 50분 뒤 비교 분석 생성.
- 내 채널 vs 노필터 라이브 1위 비교 분석 표시.
- 카테고리 점수, 레이더 차트, 핵심 진단, 개선 우선순위 표시.

오늘 추가 통과:

- 실방송 중 노필터 순위가 실제로 확인됨.

기록 문서:

```text
docs/WINDOWS_LIVE_TEST_PASS_2026-05-16_KO.md
docs/WINDOWS_NOFILTER_RANK_TEST_PASS_2026-05-17_KO.md
```

## 오늘 확인된 EXE

기존 바탕화면 실행 파일:

```text
C:\Users\A\Desktop\LiveHelper Windows OBS Rank.exe
SHA-256: 19E0137D7C59E95C962DA2EA3D2DC5F2C1DE4362B62F74782F679C4D60EBC195
```

팝업 UI 수정 최신 테스트 파일:

```text
C:\Users\A\Desktop\LiveHelper Windows OBS Rank 최신-8ae649a.exe
SHA-256: 14C35DDD0A8D09E0BB927D822C5552D54F8783128DA867BAF51AB62F48FBF036
```

주의:

- 오늘 방송 중 기존 EXE가 실행 중이라 원래 이름의 파일은 덮어쓰지 못했다.
- 다음 방송 전에는 기존 앱을 종료하고 최신 EXE를 원래 이름으로 교체하면 된다.

## 팝업 UI 수정

맥에서 수정 후 GitHub에 올린 커밋:

```text
8ae649a Fix Windows rank popup clipping
```

수정 내용:

- 팝업 크기 `430x150` -> `500x180`
- 순위 글자 `24pt` -> `18pt`
- 제목 글자 `13pt` -> `11pt`
- 순위 두 줄 표시 영역 확대
- 긴 제목/하단 문구 말줄임 처리

확인할 화면:

```text
1. 노필터 20위 밖
2. 라이브 2위
```

이 두 줄이 노란 팝업에서 안 잘리고 보여야 한다.

## 비교 리포트 UI 확인

사용자가 올린 비교 리포트 화면 기준 기능은 정상.

정상 확인:

- `내 채널 vs 노필터 라이브 1위`
- 내 점수와 상대 점수 표시
- 격차 표시
- 카테고리별 비교
- 레이더 차트
- 핵심 진단
- 개선 우선순위

남은 UI 개선:

- 하단 `참고사항` 영역이 아래쪽에서 살짝 잘려 보임.
- 1위 채널명이 한자/특수문자처럼 보였는데 실제 채널명인지, 폰트/인코딩 표시 문제인지 확인 필요.

맥에게 보낼 수정 요청:

```text
비교 리포트 화면은 기능은 통과했지만, Windows에서 하단 참고사항 영역이 아래쪽에서 잘려 보입니다. 비교 리포트 창 높이/스크롤/하단 여백을 수정해서 참고사항까지 완전히 보이게 해주세요. 그리고 1위 채널명이 한자/특수문자처럼 보이는데 실제 채널명인지, 아니면 폰트/인코딩 표시 문제인지도 확인해주세요. 수정 후 GitHub에 push해 주세요.
```

## 다음 방송 전 Windows에서 할 일

1. 기존 실행 중인 LiveHelper 앱 종료.
2. 최신 EXE를 원래 이름으로 교체.
3. 필요하면 GitHub 최신 상태 확인.

명령 기준:

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git pull
cd apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

실행 파일 교체 기준:

```text
C:\Users\A\Desktop\LiveHelper Windows OBS Rank 최신-8ae649a.exe
-> C:\Users\A\Desktop\LiveHelper Windows OBS Rank.exe
```

## 다음 새 창에서 가장 먼저 확인할 것

새 Codex 창에서는 아래를 먼저 확인한다.

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git status --short --branch
git log --oneline --decorate -5
```

정상 기준:

```text
HEAD -> main
origin/main
최신 커밋 f69ef08 또는 그 이후
```

## 현재 남은 일

우선순위 1:

- 비교 리포트 하단 참고사항 잘림 수정은 맥에서 소스 수정 후 push.

우선순위 2:

- Windows에서 pull/build 후 새 EXE로 화면 확인.

우선순위 3:

- 고객 배포용 정식 설치 파일 준비.
- 고객에게는 ZIP, 인증서 허용, Smart App Control 끄기 같은 절차를 요구하지 않는다.
- 최종 배포는 정식 코드서명된 `LiveHelperSetup.exe` 형태로 가야 한다.

## 새 창에 붙여넣을 짧은 요약

```text
LiveHelper는 이제 GitHub 저장소 https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-.git 의 main 브랜치를 기준으로 진행합니다. Windows 로컬 폴더는 C:\Users\A\Documents\Codex\LiveHelper-Git-Base 입니다. 현재 최신 커밋은 f69ef08이고, 실방송에서 5분 순위 팝업, 50분 비교 분석, 노필터 순위 확인까지 통과했습니다. 다만 비교 리포트 하단 참고사항이 잘리는 UI 수정은 맥에서 소스 수정 후 push해야 하고, Windows는 이후 git pull, 빌드, 테스트 EXE 교체를 맡습니다.
```
