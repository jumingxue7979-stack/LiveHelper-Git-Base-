# Git/GitHub 동기화 기준

## 결론

앞으로 맥/윈도우 공유는 임시 공유 폴더나 수동 ZIP 전달이 아니라 Git/GitHub를 기준으로 한다.

## 원칙

- 소스의 기준은 GitHub 저장소 하나.
- 맥은 기능 개발/소스 수정 후 `push`.
- 윈도우는 같은 저장소를 `pull` 받아 EXE 빌드/테스트.
- 빌드 결과 EXE/Setup 파일은 Git 본문이 아니라 GitHub Release 또는 Actions 산출물로 배포.
- 고객용 배포는 정식 코드서명된 설치 파일만 사용.

## 맥에서 할 일

```bash
git clone <GITHUB_REPO_URL>
cd <REPO>
```

기존 맥 프로젝트의 최신 Windows 소스는 아래 위치로 맞춘다.

```text
apps/windows-obs-live-rank/
```

수정 후:

```bash
git add .
git commit -m "Update LiveHelper Windows source"
git push
```

## 윈도우에서 할 일

```powershell
git clone <GITHUB_REPO_URL>
cd <REPO>\apps\windows-obs-live-rank
.\BUILD_ON_WINDOWS.cmd
```

## 고객 배포 기준

고객에게는 ZIP 압축 해제, 인증서 허용, Smart App Control 끄기, 보안 예외 추가 같은 절차를 요구하지 않는다.

최종 배포 형태:

```text
LiveHelperSetup.exe
```

설치 파일 요구사항:

- 정식 코드서명 인증서로 서명
- 바탕화면 아이콘 자동 생성
- 시작 메뉴 등록
- 삭제 프로그램 등록
- 업데이트 흐름 준비

## 현재 Windows 통과 기준

- 5분마다 순위표 표시 통과
- 50분 전 비교 분석 차단 통과
- 50분 후 비교 분석 생성 통과

기록 파일:

```text
docs/WINDOWS_LIVE_TEST_PASS_2026-05-16_KO.md
```
