# 비교 리포트 UI 하단 잘림 수정

작성일: 2026-05-17

## 확인된 상태

Windows 실방송 테스트에서 2단계 비교 리포트 생성은 정상 통과로 본다.

확인된 항목:

- `내 채널 vs 노필터 라이브 1위` 비교 생성
- 내 점수, 1위 점수, 격차 표시
- 카테고리별 점수 비교 표시
- 레이더 차트 표시
- 핵심 진단과 개선 우선순위 생성

## 문제

비교 리포트 화면 하단의 `참고사항` 영역이 아래쪽에서 일부 잘려 보였다.

## 원인

파일:

```text
apps/windows-obs-live-rank/LiveHelperWindowsObsRank.cs
```

대시보드 컨트롤 높이가 `900`인데, 참고사항 박스는 아래 위치까지 사용했다.

```text
참고사항 y = 878
참고사항 높이 = 54
필요 하단 위치 = 932
기존 대시보드 높이 = 900
```

그래서 약 32픽셀이 컨트롤 밖으로 나가 잘렸다.

## 수정

대시보드 높이를 `900`에서 `960`으로 늘렸다.

```text
dashboard.Height = 960;
```

## 채널명 표시

1위 채널명이 한자/중국어처럼 보이는 것은 실제 YouTube 라이브 1위 채널명이 외국어였던 것으로 확인했다. 글자 깨짐이 아니므로 수정하지 않는다.

## Windows 반영 방법

```powershell
cd C:\Users\A\Documents\Codex\LiveHelper-Git-Base
git pull
cd apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

새 EXE에서 비교 리포트 하단 `참고사항` 전체가 보여야 한다.
