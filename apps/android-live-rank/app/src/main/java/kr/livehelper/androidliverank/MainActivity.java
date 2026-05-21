package kr.livehelper.androidliverank;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREFS = "livehelper_android_live_rank";
    private static final String KEY_LIVE_RANK_RUNNING = "liveRankRunning";
    private static final String[] PRISM_PACKAGES = {
        "com.prism.live",
        "com.prismlive.studio",
        "com.prism.live.studio"
    };
    private static final String PRISM_STORE_PACKAGE = "com.prism.live";
    private static final String[] YOUTUBE_PACKAGES = {"com.google.android.youtube"};
    private static final String YOUTUBE_STORE_PACKAGE = "com.google.android.youtube";

    private LinearLayout setupPanel;
    private LinearLayout broadcastPanel;
    private LinearLayout liveStatusBar;
    private TextView statusText;
    private TextView savedSummary;
    private TextView usageGuideText;
    private TextView comparisonReportText;
    private ComparisonDashboardView comparisonDashboardView;
    private EditText apiInput;
    private EditText channelInput;
    private EditText keywordInput;
    private String[] pendingPermissionPackageNames;
    private String pendingPermissionStorePackageName;
    private String pendingPermissionAppName;
    private boolean waitingForOverlayPermission;
    private Runnable hideStatusRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF8FAFC);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("라이브 도우미");
        title.setTextColor(0xFF111827);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("");
        desc.setTextColor(0xFF334155);
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        desc.setVisibility(View.GONE);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.setMargins(0, 0, 0, dp(8));
        root.addView(desc, descParams);

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(setupPanel, matchWrap());

        apiInput = makeInput("API 키 입력", true);
        channelInput = makeInput("@채널명 또는 유튜브 주소", false);
        keywordInput = makeInput("검색 키워드", false);

        setupPanel.addView(makeLabel("API"));
        setupPanel.addView(apiInput, inputParams());
        setupPanel.addView(makeLabel("채널 주소"));
        setupPanel.addView(channelInput, inputParams());
        setupPanel.addView(makeLabel("키워드"));
        setupPanel.addView(keywordInput, inputParams());

        Button saveButton = makeButton("설정 저장", 0xFF2563EB);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFromInputs();
            }
        });
        setupPanel.addView(saveButton, buttonParams());

        Button guideButton = makeButton("사용 방법 / 이용 안내", 0xFF475569);
        guideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleUsageGuide();
            }
        });
        setupPanel.addView(guideButton, buttonParams());

        usageGuideText = new TextView(this);
        usageGuideText.setText(
            "처음 1회만 API 키, 채널 주소, 키워드를 저장하세요.\n\n"
                + "다음 방송부터는 PRISM 또는 YouTube 버튼만 누르면 LiveRank가 방송 중 순위 화면을 띄웁니다.\n\n"
                + "다른 앱 위에 표시 권한은 방송 시작 버튼을 누를 때 필요한 경우에만 안내됩니다.\n\n"
                + "방송 앱에서 라이브를 끝내면 LiveRank도 자동으로 정리됩니다."
        );
        usageGuideText.setTextColor(0xFF334155);
        usageGuideText.setTextSize(14);
        usageGuideText.setLineSpacing(dp(3), 1.05f);
        usageGuideText.setPadding(dp(14), dp(12), dp(14), dp(12));
        usageGuideText.setBackgroundColor(0xFFFFFFFF);
        usageGuideText.setVisibility(View.GONE);
        LinearLayout.LayoutParams guideParams = matchWrap();
        guideParams.setMargins(0, dp(10), 0, 0);
        setupPanel.addView(usageGuideText, guideParams);

        broadcastPanel = new LinearLayout(this);
        broadcastPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(broadcastPanel, matchWrap());

        savedSummary = new TextView(this);
        savedSummary.setTextColor(0xFF334155);
        savedSummary.setTextSize(15);
        savedSummary.setGravity(Gravity.CENTER);
        savedSummary.setVisibility(View.GONE);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.setMargins(0, 0, 0, dp(10));
        broadcastPanel.addView(savedSummary, summaryParams);

        Button prismButton = makeButton("PRISM으로 방송 시작", 0xFFE11D48);
        prismButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startBroadcast(PRISM_PACKAGES, PRISM_STORE_PACKAGE, "PRISM");
            }
        });
        broadcastPanel.addView(prismButton, buttonParams());

        Button youtubeButton = makeButton("YouTube 앱으로 방송 시작", 0xFF111827);
        youtubeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startBroadcast(YOUTUBE_PACKAGES, YOUTUBE_STORE_PACKAGE, "YouTube");
            }
        });
        broadcastPanel.addView(youtubeButton, buttonParams());

        Button reportButton = makeButton("최근 비교 분석 보기", 0xFF2563EB);
        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLatestComparisonReport();
            }
        });
        broadcastPanel.addView(reportButton, buttonParams());

        Button editButton = makeButton("설정", 0xFF475569);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSetup(true);
            }
        });
        broadcastPanel.addView(editButton, buttonParams());

        liveStatusBar = new LinearLayout(this);
        liveStatusBar.setOrientation(LinearLayout.HORIZONTAL);
        liveStatusBar.setGravity(Gravity.CENTER_VERTICAL);
        liveStatusBar.setPadding(dp(14), dp(8), dp(10), dp(8));
        liveStatusBar.setBackgroundColor(0xFFEFF6FF);

        TextView liveStatusText = new TextView(this);
        liveStatusText.setText("● 라이브 분석 중");
        liveStatusText.setTextColor(0xFF1D4ED8);
        liveStatusText.setTextSize(14);
        liveStatusText.setTypeface(null, 1);
        LinearLayout.LayoutParams liveStatusTextParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        );
        liveStatusBar.addView(liveStatusText, liveStatusTextParams);

        Button stopInlineButton = makeSmallButton("중지", 0xFF64748B);
        stopInlineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmStopLiveRank();
            }
        });
        liveStatusBar.addView(stopInlineButton, new LinearLayout.LayoutParams(dp(82), dp(40)));
        LinearLayout.LayoutParams liveStatusParams = matchWrap();
        liveStatusParams.setMargins(0, dp(12), 0, 0);
        liveStatusBar.setVisibility(View.GONE);
        broadcastPanel.addView(liveStatusBar, liveStatusParams);

        comparisonReportText = new TextView(this);
        comparisonReportText.setTextColor(0xFF172033);
        comparisonReportText.setTextSize(15);
        comparisonReportText.setLineSpacing(dp(3), 1.08f);
        comparisonReportText.setPadding(dp(14), dp(12), dp(14), dp(12));
        comparisonReportText.setBackgroundColor(0xFFFFFFFF);
        comparisonReportText.setVisibility(View.GONE);
        LinearLayout.LayoutParams reportParams = matchWrap();
        reportParams.setMargins(0, dp(10), 0, 0);
        broadcastPanel.addView(comparisonReportText, reportParams);

        comparisonDashboardView = new ComparisonDashboardView(this);
        comparisonDashboardView.setVisibility(View.GONE);
        LinearLayout.LayoutParams dashboardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dashboardParams.setMargins(0, dp(10), 0, 0);
        broadcastPanel.addView(comparisonDashboardView, dashboardParams);

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(14), 0, dp(8));
        root.addView(statusText, statusParams);

        loadSavedInputs();
        getSharedPreferences(PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
        renderMode();
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        continuePendingBroadcastIfAllowed();
        refreshLiveStatusBar();
        refreshVisibleComparisonReport();
    }

    @Override
    protected void onDestroy() {
        cancelStatusHide();
        getSharedPreferences(PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_LIVE_RANK_RUNNING.equals(key)) {
            refreshLiveStatusBar();
        }
    }

    private void loadSavedInputs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        apiInput.setText(prefs.getString("apiKey", ""));
        channelInput.setText(prefs.getString("channel", ""));
        keywordInput.setText(prefs.getString("keyword", ""));
    }

    private void saveFromInputs() {
        String apiKey = clean(apiInput.getText());
        String channel = clean(channelInput.getText());
        String keyword = clean(keywordInput.getText());
        if (apiKey.length() == 0 || channel.length() == 0 || keyword.length() == 0) {
            showStatus("API, 채널 주소, 키워드를 모두 입력해 주세요.", 0xFFB91C1C);
            return;
        }
        saveInputs(apiKey, channel, keyword);
        renderMode();
        showStatus("저장되었습니다. 다음부터 방송 방식만 누르면 됩니다.", 0xFF047857);
    }

    private void saveInputs(String apiKey, String channel, String keyword) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString("apiKey", apiKey)
            .putString("channel", channel)
            .putString("keyword", keyword)
            .apply();
    }

    private void renderMode() {
        boolean ready = hasSavedInputs();
        setupPanel.setVisibility(ready ? View.GONE : View.VISIBLE);
        broadcastPanel.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) {
            savedSummary.setText("");
        }
        refreshLiveStatusBar();
    }

    private void showSetup(boolean show) {
        setupPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        broadcastPanel.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private boolean hasSavedInputs() {
        return clean(apiInput.getText()).length() > 0
            && clean(channelInput.getText()).length() > 0
            && clean(keywordInput.getText()).length() > 0;
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        }
    }

    private void startBroadcast(String[] packageNames, String storePackageName, String appName) {
        if (!hasSavedInputs()) {
            showSetup(true);
            showStatus("먼저 API, 채널 주소, 키워드를 저장해 주세요.", 0xFFB91C1C);
            return;
        }
        if (!canDrawOverlays()) {
            showOverlayPermissionDialog(packageNames, storePackageName, appName);
            return;
        }
        if (!startLiveRank()) {
            return;
        }
        showStatus(appName + " 앱을 여는 중입니다.", 0xFF047857);
        openExternalApp(packageNames, storePackageName, appName);
    }

    private boolean startLiveRank() {
        if (!canDrawOverlays()) {
            return false;
        }

        String apiKey = clean(apiInput.getText());
        String channel = clean(channelInput.getText());
        String keyword = clean(keywordInput.getText());
        if (apiKey.length() == 0 || channel.length() == 0 || keyword.length() == 0) {
            showSetup(true);
            showStatus("먼저 API, 채널 주소, 키워드를 저장해 주세요.", 0xFFB91C1C);
            return false;
        }

        saveInputs(apiKey, channel, keyword);
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SHOW);
        intent.putExtra(OverlayService.EXTRA_API_KEY, apiKey);
        intent.putExtra(OverlayService.EXTRA_CHANNEL, channel);
        intent.putExtra(OverlayService.EXTRA_KEYWORD, keyword);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        setLiveRankRunning(true);
        refreshLiveStatusBar();
        return true;
    }

    private void openExternalApp(String[] packageNames, String storePackageName, String appName) {
        for (String packageName : packageNames) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return;
            }
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + storePackageName)));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + storePackageName)));
        }
        showStatus(appName + " 앱이 설치되어 있지 않습니다.", 0xFFB91C1C);
    }

    private void showOverlayPermissionDialog(final String[] packageNames, final String storePackageName, final String appName) {
        new AlertDialog.Builder(this)
            .setMessage("방송 중 순위 화면을 띄우려면 '다른 앱 위에 표시' 권한이 필요합니다. 처음 1회만 설정하면 됩니다.")
            .setPositiveButton("권한 열기", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingPermissionPackageNames = packageNames;
                    pendingPermissionStorePackageName = storePackageName;
                    pendingPermissionAppName = appName;
                    waitingForOverlayPermission = true;
                    openOverlayPermission();
                }
            })
            .setNegativeButton("나중에", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    clearPendingPermissionLaunch();
                }
            })
            .show();
    }

    private void continuePendingBroadcastIfAllowed() {
        if (!waitingForOverlayPermission) {
            return;
        }
        if (!canDrawOverlays()) {
            clearPendingPermissionLaunch();
            return;
        }
        String[] packageNames = pendingPermissionPackageNames;
        String storePackageName = pendingPermissionStorePackageName;
        String appName = pendingPermissionAppName;
        clearPendingPermissionLaunch();
        if (packageNames != null && storePackageName != null && appName != null) {
            startBroadcast(packageNames, storePackageName, appName);
        }
    }

    private void clearPendingPermissionLaunch() {
        pendingPermissionPackageNames = null;
        pendingPermissionStorePackageName = null;
        pendingPermissionAppName = null;
        waitingForOverlayPermission = false;
    }

    private void confirmStopLiveRank() {
        new AlertDialog.Builder(this)
            .setMessage("LiveRank 분석을 중지할까요? 방송은 종료되지 않습니다.")
            .setPositiveButton("중지", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    stopLiveRank();
                }
            })
            .setNegativeButton("취소", null)
            .show();
    }

    private void stopLiveRank() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_HIDE);
        startService(intent);
        setLiveRankRunning(false);
        refreshLiveStatusBar();
        showStatus("LiveRank 분석을 중지했습니다.", 0xFF047857);
    }

    private void showLatestComparisonReport() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String report = prefs.getString("lastComparisonReport", "");
        if (report.length() == 0) {
            comparisonDashboardView.setVisibility(View.GONE);
            comparisonReportText.setText("아직 비교 분석이 없습니다.\n방송 시작 후 50분이 지나면 자동으로 생성됩니다.");
            comparisonReportText.setVisibility(View.VISIBLE);
        } else if (!isSupportedComparisonReport(report)) {
            prefs.edit().remove("lastComparisonReport").apply();
            comparisonDashboardView.setVisibility(View.GONE);
            comparisonReportText.setText("이전 형식의 비교 분석은 새 화면에 표시하지 않습니다.\n방송을 다시 분석하면 3대 카테고리 구조로 새 리포트가 생성됩니다.");
            comparisonReportText.setVisibility(View.VISIBLE);
        } else {
            comparisonReportText.setVisibility(View.GONE);
            comparisonDashboardView.setReport(displayComparisonReport(report));
            comparisonDashboardView.setVisibility(View.VISIBLE);
        }
    }

    private String displayComparisonReport(String report) {
        if (report == null || report.trim().length() == 0) return "";
        if (!isSupportedComparisonReport(report)) {
            return "";
        }
        if (report.contains("이번에 비교한 것") || report.contains("이번 결과의 의미")) {
            return report;
        }
        if (!report.contains("LiveRank 비교 분석")) {
            return report;
        }

        String keyword = extractReportLine(report, "키워드:");
        if (keyword.length() == 0) keyword = "핵심 키워드";
        String summary = findReportLine(report, "내 채널 ", "점(");
        String priorities = extractSection(report, "개선 팁 (1위 따라잡기 전략)", "제목 개선 예시");
        if (priorities.length() == 0) priorities = extractSection(report, "핵심 개선 우선순위", "제목 개선 예시");

        StringBuilder builder = new StringBuilder(report.trim());
        builder.append("\n\n------------------------------\n");
        builder.append("이 리포트 해석\n");
        if (summary.length() > 0) {
            builder.append("- ").append(summary).append("\n");
        }
        builder.append("- 위 점수는 공개 API로 확인 가능한 트래픽 질량, 채널 영향, 기본 최적화를 비교한 값입니다.\n");
        builder.append("- 점수 자체보다 중요한 것은 아래 개선 팁입니다. 낮은 항목부터 고치면 같은 키워드에서 경쟁력을 높일 수 있습니다.\n\n");

        builder.append("무엇을 비교했나\n");
        builder.append("1. 트래픽 질량 (60점 묶음): 현재 시청자, 노필터 순위 접근도, 구독자 대비 현재 시청자 효율\n");
        builder.append("2. 채널 영향 (30점 묶음): 구독자 규모, 채널 누적 조회/영상 기반, 채널 정보 확인성\n");
        builder.append("3. 기본 최적화 (10점 묶음): 제목, 설명, 썸네일 기본 신호\n");
        builder.append("4. 반응 품질: 채팅 참여율과 좋아요 반응은 점수와 분리해 참고\n\n");

        builder.append("왜 이게 필요한가\n");
        builder.append("- 사용자는 점수만으로 행동할 수 없습니다. 이 화면은 어떤 항목이 1위보다 약한지 보고 바로 수정하기 위한 화면입니다.\n");
        builder.append("- 특히 트래픽 질량이 낮으면 방송 초반 유입 동선과 시청 이유 제시를 먼저 점검해야 합니다.\n");
        builder.append("- 기본 최적화가 낮으면 제목·태그·설명에 키워드와 오늘 방송의 차별점을 더 분명히 넣어야 합니다.\n\n");

        builder.append("오늘 바로 할 일\n");
        if (priorities.contains("트래픽")) {
            builder.append("1. 방송 시작 직후 질문 또는 참여 이유를 제시하세요.\n");
            builder.append("2. 초반 10분 안에 채팅 참여와 좋아요 반응을 자연스럽게 유도하세요.\n");
        }
        if (priorities.contains("기본") || priorities.contains("제목")) {
            builder.append("3. 제목 앞부분을 '").append(keyword).append(" 라이브 | 오늘 핵심 내용'처럼 키워드 먼저 보이게 바꾸세요.\n");
        }
        builder.append("4. 설명 첫 2줄에 '").append(keyword).append("', 오늘 다룰 내용, 시청자가 얻는 이득을 적으세요.\n\n");

        builder.append("다음 리포트부터\n");
        builder.append("- 새 버전에서는 위 해석이 기본 리포트 안에 더 자세히 포함됩니다.\n");
        builder.append("- 1등 노출이나 조회수 상승을 보장하지 않고, 공개 데이터 기준 개선 방향만 제공합니다.");
        return builder.toString();
    }

    private boolean isSupportedComparisonReport(String report) {
        if (report == null || report.trim().length() == 0) return false;
        if (hasLegacyComparisonCategory(report)) return false;
        if (report.contains("이번 결과의 의미")) return true;
        return report.contains("트래픽 질량")
            && report.contains("채널 영향")
            && report.contains("기본 최적화")
            && report.contains("반응 품질 참고");
    }

    private boolean hasLegacyComparisonCategory(String report) {
        String text = report == null ? "" : report;
        return text.contains("제목 최적화")
            || text.contains("썸네일(클릭력)")
            || text.contains("메타데이터")
            || text.contains("라이브 현재 성과")
            || text.contains("채널 신뢰도")
            || text.contains("시청자 반응")
            || text.contains("발행 전략");
    }

    private String extractReportLine(String report, String prefix) {
        String[] lines = report.split("\\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.startsWith(prefix)) {
                return cleanLine.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String findReportLine(String report, String required, String alsoRequired) {
        String[] lines = report.split("\\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.contains(required) && cleanLine.contains(alsoRequired)) {
                return cleanLine;
            }
        }
        return "";
    }

    private String extractSection(String report, String start, String end) {
        int startIndex = report.indexOf(start);
        if (startIndex < 0) return "";
        int sectionStart = startIndex + start.length();
        int endIndex = report.indexOf(end, sectionStart);
        if (endIndex < 0) endIndex = report.length();
        return report.substring(sectionStart, endIndex).trim();
    }

    private void refreshVisibleComparisonReport() {
        boolean textVisible = comparisonReportText != null && comparisonReportText.getVisibility() == View.VISIBLE;
        boolean dashboardVisible = comparisonDashboardView != null && comparisonDashboardView.getVisibility() == View.VISIBLE;
        if (textVisible || dashboardVisible) {
            showLatestComparisonReport();
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void toggleUsageGuide() {
        usageGuideText.setVisibility(usageGuideText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void showStatus(String message, int color) {
        cancelStatusHide();
        statusText.setText(message);
        statusText.setTextColor(color);
        statusText.setVisibility(View.VISIBLE);
        hideStatusRunnable = new Runnable() {
            @Override
            public void run() {
                statusText.setVisibility(View.GONE);
                hideStatusRunnable = null;
            }
        };
        statusText.postDelayed(hideStatusRunnable, 4000L);
    }

    private void cancelStatusHide() {
        if (statusText != null && hideStatusRunnable != null) {
            statusText.removeCallbacks(hideStatusRunnable);
            hideStatusRunnable = null;
        }
    }

    private void setLiveRankRunning(boolean running) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIVE_RANK_RUNNING, running)
            .apply();
    }

    private boolean isLiveRankRunning() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LIVE_RANK_RUNNING, false);
    }

    private void refreshLiveStatusBar() {
        if (liveStatusBar != null) {
            liveStatusBar.setVisibility(isLiveRankRunning() ? View.VISIBLE : View.GONE);
        }
    }

    private TextView makeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFF0F172A);
        label.setTextSize(15);
        label.setTypeface(null, 1);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(10), 0, dp(4));
        label.setLayoutParams(params);
        return label;
    }

    private EditText makeInput(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackgroundColor(0xFFFFFFFF);
        input.setInputType(password
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        return button;
    }

    private Button makeSmallButton(String text, int color) {
        Button button = makeButton(text, color);
        button.setTextSize(14);
        return button;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        );
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(54)
        );
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private String clean(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
