# 맥에게 보낼 한 문장

앞으로 LiveHelper는 공유 폴더/ZIP 수동 전달이 아니라 GitHub 저장소 하나를 기준으로 맥은 소스를 push하고 윈도우는 같은 저장소를 pull해서 빌드/테스트하게 맞춰주세요. 저장소 구조는 `apps/windows-obs-live-rank`에 Windows 소스, `tools/windows-build-agent`에 윈도우 빌드 도구, `docs`에 테스트 기록을 두는 방식입니다.
