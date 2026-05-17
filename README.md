# LiveHelper

LiveHelper Windows OBS Rank is the Windows live-rank tracking and comparison-report app.

## Current Verified Windows Behavior

- Every 5 minutes during a live broadcast: ranking popup appears.
- Before 50 minutes: comparison analysis is blocked.
- At 50 minutes after broadcast start: comparison analysis appears once.
- Live test passed on Windows. See `docs/WINDOWS_LIVE_TEST_PASS_2026-05-16_KO.md`.

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
