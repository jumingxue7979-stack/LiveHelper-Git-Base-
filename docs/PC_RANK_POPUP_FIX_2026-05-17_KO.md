# PC 순위 팝업 표시 수정

작성일: 2026-05-17

## 문제

Windows PC에서 노란 순위 팝업의 글자가 너무 커서 두 번째 줄인 `라이브 순위`가 아래쪽에서 잘려 보였다.

현장 사진 기준:

- 1줄: 노필터 순위는 보임
- 2줄: 라이브 순위가 절반만 보임
- 원인: 고정 팝업 크기에 비해 순위 글자 크기와 줄 높이가 큼

## 수정

파일:

```text
apps/windows-obs-live-rank/LiveHelperWindowsObsRank.cs
```

수정 내용:

- 팝업 크기: `430 x 150` -> `500 x 180`
- 제목 글자: `13pt` -> `11pt`
- 순위 글자: `24pt` -> `18pt`
- 순위 표시 영역 높이 확대
- 제목/하단 문구에 말줄임 처리 추가

## 테스트 기준

Windows에서 `git pull` 후 새로 빌드한다.

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git pull
cd apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

라이브 중 노란 팝업에서 아래 두 줄이 모두 보여야 한다.

```text
1. 노필터 20위 밖
2. 라이브 2위
```

노필터 순위는 트래픽이 낮으면 실제 검색 결과에 잡히지 않을 수 있으므로, 이번 테스트의 우선 확인 대상은 PC 팝업 레이아웃과 라이브 순위 줄 표시다.
