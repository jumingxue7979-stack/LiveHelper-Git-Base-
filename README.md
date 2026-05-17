# LiveHelper

## 오늘 최종 인수인계

- 새창 붙여넣기: `NEXT_CHAT_COPY_PASTE_2026-05-17_KO.md`
- 전체 기록: `docs/final-handoff-2026-05-17-ko.md`
- 교육 자료: `docs/education-guide-git-and-build-2026-05-17-ko.md`

오늘 기준으로 LiveHelper는 GitHub `main` 브랜치가 공식 기준입니다. Mac과 Windows는 ZIP 전달이 아니라 `git pull`/`git push`로 동기화합니다.


Start here Korean guide: `START_HERE_KO.md`

LiveHelper Windows OBS Rank is the Windows live-rank tracking and comparison-report app.

## Current Verified Windows Behavior

- Every 5 minutes during a live broadcast: ranking popup appears.
- Before 50 minutes: comparison analysis is blocked.
- At 50 minutes after broadcast start: comparison analysis appears once.
- No-filter ranking was confirmed during a Windows live test.
- PC popup clipping and comparison report bottom clipping have code fixes committed; rebuild on Windows and visually recheck.
- Live test records: `docs/WINDOWS_LIVE_TEST_PASS_2026-05-16_KO.md`, `docs/WINDOWS_NOFILTER_RANK_TEST_PASS_2026-05-17_KO.md`.

## Repository Layout

```text
apps/windows-obs-live-rank/
  Windows C# source and BUILD_ON_WINDOWS.cmd

tools/windows-build-agent/
  Local Windows build agent and signing helper

docs/
  Test records and handoff notes
```

## Windows Build

```bat
cd apps\windows-obs-live-rank
BUILD_ON_WINDOWS.cmd
```

Build output:

```text
apps\windows-obs-live-rank\dist\LiveHelperWindowsObsRank.exe
```


## GitHub Actions Build

After pushing to GitHub, Windows EXE can also be built from GitHub Actions.

```text
Actions -> Build Windows OBS Live Rank -> Run workflow -> download artifact
```

Mac/Windows sync guide:

```text
docs/github-sync-and-windows-build-2026-05-17-ko.md
```

## Distribution Rule

Do not ask customers to disable security settings or manually trust a local certificate.

Customer delivery must be:

```text
Signed LiveHelperSetup.exe
double-click -> install -> desktop icon -> run
```

Local self-signing is only for internal Windows test PCs.
