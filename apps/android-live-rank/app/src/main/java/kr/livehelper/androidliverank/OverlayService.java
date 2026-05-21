package kr.livehelper.androidliverank;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class OverlayService extends Service {
    public static final String ACTION_SHOW = "kr.livehelper.androidliverank.SHOW";
    public static final String ACTION_HIDE = "kr.livehelper.androidliverank.HIDE";
    public static final String EXTRA_API_KEY = "apiKey";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_KEYWORD = "keyword";

    private static final String CHANNEL_ID = "livehelper_live_rank";
    private static final String PREFS = "livehelper_android_live_rank";
    private static final String KEY_LIVE_RANK_RUNNING = "liveRankRunning";
    private static final long FIRST_QUERY_DELAY_MS = 5 * 60 * 1000L;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long COMPARISON_DELAY_MS = 50 * 60 * 1000L;
    private static final int MAX_PRE_LIVE_MISSES = 3;
    private static final int MAX_POLL_ERRORS = 3;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout panel;
    private View overlayView;
    private TextView titleText;
    private TextView rankText;
    private TextView subText;
    private WindowManager.LayoutParams overlayParams;
    private Runnable collapseRunnable;
    private String apiKey = "";
    private String channel = "";
    private String keyword = "";
    private volatile boolean polling;
    private Thread pollingThread;
    private int startX;
    private int startY;
    private float touchStartX;
    private float touchStartY;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(1001, buildNotification("라이브 순위 준비 중"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            setLiveRankRunning(false);
            stopPolling();
            hideOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            apiKey = safe(intent.getStringExtra(EXTRA_API_KEY));
            channel = safe(intent.getStringExtra(EXTRA_CHANNEL));
            keyword = safe(intent.getStringExtra(EXTRA_KEYWORD));
        }
        setLiveRankRunning(true);
        startPolling();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        setLiveRankRunning(false);
        stopPolling();
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean ensureOverlay() {
        if (overlayView != null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return false;
        }

        overlayView = createOverlayView();
        overlayParams = createOverlayParams();
        windowManager.addView(overlayView, overlayParams);
        return true;
    }

    private void hideOverlay() {
        cancelCollapse();
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            panel = null;
            titleText = null;
            rankText = null;
            subText = null;
            overlayParams = null;
        }
    }

    private void startPolling() {
        stopPolling();
        if (apiKey.length() == 0 || channel.length() == 0 || keyword.length() == 0) {
            updateOverlay("라이브 순위", "입력 필요", "앱에서 API/채널/키워드를 입력해 주세요.");
            return;
        }

        polling = true;
        pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                LiveRankClient client = new LiveRankClient(apiKey);
                int preLiveMisses = 0;
                int preLiveErrors = 0;
                final long monitoringStartedAt = System.currentTimeMillis();
                updateForeground("첫 순위 조회는 5분 후 시작");
                if (!sleepFor(FIRST_QUERY_DELAY_MS)) {
                    return;
                }

                while (polling) {
                    try {
                        LiveRankClient.ActiveLiveStatus activeStatus = client.inspectActiveLive(channel);
                        preLiveErrors = 0;
                        if (activeStatus.count > 1) {
                            showMessageThenStop(
                                "동시 라이브 감지",
                                "지원 안 함",
                                "라이브 1개만 켜고 다시 시작해 주세요."
                            );
                            return;
                        }
                        if (activeStatus.count == 1 && activeStatus.firstVideoId.length() > 0) {
                            preLiveMisses = 0;
                            String lockedVideoId = activeStatus.firstVideoId;
                            updateForeground("라이브 고정: " + shortMessage(activeStatus.firstTitle));
                            runLockedLivePolling(client, lockedVideoId, monitoringStartedAt);
                            return;
                        }

                        preLiveMisses += 1;
                        updateForeground("유튜브 라이브 검색 반영 대기 " + preLiveMisses + "/" + MAX_PRE_LIVE_MISSES);
                        if (preLiveMisses >= MAX_PRE_LIVE_MISSES) {
                            autoStop("라이브 미확인으로 자동 종료");
                            return;
                        }
                    } catch (Exception error) {
                        preLiveErrors += 1;
                        postOverlay("조회 실패", "확인 필요", shortMessage(error.getMessage()));
                        updateForeground("라이브 순위 조회 실패");
                        if (preLiveErrors >= MAX_POLL_ERRORS) {
                            autoStop("연속 조회 실패로 자동 종료");
                            return;
                        }
                    }
                    if (!sleepFor(REFRESH_INTERVAL_MS)) {
                        return;
                    }
                }
            }
        }, "LiveHelperRankPoller");
        pollingThread.start();
    }

    private void runLockedLivePolling(LiveRankClient client, String lockedVideoId, long monitoringStartedAt) {
        boolean comparisonDone = false;
        int consecutiveErrors = 0;
        while (polling) {
            try {
                if (!client.isVideoLive(lockedVideoId)) {
                    autoStop("라이브 종료 확인, 순위 조회 중지");
                    return;
                }
                LiveRankClient.Result result = client.fetchLiveRankForVideo(channel, keyword, lockedVideoId);
                consecutiveErrors = 0;
                postResult(result);
                updateForeground("노필터 " + rankLabel(result.noFilterRank) + " · 라이브 " + rankLabel(result.liveRank));
                if (!comparisonDone && System.currentTimeMillis() - monitoringStartedAt >= COMPARISON_DELAY_MS) {
                    try {
                        comparisonDone = true;
                        ComparisonReport report = client.fetchComparisonReport(channel, keyword, lockedVideoId);
                        saveComparisonReport(report);
                        postOverlay("비교 분석 준비", report.summary, "앱에서 최근 비교 분석을 확인하세요.");
                        updateForeground("비교 분석 준비 완료");
                    } catch (Exception reportError) {
                        comparisonDone = false;
                        updateForeground("비교 분석 실패: " + shortMessage(reportError.getMessage()));
                    }
                }
            } catch (Exception error) {
                consecutiveErrors += 1;
                postOverlay("조회 실패", "확인 필요", shortMessage(error.getMessage()));
                updateForeground("라이브 순위 조회 실패");
                if (consecutiveErrors >= MAX_POLL_ERRORS) {
                    autoStop("연속 조회 실패로 자동 종료");
                    return;
                }
            }
            if (!sleepFor(REFRESH_INTERVAL_MS)) {
                return;
            }
        }
    }

    private void saveComparisonReport(ComparisonReport report) {
        String detail = report.detail == null ? "" : report.detail.trim();
        if (detail.length() > 0) {
            getSharedPreferences("livehelper_android_live_rank", MODE_PRIVATE)
                .edit()
                .putString("lastComparisonReport", detail)
                .putString("lastComparisonAt", String.valueOf(System.currentTimeMillis()))
                .apply();
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("LiveRank 비교 분석\n");
        builder.append("키워드: ").append(report.keyword).append("\n");
        builder.append("내 채널: ").append(report.ownChannelTitle).append("\n");
        builder.append("노필터 라이브 1위: ").append(report.competitorChannelTitle.length() == 0 ? "비교 대상 없음" : report.competitorChannelTitle).append("\n\n");
        builder.append(report.summary).append("\n\n");
        builder.append("개선 팁 (1위 따라잡기 전략)\n").append(report.priorities).append("\n\n");
        builder.append("제목 개선 예시\n").append(report.titleSuggestion).append("\n\n");
        builder.append("참고: 공개 데이터 기준 비교이며 1등 노출이나 조회수 상승을 보장하지 않습니다.");
        getSharedPreferences("livehelper_android_live_rank", MODE_PRIVATE)
            .edit()
            .putString("lastComparisonReport", builder.toString())
            .putString("lastComparisonAt", String.valueOf(System.currentTimeMillis()))
            .apply();
    }

    private void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
    }

    private boolean sleepFor(long durationMs) {
        long remaining = durationMs;
        while (polling && remaining > 0) {
            long chunk = Math.min(remaining, 1000L);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException ignored) {
                return false;
            }
            remaining -= chunk;
        }
        return polling;
    }

    private boolean hasRank(LiveRankClient.Result result) {
        return result.noFilterRank > 0 || result.liveRank > 0;
    }

    private void autoStop(final String message) {
        polling = false;
        setLiveRankRunning(false);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                hideOverlay();
                updateForeground(message);
                stopSelf();
            }
        });
    }

    private void showMessageThenStop(String title, String rank, String sub) {
        postOverlay(title, rank, sub);
        updateForeground(title);
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException ignored) {
            return;
        }
        autoStop(title);
    }

    private void postOverlay(final String title, final String rank, final String sub) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                updateOverlay(title, rank, sub);
            }
        });
    }

    private void postResult(final LiveRankClient.Result result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                renderResult(result);
            }
        });
    }

    private void updateOverlay(String title, String rank, String sub) {
        if (!ensureOverlay()) return;
        applyDisplayMode(true);
        if (titleText != null) titleText.setText(title);
        if (rankText != null) rankText.setText(rank);
        if (subText != null) subText.setText(sub);
        scheduleHide();
    }

    private void renderResult(final LiveRankClient.Result result) {
        if (!ensureOverlay()) return;
        final String popupRank = "1. 노필터 " + rankLabel(result.noFilterRank) + "\n2. 라이브 " + rankLabel(result.liveRank);
        String sub = "5분마다 갱신";
        if (result.topLiveChannelTitle.length() > 0) {
            sub += " · 라이브 1위 " + result.topLiveChannelTitle;
        } else if (result.topNoFilterChannelTitle.length() > 0) {
            sub += " · 노필터 1위 " + result.topNoFilterChannelTitle;
        }

        applyDisplayMode(true);
        titleText.setText(result.keyword.length() > 0 ? result.keyword : "라이브 순위");
        rankText.setText(popupRank);
        subText.setText(sub);
        scheduleHide();
    }

    private void scheduleHide() {
        cancelCollapse();
        collapseRunnable = new Runnable() {
            @Override
            public void run() {
                hideOverlay();
            }
        };
        mainHandler.postDelayed(collapseRunnable, 5000);
    }

    private void cancelCollapse() {
        if (collapseRunnable != null) {
            mainHandler.removeCallbacks(collapseRunnable);
            collapseRunnable = null;
        }
    }

    private String rankLabel(int rank) {
        return rank > 0 ? rank + "위" : "20위 밖";
    }

    private void applyDisplayMode(boolean popup) {
        if (panel == null || titleText == null || rankText == null || subText == null) return;
        panel.setPadding(dp(18), dp(14), dp(14), dp(14));
        titleText.setTextSize(16);
        rankText.setTextSize(29);
        subText.setTextSize(13);
        rankText.setMaxLines(2);
        subText.setMaxLines(2);
        if (overlayView != null && overlayParams != null) {
            windowManager.updateViewLayout(overlayView, overlayParams);
        }
    }

    private View createOverlayView() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFE500);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(2), 0xFF111827);
        panel.setBackground(background);

        LinearLayout textStack = new LinearLayout(this);
        textStack.setOrientation(LinearLayout.VERTICAL);

        titleText = new TextView(this);
        titleText.setTextColor(0xFF111827);
        titleText.setTextSize(13);
        titleText.setTypeface(null, 1);
        textStack.addView(titleText);

        rankText = new TextView(this);
        rankText.setTextColor(0xFF111827);
        rankText.setTextSize(24);
        rankText.setTypeface(null, 1);
        textStack.addView(rankText);

        subText = new TextView(this);
        subText.setTextColor(0xFF334155);
        subText.setTextSize(12);
        subText.setMaxLines(2);
        textStack.addView(subText);

        panel.addView(textStack);

        Button closeButton = new Button(this);
        closeButton.setText("X");
        closeButton.setTextSize(12);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setBackgroundColor(0xFF111827);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeParams.setMargins(dp(12), 0, 0, 0);
        panel.addView(closeButton, closeParams);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopPolling();
                hideOverlay();
                stopSelf();
            }
        });

        panel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (overlayParams == null) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = overlayParams.x;
                        startY = overlayParams.y;
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        overlayParams.x = startX + (int) (event.getRawX() - touchStartX);
                        overlayParams.y = startY + (int) (event.getRawY() - touchStartY);
                        windowManager.updateViewLayout(overlayView, overlayParams);
                        return true;
                    default:
                        return false;
                }
            }
        });

        return panel;
    }

    private WindowManager.LayoutParams createOverlayParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_SECURE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(18);
        params.y = dp(120);
        return params;
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "라이브 도우미 라이브 순위",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_livehelper)
            .setContentTitle("라이브 도우미")
            .setContentText(text)
            .setOngoing(true)
            .build();
    }

    private void updateForeground(String text) {
        startForeground(1001, buildNotification(text));
    }

    private String shortMessage(String value) {
        String clean = safe(value);
        if (clean.length() == 0) return "API 키, 채널 주소, 키워드를 확인해 주세요.";
        return clean.length() > 55 ? clean.substring(0, 55) + "..." : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void setLiveRankRunning(boolean running) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIVE_RANK_RUNNING, running)
            .apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
