# LiveHelper 최종 인수인계 기록 - 2026-05-17

이 문서는 2026-05-17 하루 동안 정리, 설계, 수정, 테스트 확인, 남은 확인 사항을 다음 새창에서 그대로 이어가기 위해 남긴 최종 기록이다.

## 1. 오늘의 결론

LiveHelper는 이제 날짜별 ZIP, 바탕화면 임시 폴더, 공유 폴더 수동 전달 방식에서 벗어나 GitHub 단일 저장소 기준으로 움직인다.

공식 기준은 아래 하나다.

```text
/Users/opeunkeullo/Desktop/LiveHelper-Git-Base
```

GitHub 저장소는 아래다.

```text
https://github.com/jumingxue7979-stack/LiveHelper-Git-Base-
git@github.com:jumingxue7979-stack/LiveHelper-Git-Base-.git
```

공식 브랜치:

```text
main
```

현재 작업 원칙:

- Mac은 소스를 고친 뒤 `git add`, `git commit`, `git push` 한다.
- Windows는 `git pull`로 같은 소스를 받은 뒤 빌드하고 테스트한다.
- ZIP 수동 전달, SMB 공유 폴더, 날짜별 작업본은 더 이상 공식 기준이 아니다.
- 예전 자료는 참고용 보관함에만 둔다.

## 2. 정리된 폴더 기준

Mac 바탕화면 기준 공식 작업 폴더:

```text
/Users/opeunkeullo/Desktop/LiveHelper-Git-Base
```

예전 파일 보관 폴더:

```text
/Users/opeunkeullo/Desktop/_ARCHIVE_DO_NOT_USE_2026-05-17
```

보관 폴더 안의 의미:

- `01_livehelper_old_before_github`: GitHub 통합 전 LiveHelper 작업본, 이전 ZIP, 이전 인수인계.
- `02_old_liverank_separate_project`: LiveHelper와 별개인 예전 LiveRank 실험 프로젝트.
- `03_desktop_misc_screenshots_assets`: 바탕화면에 흩어져 있던 스크린샷과 임시 이미지.
- `04_downloads_project_old_packages`: 다운로드 폴더의 이전 LiveHelper/LiveRank 패키지와 참고 텍스트.
- `MOVED_FILES_LOG_2026-05-17.txt`: 어떤 파일을 어디로 옮겼는지 남긴 이동 기록.

주의:

- 보관 폴더는 삭제하지 않았다.
- 보관 폴더 안 자료는 참고용이다.
- 앞으로 실제 개발은 `LiveHelper-Git-Base`에서만 한다.

## 3. 오늘 생성/수정된 핵심 문서

바로 시작 문서:

```text
START_HERE_KO.md
```

다음 새창 붙여넣기 문서:

```text
NEXT_CHAT_COPY_PASTE_2026-05-17_KO.md
```

오늘 전체 인수인계 문서:

```text
docs/final-handoff-2026-05-17-ko.md
```

교육 자료:

```text
docs/education-guide-git-and-build-2026-05-17-ko.md
```

Mac 정리 기록:

```text
docs/MAC_CLEANUP_2026-05-17_KO.md
```

Windows 쪽 중간 작업일지:

```text
docs/DAILY_WORK_LOG_2026-05-17_KO.md
```

이 일지는 중간 기록이고, 최종 판정은 이 인수인계 문서를 우선한다.

GitHub 동기화 및 빌드 가이드:

```text
docs/github-sync-and-windows-build-2026-05-17-ko.md
docs/GIT_GITHUB_SYNC_GUIDE_KO.md
```

노필터 순위 실방송 통과 기록:

```text
docs/WINDOWS_NOFILTER_RANK_TEST_PASS_2026-05-17_KO.md
```

순위 팝업 UI 수정 기록:

```text
docs/PC_RANK_POPUP_FIX_2026-05-17_KO.md
```

비교 리포트 UI 수정 기록:

```text
docs/COMPARISON_REPORT_UI_FIX_2026-05-17_KO.md
```

## 4. 오늘 코드/구조 작업 기록

### 4.1 GitHub 단일 기준 구조로 전환

문제:

- Mac, Windows, ZIP, 공유 폴더, 날짜별 산출물이 뒤섞여 어떤 파일이 최신인지 구분하기 어려웠다.
- Windows에서 빌드된 최신 통과본과 Mac에서 수정한 소스가 서로 자동으로 맞물리지 않았다.

결정:

- GitHub 저장소 하나를 공식 기준으로 정했다.
- Windows도 Mac도 같은 저장소를 보고 움직이게 했다.

효과:

- Mac에서 수정 후 push하면 Windows에서 pull로 받는다.
- Windows에서 테스트 기록을 남기면 Mac에서 pull로 받는다.
- 앞으로 “어떤 ZIP이 최신인가?”를 따질 필요가 줄어든다.

### 4.2 노필터 순위 기록 문제 수정

사용자 제보:

- YouTube 노필터 검색 화면에는 내 라이브가 2위로 보이는데 앱에는 노필터 순위가 기록되지 않았다.
- 라이브 순위만 기록되는 것처럼 보였다.

원인 방향:

- 검색 결과에서 정확한 영상 ID가 바로 맞지 않을 때 같은 채널의 라이브 결과를 후보로 보관하지 못했다.
- 실제 YouTube 화면에는 내 방송이 보이지만 앱 내부 판단에서는 놓칠 수 있었다.

수정:

- `YoutubeClient.cs`의 검색 결과 처리에서 같은 채널 라이브 결과를 fallback 후보로 저장하도록 보강했다.
- 정확한 영상 ID가 잡히지 않을 때도 같은 채널의 라이브 결과가 있으면 노필터 순위로 적용할 수 있게 했다.

결과:

- Windows 실방송 중 노필터 순위가 실제로 확인되었다.
- 관련 기록은 `docs/WINDOWS_NOFILTER_RANK_TEST_PASS_2026-05-17_KO.md`에 남겼다.

### 4.3 PC 노란 순위 팝업 잘림 수정

사용자 제보:

- PC 화면에서 노란 순위 팝업의 글자가 너무 커서 아래 줄이 절반만 보였다.
- `라이브 순위` 줄이 잘 보이지 않았다.

수정:

- 노란 팝업 창 크기를 키웠다.
- 제목 글자 크기를 줄였다.
- 순위 글자 크기를 줄였다.
- 두 번째 줄 글자 크기도 줄였다.
- 긴 텍스트가 한 줄을 밀어내지 않도록 표시 영역을 넓혔다.

관련 커밋:

```text
8ae649a Fix Windows rank popup clipping
```

남은 확인:

- Windows에서 최신 소스를 pull한 뒤 새 EXE를 빌드해 실제 PC 화면에서 두 줄이 모두 보이는지 확인한다.

### 4.4 중국어처럼 보이는 채널명 확인

사용자 질문:

- 노란 팝업 아래에 중국어가 나열되어 보이는데 오류인지 확인 요청.

확인 결과:

- 코드상 아래 줄은 `라이브 1위 + 1위 채널명`을 표시한다.
- 사용자가 나중에 “중국어 채널 맞음”, “바꿀 이유 없다”고 확인했다.

결론:

- 글자 깨짐이나 인코딩 문제가 아니다.
- 실제 1위 채널명이 외국어였으므로 수정하지 않는다.

### 4.5 비교 리포트 하단 참고사항 잘림 수정

사용자 제보:

- 비교 리포트 기능은 정상으로 보이지만 화면 맨 아래 `참고사항` 영역이 살짝 잘렸다.

기능 통과로 확인된 항목:

- `내 채널 vs 노필터 라이브 1위` 비교 생성.
- 내 점수, 1위 점수, 격차 표시.
- 카테고리별 점수 비교 표시.
- 레이더 차트 표시.
- 핵심 진단과 개선 우선순위 표시.

원인:

- 비교 리포트 대시보드 높이가 `900`인데 참고사항 영역이 실제로는 y=878부터 높이 54까지 필요했다.
- 필요한 하단 위치는 932였으므로 약 32픽셀이 잘렸다.

수정:

- 대시보드 높이를 `900`에서 `960`으로 늘렸다.

관련 커밋:

```text
2f12f7d Fix comparison report bottom clipping
```

남은 확인:

- Windows에서 최신 소스를 pull한 뒤 새 EXE에서 비교 리포트 맨 아래 참고사항이 잘리지 않는지 확인한다.

## 5. 오늘 기준 기능 판정

5분 순위 팝업:

- Windows 실방송 테스트에서 정상 확인됨.
- 5분 간격으로 순위가 나오는 흐름 확인됨.

50분 비교 리포트:

- Windows 실방송 테스트에서 정상 확인됨.
- 내 채널과 1위 채널 비교, 점수, 레이더 차트, 개선 우선순위가 생성됨.

노필터 순위 기록:

- Windows 실방송에서 확인됨.
- 기존의 핵심 미확인 항목이 통과로 이동됨.

UI 남은 재확인:

- 노란 순위 팝업 두 줄 표시가 최신 빌드에서 안 잘리는지 확인.
- 비교 리포트 하단 참고사항이 최신 빌드에서 안 잘리는지 확인.

제품화 판단:

- 1차 순위 기능과 2차 비교 분석 기능은 큰 기능 흐름상 통과에 가깝다.
- 다만 Windows 최신 빌드로 UI 수정 2개를 눈으로 재확인해야 한다.
- 3차 어뷰징/비정상 공격 감지 기능은 아직 설계 단계로 따로 잡아야 한다.

## 6. 현재 Git 커밋 흐름

오늘 기준 주요 커밋:

```text
caef915 Add daily work log for 2026-05-17
2f12f7d Fix comparison report bottom clipping
f69ef08 Record Windows no-filter rank live test pass
8ae649a Fix Windows rank popup clipping
36e21c8 Document Mac cleanup and current workspace
b8a0ffc Add Mac sync workflow and no-filter rank fallback
087d29e Initialize LiveHelper shared source baseline
```

각 커밋 의미:

- `087d29e`: Windows 기준 소스를 GitHub에 처음 올린 기준점.
- `b8a0ffc`: Mac에서 GitHub 동기화 구조와 노필터 fallback 수정 추가.
- `36e21c8`: Mac 바탕화면 정리와 공식 작업 폴더 기준 문서화.
- `8ae649a`: PC 노란 순위 팝업 글자 잘림 수정.
- `f69ef08`: Windows에서 노필터 순위 실방송 확인 기록 추가.
- `2f12f7d`: 비교 리포트 하단 참고사항 잘림 수정.
- `caef915`: Windows 쪽 2026-05-17 작업일지 추가. 단, 일부 내용은 중간 시점 기록이므로 최종 상태는 이 문서를 우선한다.

이 문서를 추가한 뒤에는 별도 인수인계 커밋이 하나 더 생긴다.

## 7. Windows에서 다음에 할 일

Windows 새창이나 Windows Codex에게 아래를 실행하게 한다.

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git pull
cd apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

빌드 후 확인할 것:

1. 노란 순위 팝업에서 `노필터`, `라이브` 두 줄이 모두 잘 보이는지.
2. 비교 리포트 맨 아래 `참고사항`이 안 잘리는지.
3. 기존 5분 순위 팝업이 계속 정상인지.
4. 50분 비교 리포트가 계속 정상인지.

Windows에게 전달할 짧은 문장:

```text
GitHub main 최신으로 pull해서 Windows EXE를 다시 빌드해 주세요. 확인할 것은 노란 순위 팝업 두 줄 표시와 비교 리포트 하단 참고사항 잘림 여부입니다. 최신 Mac 커밋에는 비교 리포트 하단 높이 수정까지 들어 있습니다.
```

## 8. Mac에서 다음에 할 일

Mac 새창에서 시작할 때:

```bash
cd /Users/opeunkeullo/Desktop/LiveHelper-Git-Base
git pull
git status
```

새 작업 전 확인:

- `git status`가 깨끗한지 본다.
- 작업할 파일이 공식 폴더 안에 있는지 본다.
- 예전 archive 폴더에서 직접 수정하지 않는다.

새 수정 후 공유:

```bash
git add 수정한파일
git commit -m "수정 내용 요약"
git push
```

## 9. 사용자가 기억할 핵심 원칙

1. 이제 “파일을 어디서 가져오지?”라고 생각하면 먼저 GitHub 저장소를 본다.
2. Mac과 Windows는 서로 파일을 직접 주고받는 것이 아니라 같은 GitHub 저장소를 바라본다.
3. 테스트 통과 여부는 말로만 남기지 말고 `docs/`에 기록한다.
4. 실제 기능은 코드에, 판단과 기록은 문서에 남긴다.
5. 예전 ZIP은 참고용이지 작업 기준이 아니다.
6. 새 기능을 만들기 전에는 먼저 현재 통과 상태를 다시 확인한다.
7. 3차 어뷰징 기능은 1차/2차가 완전히 닫힌 뒤 별도 설계로 시작한다.

## 10. 다음 작업 우선순위

최우선:

- Windows에서 최신 GitHub main을 pull하고 새 EXE 빌드.
- 노란 순위 팝업과 비교 리포트 하단 UI 재확인.

그 다음:

- 5분 순위, 노필터 순위, 50분 비교 리포트를 한 번 더 짧게 회귀 테스트.
- 결과를 `docs/`에 날짜별 테스트 기록으로 남기기.

이후:

- 3차 어뷰징/비정상 공격 감지 기능 설계.
- 어떤 공격을 잡을지, 어떤 데이터로 판단할지, 어떤 화면으로 보여줄지 먼저 정리.
- 설계가 확정된 뒤 구현.

## 11. 새창 시작 문장

다음 새창에는 아래처럼 말하면 된다.

```text
/Users/opeunkeullo/Desktop/LiveHelper-Git-Base/NEXT_CHAT_COPY_PASTE_2026-05-17_KO.md 파일을 먼저 읽고, 공식 GitHub main 기준으로 LiveHelper 작업을 이어가 주세요. 오늘 마지막 주요 상태는 5분 순위, 50분 비교, 노필터 순위가 통과했고, 최신 커밋에는 PC 노란 팝업과 비교 리포트 하단 잘림 수정이 들어 있습니다. Windows에서는 최신 pull 후 새 EXE 빌드와 UI 재확인이 필요합니다.
```
