# LiveHelper 교육 자료 - GitHub 기준으로 배우고 쓰는 방법

작성일: 2026-05-17

이 문서는 코드를 직접 읽기 어려운 사용자가 LiveHelper를 안전하게 운영하고, Codex에게 정확히 지시하고, 결과를 검증하는 방법을 배우기 위한 자료다.

## 1. 먼저 큰 그림

예전 방식은 “파일 전달”이었다.

```text
Mac에서 ZIP 만들기 -> Windows로 보내기 -> Windows에서 압축 풀기 -> 어떤 버전인지 헷갈림
```

이제 방식은 “공통 저장소”다.

```text
Mac 수정 -> GitHub에 push -> Windows에서 pull -> Windows EXE 빌드/테스트
```

이 차이가 가장 중요하다.

ZIP은 복사본이다. GitHub는 기준본이다.

## 2. GitHub가 왜 필요한가

GitHub는 코드의 중앙 장부다.

할 수 있는 일:

- 누가 언제 무엇을 고쳤는지 기록한다.
- Mac과 Windows가 같은 파일을 보게 한다.
- 이전 상태로 비교할 수 있다.
- “최신 파일이 뭐냐”는 혼란을 줄인다.

사용자가 꼭 외울 말:

```text
최신 기준은 GitHub main이다.
```

## 3. pull, commit, push 뜻

`git pull`:

- GitHub에 있는 최신 내용을 내 컴퓨터로 가져온다.
- Windows가 Mac 수정분을 받을 때 쓴다.

`git add`:

- 이번에 저장할 변경 파일을 고른다.

`git commit`:

- 변경 내용을 하나의 기록으로 묶는다.

`git push`:

- 내 컴퓨터의 기록을 GitHub로 올린다.

실제 흐름:

```text
pull = 가져오기
commit = 기록하기
push = 올리기
```

## 4. LiveHelper 현재 구조

공식 폴더:

```text
/Users/opeunkeullo/Desktop/LiveHelper-Git-Base
```

주요 위치:

```text
apps/windows-obs-live-rank/
  Windows EXE 소스와 빌드 스크립트

docs/
  테스트 기록, 인수인계, 작업 설명

tools/windows-build-agent/
  Windows 빌드 보조 도구
```

보면 되는 파일:

- `START_HERE_KO.md`: 시작 안내.
- `NEXT_CHAT_COPY_PASTE_2026-05-17_KO.md`: 다음 새창에 붙여넣는 요약.
- `docs/final-handoff-2026-05-17-ko.md`: 오늘 전체 기록.

## 5. 사용자는 코드를 몰라도 무엇을 해야 하나

코드를 직접 읽지 않아도 제품을 만들 수 있다. 대신 세 가지는 정확히 해야 한다.

첫째, 현상을 구체적으로 말한다.

좋지 않은 말:

```text
이상해. 안 돼.
```

좋은 말:

```text
라이브 시작 6분 뒤에도 노필터 순위가 안 나왔고, YouTube 화면에는 내 방송이 2위로 보였다.
```

둘째, 사진과 시간을 같이 준다.

좋은 정보:

- 테스트 날짜와 시간.
- 라이브 시작 후 몇 분인지.
- 어떤 키워드인지.
- YouTube 화면 사진.
- 앱 화면 사진.

셋째, 통과 기준을 정한다.

예:

```text
5분마다 순위가 나와야 한다.
50분 전에는 비교 분석이 막혀야 한다.
50분 뒤에는 비교 리포트가 한 번 나와야 한다.
노필터에 내 방송이 보이면 앱에도 노필터 순위가 기록되어야 한다.
```

## 6. LiveHelper 테스트 체크리스트

방송 시작 전:

- 최신 EXE인지 확인.
- 키워드가 맞는지 확인.
- YouTube 로그인 또는 검색 화면이 정상인지 확인.
- OBS 화면 위 노란 팝업 위치가 보기 좋은지 확인.

방송 시작 후 5~8분:

- 첫 순위 팝업이 나오는지 확인.
- `노필터` 줄과 `라이브` 줄이 모두 보이는지 확인.
- 순위가 YouTube 화면과 크게 다르지 않은지 확인.

방송 중 10~45분:

- 5분마다 계속 순위가 갱신되는지 확인.
- 중간에 앱이 멈추지 않는지 확인.
- 노필터에 내 방송이 보이면 앱에도 기록되는지 확인.

50분 이후:

- 비교 리포트가 생성되는지 확인.
- 내 점수, 1위 점수, 격차가 보이는지 확인.
- 카테고리별 점수와 레이더 차트가 보이는지 확인.
- 맨 아래 `참고사항`이 잘리지 않는지 확인.

테스트 후:

- 통과/실패를 말로만 남기지 말고 문서에 남긴다.
- 실패 화면은 사진으로 저장한다.
- 어떤 EXE로 테스트했는지 기록한다.

## 7. Codex에게 지시하는 법

좋은 지시 구조:

```text
현재 공식 저장소는 /Users/opeunkeullo/Desktop/LiveHelper-Git-Base 입니다.
GitHub main 기준으로 작업해 주세요.
문제는 Windows 화면에서 노란 순위 팝업 두 번째 줄이 잘리는 것입니다.
목표는 노필터/라이브 두 줄이 모두 보이게 하는 것입니다.
수정 후 문서 기록과 git commit/push까지 해주세요.
```

나쁜 지시 구조:

```text
화면 이상해. 고쳐줘.
```

좋은 지시는 코드 지식보다 중요하다.

## 8. 자주 확인할 부분

Git 상태:

```bash
cd /Users/opeunkeullo/Desktop/LiveHelper-Git-Base
git status
```

최신 기록:

```bash
git log --oneline -5
```

Windows 최신 받기:

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git pull
```

Windows 빌드:

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base\apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

## 9. 헷갈리면 이 순서로 판단

1. 공식 폴더인가?
2. GitHub main과 맞는가?
3. Windows가 최신 pull을 했는가?
4. 새 EXE를 다시 빌드했는가?
5. 실제 방송 또는 테스트 화면으로 확인했는가?
6. 결과를 docs에 남겼는가?

이 여섯 가지를 지키면 “어느 파일이 최신인지 모르는 문제”를 크게 줄일 수 있다.

## 10. 앞으로 3차 기능을 만들 때의 올바른 순서

3차 어뷰징/비정상 공격 감지는 바로 코드부터 만들면 위험하다.

먼저 정해야 한다.

- 어떤 행동을 어뷰징으로 볼 것인가.
- 어떤 데이터로 판단할 것인가.
- 정상 사용자를 오탐하지 않으려면 어떤 예외가 필요한가.
- 결과를 사용자에게 어떻게 보여줄 것인가.
- 경고만 할 것인가, 점수화할 것인가.

그 다음 구현한다.

순서:

```text
정의 -> 화면 설계 -> 데이터 기준 -> 코드 구현 -> 테스트 -> 문서 기록
```

이것이 제품화에 가까운 길이다.
