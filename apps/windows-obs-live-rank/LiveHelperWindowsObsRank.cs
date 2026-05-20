using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Drawing.Drawing2D;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace LiveHelperWindowsObsRank
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    internal sealed class MainForm : Form
    {
        private const int FirstDelayMs = 5 * 60 * 1000;
        private const int RefreshMs = 5 * 60 * 1000;
        private const int ComparisonDelayMs = 50 * 60 * 1000;
        private const int MaxPreLiveMisses = 3;

        private readonly TextBox apiBox = new TextBox();
        private readonly TextBox channelBox = new TextBox();
        private readonly TextBox keywordBox = new TextBox();
        private readonly Panel mainPanel = new Panel();
        private readonly Panel settingsPanel = new Panel();
        private readonly Panel liveStatusBar = new Panel();
        private readonly Label liveStatusLabel = new Label();
        private readonly Label statusLabel = new Label();
        private readonly Button startButton = new Button();
        private readonly Button stopButton = new Button();
        private readonly Button reportButton = new Button();
        private readonly Button settingsButton = new Button();
        private readonly Button saveButton = new Button();
        private readonly Button guideButton = new Button();
        private readonly Button backButton = new Button();
        private readonly Label guideLabel = new Label();
        private readonly Timer firstTimer = new Timer();
        private readonly Timer refreshTimer = new Timer();
        private readonly string settingsPath;
        private readonly string reportPath;
        private readonly string sessionPath;

        private bool isChecking;
        private bool isGeneratingComparison;
        private int preLiveMisses;
        private int consecutiveErrors;
        private string lockedVideoId = "";
        private string lastKnownVideoId = "";
        private string lastKnownVideoTitle = "";
        private DateTime lastSessionStartedAtUtc = DateTime.MinValue;
        private DateTime lastSessionEndedAtUtc = DateTime.MinValue;
        private bool monitoringActive;
        private bool comparisonDone;
        private DateTime monitoringStartedAtUtc;
        private string latestComparisonReport = "";
        private YoutubeClient client;
        private RankPopupForm popup;
        private Timer popupHideTimer;

        public MainForm()
        {
            Text = "라이브 도우미 Windows OBS";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            ClientSize = new Size(430, 340);
            BackColor = Color.FromArgb(248, 250, 252);

            string settingsDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "LiveHelperWindowsObsRank"
            );
            Directory.CreateDirectory(settingsDir);
            settingsPath = Path.Combine(settingsDir, "settings.txt");
            reportPath = Path.Combine(settingsDir, "latest-comparison-report.txt");
            sessionPath = Path.Combine(settingsDir, "last-session.txt");

            BuildUi();
            LoadSettings();
            LoadLatestComparisonReport();
            LoadLastSession();

            firstTimer.Interval = FirstDelayMs;
            firstTimer.Tick += (sender, args) =>
            {
                firstTimer.Stop();
                CheckOnce();
            };

            refreshTimer.Interval = RefreshMs;
            refreshTimer.Tick += (sender, args) => CheckOnce();
        }

        private void BuildUi()
        {
            Label title = new Label();
            title.Text = "라이브 도우미";
            title.Font = new Font("Malgun Gothic", 24, FontStyle.Bold);
            title.ForeColor = Color.FromArgb(15, 23, 42);
            title.TextAlign = ContentAlignment.MiddleCenter;
            title.SetBounds(20, 22, 390, 44);
            Controls.Add(title);

            mainPanel.SetBounds(0, 92, 430, 248);
            Controls.Add(mainPanel);

            startButton.Text = "OBS 방송 시작";
            startButton.Font = new Font("Malgun Gothic", 12, FontStyle.Bold);
            startButton.ForeColor = Color.White;
            startButton.BackColor = Color.FromArgb(225, 29, 72);
            startButton.FlatStyle = FlatStyle.Flat;
            startButton.SetBounds(24, 0, 382, 44);
            startButton.Click += (sender, args) => StartMonitoring();
            mainPanel.Controls.Add(startButton);

            reportButton.Text = "최근 비교 분석 보기";
            reportButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            reportButton.ForeColor = Color.White;
            reportButton.BackColor = Color.FromArgb(37, 99, 235);
            reportButton.FlatStyle = FlatStyle.Flat;
            reportButton.SetBounds(24, 56, 382, 44);
            reportButton.Click += (sender, args) => ShowLatestComparisonReport();
            mainPanel.Controls.Add(reportButton);

            settingsButton.Text = "설정";
            settingsButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            settingsButton.ForeColor = Color.White;
            settingsButton.BackColor = Color.FromArgb(71, 85, 105);
            settingsButton.FlatStyle = FlatStyle.Flat;
            settingsButton.SetBounds(24, 112, 382, 44);
            settingsButton.Click += (sender, args) => ShowSettings();
            mainPanel.Controls.Add(settingsButton);

            liveStatusBar.SetBounds(24, 172, 382, 44);
            liveStatusBar.BackColor = Color.FromArgb(239, 246, 255);
            liveStatusBar.Visible = false;
            mainPanel.Controls.Add(liveStatusBar);

            liveStatusLabel.Text = "● 라이브 분석 중";
            liveStatusLabel.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            liveStatusLabel.ForeColor = Color.FromArgb(29, 78, 216);
            liveStatusLabel.TextAlign = ContentAlignment.MiddleLeft;
            liveStatusLabel.SetBounds(14, 0, 260, 44);
            liveStatusBar.Controls.Add(liveStatusLabel);

            stopButton.Text = "중지";
            stopButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            stopButton.ForeColor = Color.White;
            stopButton.BackColor = Color.FromArgb(71, 85, 105);
            stopButton.FlatStyle = FlatStyle.Flat;
            stopButton.SetBounds(296, 7, 72, 30);
            stopButton.Click += (sender, args) => ConfirmStopMonitoring();
            liveStatusBar.Controls.Add(stopButton);

            statusLabel.Font = new Font("Malgun Gothic", 9, FontStyle.Regular);
            statusLabel.ForeColor = Color.FromArgb(71, 85, 105);
            statusLabel.TextAlign = ContentAlignment.MiddleCenter;
            statusLabel.SetBounds(24, 224, 382, 22);
            statusLabel.Visible = false;
            mainPanel.Controls.Add(statusLabel);

            settingsPanel.SetBounds(0, 92, 430, 608);
            settingsPanel.Visible = false;
            Controls.Add(settingsPanel);

            AddLabel(settingsPanel, "API", 24, 0);
            apiBox.SetBounds(24, 22, 382, 30);
            apiBox.PasswordChar = '*';
            settingsPanel.Controls.Add(apiBox);

            AddLabel(settingsPanel, "채널 주소", 24, 60);
            channelBox.SetBounds(24, 82, 382, 30);
            settingsPanel.Controls.Add(channelBox);

            AddLabel(settingsPanel, "키워드", 24, 120);
            keywordBox.SetBounds(24, 142, 382, 30);
            settingsPanel.Controls.Add(keywordBox);

            saveButton.Text = "설정 저장";
            saveButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            saveButton.ForeColor = Color.White;
            saveButton.BackColor = Color.FromArgb(37, 99, 235);
            saveButton.FlatStyle = FlatStyle.Flat;
            saveButton.SetBounds(24, 192, 382, 40);
            saveButton.Click += (sender, args) => SaveSettingsFromForm();
            settingsPanel.Controls.Add(saveButton);

            guideButton.Text = "사용 방법 / 이용 안내";
            guideButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            guideButton.ForeColor = Color.White;
            guideButton.BackColor = Color.FromArgb(71, 85, 105);
            guideButton.FlatStyle = FlatStyle.Flat;
            guideButton.SetBounds(24, 244, 382, 40);
            guideButton.Click += (sender, args) => ToggleGuide();
            settingsPanel.Controls.Add(guideButton);

            guideLabel.Text =
                "처음 1회만 API 키, 채널 주소, 키워드를 저장하세요.\r\n\r\n"
                + "다음 방송부터는 OBS 방송 시작 버튼만 누르면 LiveRank가 자동으로 분석을 시작합니다.\r\n\r\n"
                + "방송 앱에서 라이브를 끝내면 LiveRank도 자동으로 정리됩니다.\r\n\r\n"
                + "분석을 수동으로 멈추고 싶을 때만 [중지] 버튼을 사용하세요.\r\n\r\n"
                + "LiveRank는 YouTube 라이브 신호 자체를 분석하므로, 어떤 방송 앱을 사용하셔도 분석이 동작합니다. 기본 추천은 PC는 OBS, 모바일은 PRISM 또는 YouTube 앱입니다.";
            guideLabel.Font = new Font("Malgun Gothic", 9, FontStyle.Regular);
            guideLabel.ForeColor = Color.FromArgb(51, 65, 85);
            guideLabel.BackColor = Color.White;
            guideLabel.Padding = new Padding(12, 10, 12, 10);
            guideLabel.SetBounds(24, 296, 382, 230);
            guideLabel.Visible = true;
            settingsPanel.Controls.Add(guideLabel);

            backButton.Text = "메인으로 돌아가기";
            backButton.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
            backButton.ForeColor = Color.White;
            backButton.BackColor = Color.FromArgb(100, 116, 139);
            backButton.FlatStyle = FlatStyle.Flat;
            backButton.SetBounds(24, 540, 382, 40);
            backButton.Click += (sender, args) => ShowMain();
            settingsPanel.Controls.Add(backButton);
        }

        private void AddLabel(Control parent, string text, int x, int y)
        {
            Label label = new Label();
            label.Text = text;
            label.Font = new Font("Malgun Gothic", 9, FontStyle.Bold);
            label.ForeColor = Color.FromArgb(15, 23, 42);
            label.SetBounds(x, y, 150, 20);
            parent.Controls.Add(label);
        }

        private void ShowMain()
        {
            settingsPanel.Visible = false;
            mainPanel.Visible = true;
            ClientSize = new Size(430, 340);
            UpdateLiveStatusBar();
        }

        private void ShowSettings()
        {
            mainPanel.Visible = false;
            settingsPanel.Visible = true;
            ClientSize = new Size(430, 700);
            WindowState = FormWindowState.Normal;
            Activate();
        }

        private void SaveSettingsFromForm()
        {
            if (apiBox.Text.Trim().Length == 0 || channelBox.Text.Trim().Length == 0 || keywordBox.Text.Trim().Length == 0)
            {
                MessageBox.Show(this, "API, 채널 주소, 키워드를 모두 입력해 주세요.", "설정 저장", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            SaveSettings();
            ShowMain();
            SetStatus("설정이 저장되었습니다.", false);
        }

        private void ToggleGuide()
        {
            guideLabel.Visible = !guideLabel.Visible;
        }

        private void ConfirmStopMonitoring()
        {
            using (Form dialog = new Form())
            {
                dialog.Text = "LiveRank 분석 중지";
                dialog.StartPosition = FormStartPosition.CenterParent;
                dialog.FormBorderStyle = FormBorderStyle.FixedDialog;
                dialog.MaximizeBox = false;
                dialog.MinimizeBox = false;
                dialog.ClientSize = new Size(360, 146);
                dialog.BackColor = Color.White;

                Label message = new Label();
                message.Text = "LiveRank 분석을 중지할까요?\r\n방송은 종료되지 않습니다.";
                message.Font = new Font("Malgun Gothic", 10, FontStyle.Regular);
                message.ForeColor = Color.FromArgb(15, 23, 42);
                message.TextAlign = ContentAlignment.MiddleCenter;
                message.SetBounds(18, 18, 324, 52);
                dialog.Controls.Add(message);

                Button stop = new Button();
                stop.Text = "중지";
                stop.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
                stop.ForeColor = Color.White;
                stop.BackColor = Color.FromArgb(225, 29, 72);
                stop.FlatStyle = FlatStyle.Flat;
                stop.DialogResult = DialogResult.OK;
                stop.SetBounds(82, 88, 88, 36);
                dialog.Controls.Add(stop);

                Button cancel = new Button();
                cancel.Text = "취소";
                cancel.Font = new Font("Malgun Gothic", 10, FontStyle.Bold);
                cancel.ForeColor = Color.White;
                cancel.BackColor = Color.FromArgb(100, 116, 139);
                cancel.FlatStyle = FlatStyle.Flat;
                cancel.DialogResult = DialogResult.Cancel;
                cancel.SetBounds(190, 88, 88, 36);
                dialog.Controls.Add(cancel);

                dialog.AcceptButton = stop;
                dialog.CancelButton = cancel;

                if (dialog.ShowDialog(this) == DialogResult.OK)
                {
                    StopMonitoring("LiveRank 분석을 중지했습니다.");
                }
            }
        }

        private void UpdateLiveStatusBar()
        {
            liveStatusBar.Visible = monitoringActive;
        }

        private void StartMonitoring()
        {
            string apiKey = apiBox.Text.Trim();
            string channel = channelBox.Text.Trim();
            string keyword = keywordBox.Text.Trim();
            if (apiKey.Length == 0 || channel.Length == 0 || keyword.Length == 0)
            {
                ShowSettings();
                MessageBox.Show(this, "먼저 API, 채널 주소, 키워드를 저장해 주세요.", "설정 필요", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            SaveSettings();
            StopTimersOnly();
            client = new YoutubeClient(apiKey);
            preLiveMisses = 0;
            consecutiveErrors = 0;
            lockedVideoId = "";
            lastKnownVideoId = "";
            lastKnownVideoTitle = "";
            latestComparisonReport = "";
            comparisonDone = false;
            monitoringStartedAtUtc = DateTime.UtcNow;
            lastSessionStartedAtUtc = monitoringStartedAtUtc;
            lastSessionEndedAtUtc = DateTime.MinValue;
            monitoringActive = true;
            isChecking = false;
            SaveLastSession();
            ClearLatestComparisonReport();

            TryOpenObs();
            UpdateLiveStatusBar();
            SetStatus("첫 순위 조회는 5분 후 시작합니다.", false);
            WindowState = FormWindowState.Minimized;
            firstTimer.Start();
        }

        private void CheckOnce()
        {
            if (isChecking) return;
            isChecking = true;
            SetStatus("YouTube 순위를 확인하는 중입니다.", false);

            System.Threading.ThreadPool.QueueUserWorkItem(_ =>
            {
                try
                {
                    if (lockedVideoId.Length == 0)
                    {
                        ActiveLiveStatus active = client.InspectActiveLive(channelBox.Text.Trim());
                        if (active.Count == 0)
                        {
                            active = client.InspectOwnLiveFromKeyword(channelBox.Text.Trim(), keywordBox.Text.Trim());
                        }

                        if (active.Count == 0)
                        {
                            if (FetchAndShowRank()) return;
                        }

                        if (active.Count > 1)
                        {
                            BeginInvoke(new Action(() =>
                            {
                                StopMonitoring("동시 라이브 감지로 자동 종료했습니다.", false);
                                ShowPopup("동시 라이브 감지", "지원 안 함", "라이브 1개만 켜고 다시 시작해 주세요.");
                            }));
                            return;
                        }

                        if (active.Count == 1 && active.FirstVideoId.Length > 0)
                        {
                            lockedVideoId = active.FirstVideoId;
                            lastKnownVideoId = active.FirstVideoId;
                            lastKnownVideoTitle = active.FirstTitle;
                            SaveLastSession();
                            preLiveMisses = 0;
                            BeginInvoke(new Action(() => SetStatus("라이브를 고정했습니다: " + ShortText(active.FirstTitle), false)));
                            FetchAndShowRank();
                            return;
                        }

                        preLiveMisses += 1;
                        if (preLiveMisses >= MaxPreLiveMisses)
                        {
                            BeginInvoke(new Action(() => StopMonitoring("라이브 미확인으로 자동 종료했습니다.")));
                            return;
                        }

                        BeginInvoke(new Action(() => SetStatus("유튜브 라이브 검색 반영 대기 " + preLiveMisses + "/" + MaxPreLiveMisses, false)));
                        BeginInvoke(new Action(() => ShowPopup("라이브 감지 대기", preLiveMisses + "/" + MaxPreLiveMisses, "아직 내 라이브를 찾지 못했습니다.")));
                        BeginInvoke(new Action(() => refreshTimer.Start()));
                        return;
                    }

                    FetchAndShowRank();
                }
                catch (Exception ex)
                {
                    consecutiveErrors += 1;
                    BeginInvoke(new Action(() =>
                    {
                        ShowPopup("조회 실패", "확인 필요", ShortText(ex.Message));
                        SetStatus("조회 실패: " + ShortText(ex.Message), true);
                        if (consecutiveErrors >= 3)
                        {
                            StopMonitoring("연속 조회 실패로 자동 종료했습니다.");
                            return;
                        }
                        refreshTimer.Start();
                    }));
                }
                finally
                {
                    isChecking = false;
                }
            });
        }

        private bool FetchAndShowRank()
        {
            if (lockedVideoId.Length > 0 && !client.IsVideoLive(lockedVideoId))
            {
                BeginInvoke(new Action(() => StopMonitoring("라이브 종료 확인, 순위 조회를 멈췄습니다.")));
                return false;
            }

            RankResult result = client.FetchLiveRankForVideo(
                channelBox.Text.Trim(),
                keywordBox.Text.Trim(),
                lockedVideoId
            );

            bool isVisible = result.NoFilterRank > 0 || result.LiveRank > 0;
            if (result.TargetVideoId.Length > 0)
            {
                lockedVideoId = result.TargetVideoId;
                lastKnownVideoId = result.TargetVideoId;
                lastKnownVideoTitle = result.TargetTitle;
                SaveLastSession();
            }

            if (lockedVideoId.Length > 0 && !client.IsVideoLive(lockedVideoId))
            {
                BeginInvoke(new Action(() => StopMonitoring("라이브 종료 확인, 순위 조회를 멈췄습니다.")));
                return false;
            }
            consecutiveErrors = 0;

            if (!isVisible)
            {
                return false;
            }

            BeginInvoke(new Action(() =>
            {
                ShowPopup(
                    result.Keyword,
                    "1. 노필터 " + RankText(result.NoFilterRank) + "\n2. 라이브 " + RankText(result.LiveRank),
                    result.TopLiveChannelTitle.Length > 0 ? "라이브 1위 " + result.TopLiveChannelTitle : "5분마다 갱신"
                );
                SetStatus("노필터 " + RankText(result.NoFilterRank) + " · 라이브 " + RankText(result.LiveRank), false);
                refreshTimer.Start();
            }));

            if (!comparisonDone && (DateTime.UtcNow - monitoringStartedAtUtc).TotalMilliseconds >= ComparisonDelayMs)
            {
                comparisonDone = true;
                ComparisonReport report = client.FetchComparisonReport(
                    channelBox.Text.Trim(),
                    keywordBox.Text.Trim(),
                    lockedVideoId
                );
                latestComparisonReport = report.ToDisplayText();
                SaveLatestComparisonReport();
                BeginInvoke(new Action(() =>
                {
                    ShowPopup("비교 분석 준비", report.Summary, "최근 비교 분석 보기에서 확인하세요.");
                    SetStatus("비교 분석 준비 완료", false);
                }));
            }

            return true;
        }

        private void ShowPopup(string title, string rank, string sub)
        {
            if (popupHideTimer != null)
            {
                popupHideTimer.Stop();
                popupHideTimer.Dispose();
                popupHideTimer = null;
            }
            if (popup != null && !popup.IsDisposed) popup.Close();
            popup = new RankPopupForm(title, rank, sub);
            popup.Show();
            popupHideTimer = new Timer();
            popupHideTimer.Interval = 5000;
            popupHideTimer.Tick += (sender, args) =>
            {
                popupHideTimer.Stop();
                popupHideTimer.Dispose();
                popupHideTimer = null;
                if (popup != null && !popup.IsDisposed) popup.Close();
            };
            popupHideTimer.Start();
        }

        private void StopMonitoring(string message)
        {
            StopMonitoring(message, true);
        }

        private void StopMonitoring(string message, bool closePopup)
        {
            StopTimersOnly();
            if (lockedVideoId.Length > 0)
            {
                lastKnownVideoId = lockedVideoId;
                SaveLastSession();
            }
            lockedVideoId = "";
            preLiveMisses = 0;
            consecutiveErrors = 0;
            isChecking = false;
            if (monitoringActive)
            {
                lastSessionEndedAtUtc = DateTime.UtcNow;
            }
            monitoringActive = false;
            SaveLastSession();
            if (closePopup && popup != null && !popup.IsDisposed) popup.Close();
            ShowMain();
            SetStatus(message, false);
            WindowState = FormWindowState.Normal;
            Activate();
        }

        private void StopTimersOnly()
        {
            firstTimer.Stop();
            refreshTimer.Stop();
        }

        private void TryOpenObs()
        {
            string[] candidates = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "obs-studio", "bin", "64bit", "obs64.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "obs-studio", "bin", "64bit", "obs64.exe")
            };

            foreach (string path in candidates)
            {
                if (!File.Exists(path)) continue;
                ProcessStartInfo info = new ProcessStartInfo(path);
                info.WorkingDirectory = Path.GetDirectoryName(path);
                Process.Start(info);
                return;
            }
        }

        private void SetStatus(string text, bool error)
        {
            statusLabel.Text = text;
            statusLabel.ForeColor = error ? Color.FromArgb(185, 28, 28) : Color.FromArgb(4, 120, 87);
            statusLabel.Visible = text.Length > 0;
        }

        private void LoadSettings()
        {
            if (!File.Exists(settingsPath)) return;
            string[] lines = File.ReadAllLines(settingsPath, Encoding.UTF8);
            if (lines.Length > 0) apiBox.Text = lines[0];
            if (lines.Length > 1) channelBox.Text = lines[1];
            if (lines.Length > 2) keywordBox.Text = lines[2];
        }

        private void LoadLatestComparisonReport()
        {
            if (File.Exists(reportPath))
            {
                latestComparisonReport = File.ReadAllText(reportPath, Encoding.UTF8);
            }
        }

        private void SaveLatestComparisonReport()
        {
            File.WriteAllText(reportPath, latestComparisonReport == null ? "" : latestComparisonReport, Encoding.UTF8);
        }

        private void ClearLatestComparisonReport()
        {
            latestComparisonReport = "";
            if (File.Exists(reportPath)) File.Delete(reportPath);
        }

        private void LoadLastSession()
        {
            if (!File.Exists(sessionPath)) return;
            string[] lines = File.ReadAllLines(sessionPath, Encoding.UTF8);
            if (lines.Length > 0) lastKnownVideoId = lines[0].Trim();
            if (lines.Length > 1) lastKnownVideoTitle = lines[1].Trim();
            if (lines.Length > 2) DateTime.TryParse(lines[2].Trim(), null, System.Globalization.DateTimeStyles.RoundtripKind, out lastSessionStartedAtUtc);
            if (lines.Length > 3) DateTime.TryParse(lines[3].Trim(), null, System.Globalization.DateTimeStyles.RoundtripKind, out lastSessionEndedAtUtc);
        }

        private void SaveLastSession()
        {
            File.WriteAllLines(sessionPath, new string[]
            {
                lastKnownVideoId == null ? "" : lastKnownVideoId.Trim(),
                lastKnownVideoTitle == null ? "" : lastKnownVideoTitle.Trim(),
                lastSessionStartedAtUtc == DateTime.MinValue ? "" : lastSessionStartedAtUtc.ToString("o"),
                lastSessionEndedAtUtc == DateTime.MinValue ? "" : lastSessionEndedAtUtc.ToString("o")
            }, Encoding.UTF8);
        }

        private void SaveSettings()
        {
            File.WriteAllLines(settingsPath, new string[]
            {
                apiBox.Text.Trim(),
                channelBox.Text.Trim(),
                keywordBox.Text.Trim()
            }, Encoding.UTF8);
        }

        private string RankText(int rank)
        {
            return rank > 0 ? rank + "위" : "20위 밖";
        }

        private string ShortText(string text)
        {
            string clean = text == null ? "" : text.Trim();
            return clean.Length > 45 ? clean.Substring(0, 45) + "..." : clean;
        }

        private void ShowLatestComparisonReport()
        {
            if (!string.IsNullOrWhiteSpace(latestComparisonReport))
            {
                new ComparisonReportForm(latestComparisonReport).Show(this);
                return;
            }

            GenerateComparisonNow();
        }

        private bool IsComparisonReady()
        {
            DateTime start = monitoringStartedAtUtc != DateTime.MinValue ? monitoringStartedAtUtc : lastSessionStartedAtUtc;
            if (start == DateTime.MinValue) return false;

            DateTime end = monitoringActive ? DateTime.UtcNow : (lastSessionEndedAtUtc == DateTime.MinValue ? DateTime.UtcNow : lastSessionEndedAtUtc);
            return (end - start).TotalMilliseconds >= ComparisonDelayMs;
        }

        private string ComparisonWaitMessage()
        {
            DateTime start = monitoringStartedAtUtc != DateTime.MinValue ? monitoringStartedAtUtc : lastSessionStartedAtUtc;
            if (start == DateTime.MinValue)
            {
                return "아직 비교 분석이 없습니다.\r\nOBS 방송 시작 버튼을 누른 뒤 50분이 지나야 비교 분석이 생성됩니다.";
            }

            DateTime end = monitoringActive ? DateTime.UtcNow : (lastSessionEndedAtUtc == DateTime.MinValue ? DateTime.UtcNow : lastSessionEndedAtUtc);
            int elapsed = Math.Max(0, (int)Math.Floor((end - start).TotalMinutes));
            int remain = Math.Max(0, 50 - elapsed);
            return "아직 비교 분석 시간이 아닙니다.\r\n현재 경과: " + elapsed + "분\r\n남은 시간: 약 " + remain + "분\r\n\r\n순위 팝업은 5분마다 먼저 표시되고, 비교 분석은 방송 시작 후 50분부터 생성됩니다.";
        }

        private void GenerateComparisonNow()
        {
            string apiKey = apiBox.Text.Trim();
            string channel = channelBox.Text.Trim();
            string keyword = keywordBox.Text.Trim();
            if (apiKey.Length == 0 || channel.Length == 0 || keyword.Length == 0)
            {
                new ComparisonReportForm("API, 채널 주소, 키워드를 먼저 입력해 주세요.").Show(this);
                return;
            }

            if (!IsComparisonReady())
            {
                string wait = ComparisonWaitMessage();
                SetStatus("비교 분석은 방송 시작 후 50분부터 생성됩니다.", false);
                new ComparisonReportForm(wait).Show(this);
                return;
            }

            if (isGeneratingComparison)
            {
                SetStatus("비교 분석을 생성하는 중입니다.", false);
                return;
            }

            isGeneratingComparison = true;
            SetStatus("비교 분석을 생성하는 중입니다.", false);
            ShowPopup("비교 분석 생성", "진행 중", "잠시만 기다려 주세요.");

            string targetVideoId = lockedVideoId.Length > 0 ? lockedVideoId : lastKnownVideoId;
            YoutubeClient reportClient = client ?? new YoutubeClient(apiKey);

            System.Threading.ThreadPool.QueueUserWorkItem(_ =>
            {
                try
                {
                    ComparisonReport report = reportClient.FetchComparisonReport(channel, keyword, targetVideoId);
                    latestComparisonReport = report.ToDisplayText();
                    SaveLatestComparisonReport();
                    BeginInvoke(new Action(() =>
                    {
                        comparisonDone = true;
                        SetStatus("비교 분석 준비 완료", false);
                        ShowPopup("비교 분석 준비", report.Summary, "최근 비교 분석 보기에서 확인하세요.");
                        new ComparisonReportForm(latestComparisonReport).Show(this);
                    }));
                }
                catch (Exception ex)
                {
                    BeginInvoke(new Action(() =>
                    {
                        string message = "비교 분석 생성 실패: " + ShortText(ex.Message);
                        SetStatus(message, true);
                        ShowPopup("비교 분석 실패", "확인 필요", ShortText(ex.Message));
                        new ComparisonReportForm(message).Show(this);
                    }));
                }
                finally
                {
                    isGeneratingComparison = false;
                }
            });
        }
    }

    internal sealed class ComparisonReportForm : Form
    {
        public ComparisonReportForm(string report)
        {
            Text = "LiveRank 비교 분석";
            StartPosition = FormStartPosition.CenterParent;
            MinimumSize = new Size(980, 680);
            Rectangle area = Screen.PrimaryScreen.WorkingArea;
            int formWidth = Math.Min(1180, Math.Max(MinimumSize.Width, area.Width - 80));
            int formHeight = Math.Min(920, Math.Max(MinimumSize.Height, area.Height - 80));
            Size = new Size(formWidth, formHeight);
            BackColor = Color.FromArgb(248, 250, 252);

            Panel scrollPanel = new Panel();
            scrollPanel.Dock = DockStyle.Fill;
            scrollPanel.AutoScroll = true;
            scrollPanel.BackColor = Color.FromArgb(248, 250, 252);
            Controls.Add(scrollPanel);

            ComparisonDashboard dashboard = new ComparisonDashboard(report);
            dashboard.Location = new Point(0, 0);
            dashboard.Width = 1140;
            dashboard.Height = 1040;
            scrollPanel.AutoScrollMinSize = new Size(dashboard.Width, dashboard.Height + 24);
            scrollPanel.Controls.Add(dashboard);
        }
    }

    internal sealed class ComparisonDashboard : Control
    {
        private readonly ReportVisualData data;
        private readonly Font titleFont = new Font("Malgun Gothic", 24, FontStyle.Bold);
        private readonly Font headerFont = new Font("Malgun Gothic", 16, FontStyle.Bold);
        private readonly Font sectionFont = new Font("Malgun Gothic", 13, FontStyle.Bold);
        private readonly Font bodyFont = new Font("Malgun Gothic", 9, FontStyle.Regular);
        private readonly Font bodyBoldFont = new Font("Malgun Gothic", 9, FontStyle.Bold);
        private readonly Font scoreFont = new Font("Malgun Gothic", 34, FontStyle.Bold);
        private readonly Font smallFont = new Font("Malgun Gothic", 8, FontStyle.Regular);

        private readonly Color ink = Color.FromArgb(15, 23, 42);
        private readonly Color muted = Color.FromArgb(71, 85, 105);
        private readonly Color border = Color.FromArgb(226, 232, 240);
        private readonly Color ownColor = Color.FromArgb(236, 72, 153);
        private readonly Color competitorColor = Color.FromArgb(16, 185, 129);

        public ComparisonDashboard(string report)
        {
            DoubleBuffered = true;
            BackColor = Color.FromArgb(248, 250, 252);
            data = ReportVisualData.Parse(report);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(BackColor);

            DrawHeader(g);
            if (data.Categories.Count == 0)
            {
                DrawEmptyReport(g);
                return;
            }

            DrawScoreSummary(g);
            DrawCategoryComparison(g);
            DrawRadar(g);
            DrawCoreDiagnosis(g);
            DrawPriorities(g);
            DrawReactionQuality(g);
            DrawNotice(g);
        }

        private void DrawHeader(Graphics g)
        {
            DrawString(g, "유튜브 라이브 경쟁력 진단", titleFont, ink, new RectangleF(32, 22, 520, 40));
            DrawString(g, Blank(data.ComparisonTitle, "내 채널 vs 노필터 라이브 1위"), headerFont, muted, new RectangleF(34, 62, 520, 28));
            DrawBadge(g, new Rectangle(892, 28, 210, 38), "키워드: " + Blank(data.Keyword, "확인 불가"), Color.White, border, ink);
        }

        private void DrawEmptyReport(Graphics g)
        {
            Rectangle card = new Rectangle(32, 110, 1068, 330);
            DrawCard(g, card, Color.White);
            DrawString(g, "비교 대상 없음", headerFont, ink, new RectangleF(card.X + 28, card.Y + 26, card.Width - 56, 34));
            DrawMultiline(g, data.RawText, bodyFont, muted, new RectangleF(card.X + 28, card.Y + 76, card.Width - 56, card.Height - 104));
        }

        private void DrawScoreSummary(Graphics g)
        {
            Rectangle card = new Rectangle(32, 104, 1068, 160);
            DrawCard(g, card, Color.White);

            Rectangle left = new Rectangle(card.X + 22, card.Y + 20, 310, 100);
            Rectangle center = new Rectangle(card.X + 374, card.Y + 20, 320, 100);
            Rectangle right = new Rectangle(card.Right - 332, card.Y + 20, 310, 100);

            using (SolidBrush ownBack = new SolidBrush(Color.FromArgb(253, 242, 248)))
            using (SolidBrush compBack = new SolidBrush(Color.FromArgb(240, 253, 244)))
            {
                g.FillRectangle(ownBack, left);
                g.FillRectangle(compBack, right);
            }

            DrawString(g, "내 채널", sectionFont, ownColor, new RectangleF(left.X, left.Y + 8, left.Width, 24), Center());
            DrawString(g, data.OwnScore.ToString(), scoreFont, ownColor, new RectangleF(left.X, left.Y + 28, left.Width, 48), Center());
            DrawGrade(g, data.OwnGrade, new Rectangle(left.X + 210, left.Y + 38, 44, 36), ownColor);
            DrawString(g, Blank(data.OwnChannel, "내 채널"), bodyBoldFont, ink, new RectangleF(left.X, left.Y + 76, left.Width, 22), Center());

            DrawString(g, "격차", sectionFont, ink, new RectangleF(center.X, center.Y + 10, center.Width, 26), Center());
            DrawString(g, (data.Gap > 0 ? "-" + data.Gap : "+" + Math.Abs(data.Gap)) + "점", scoreFont, data.Gap > 0 ? Color.FromArgb(220, 38, 38) : competitorColor, new RectangleF(center.X, center.Y + 34, center.Width, 50), Center());
            DrawString(g, data.Gap > 0 ? "현재 " + data.CompetitorShortLabel + " 대비 부족" : "현재 내 채널 우세", bodyFont, muted, new RectangleF(center.X, center.Y + 82, center.Width, 24), Center());

            DrawString(g, data.CompetitorLabel, sectionFont, competitorColor, new RectangleF(right.X, right.Y + 8, right.Width, 24), Center());
            DrawString(g, data.CompetitorScore.ToString(), scoreFont, competitorColor, new RectangleF(right.X, right.Y + 28, right.Width, 48), Center());
            DrawGrade(g, data.CompetitorGrade, new Rectangle(right.X + 210, right.Y + 38, 44, 36), competitorColor);
            DrawString(g, Blank(data.CompetitorChannel, "비교 대상 없음"), bodyBoldFont, ink, new RectangleF(right.X, right.Y + 76, right.Width, 22), Center());

            int infoY = card.Bottom - 34;
            DrawString(g, "표시 시점: 방송 후/수동 분석", smallFont, muted, new RectangleF(card.X + 35, infoY, 250, 22));
            DrawString(g, "비교 기준: " + data.CompetitorLabel, smallFont, muted, new RectangleF(card.X + 382, infoY, 330, 22), Center());
            DrawString(g, DateTime.Now.ToString("yyyy-MM-dd HH:mm"), smallFont, muted, new RectangleF(card.Right - 240, infoY, 210, 22), Far());
        }

        private void DrawCategoryComparison(Graphics g)
        {
            DrawString(g, "3대 카테고리 점수 비교", sectionFont, ink, new RectangleF(32, 286, 360, 26));
            DrawString(g, "총점 100점 = 트래픽 60 + 채널 30 + 기본 10", smallFont, muted, new RectangleF(372, 290, 360, 18), Far());
            Rectangle card = new Rectangle(32, 318, 700, 410);
            DrawCard(g, card, Color.White);

            int y = card.Y + 16;
            for (int i = 0; i < data.Categories.Count; i += 1)
            {
                CategoryVisual row = data.Categories[i];
                Rectangle rowRect = new Rectangle(card.X + 14, y, card.Width - 28, 52);
                if (i > 0) g.DrawLine(new Pen(border), rowRect.X, rowRect.Y - 8, rowRect.Right, rowRect.Y - 8);
                DrawString(g, (i + 1) + ". " + row.Name, bodyBoldFont, ink, new RectangleF(rowRect.X, rowRect.Y, 180, 20));
                DrawString(g, "내 채널", smallFont, muted, new RectangleF(rowRect.X + 190, rowRect.Y, 60, 16));
                DrawBar(g, new Rectangle(rowRect.X + 250, rowRect.Y + 3, 230, 9), row.OwnScore, ownColor);
                DrawString(g, row.OwnScore + "점", bodyBoldFont, ink, new RectangleF(rowRect.X + 492, rowRect.Y - 2, 45, 18), Far());

                DrawString(g, data.CompetitorShortLabel, smallFont, muted, new RectangleF(rowRect.X + 190, rowRect.Y + 24, 60, 16));
                DrawBar(g, new Rectangle(rowRect.X + 250, rowRect.Y + 27, 230, 9), row.CompetitorScore, competitorColor);
                DrawString(g, row.CompetitorScore + "점", bodyBoldFont, ink, new RectangleF(rowRect.X + 492, rowRect.Y + 22, 45, 18), Far());
                DrawStatusBadge(g, new Rectangle(rowRect.Right - 104, rowRect.Y + 10, 92, 28), row.Status);
                y += 56;
            }
        }

        private void DrawRadar(Graphics g)
        {
            DrawString(g, "한눈에 보는 경쟁력 구조", sectionFont, ink, new RectangleF(762, 286, 320, 26), Center());
            Rectangle card = new Rectangle(760, 318, 340, 300);
            DrawCard(g, card, Color.White);

            PointF center = new PointF(card.X + card.Width / 2f, card.Y + 152);
            float radius = 92f;
            int count = Math.Min(data.Categories.Count, 7);
            if (count == 0) return;

            using (Pen gridPen = new Pen(Color.FromArgb(226, 232, 240)))
            using (Pen ownPen = new Pen(ownColor, 2))
            using (Pen compPen = new Pen(competitorColor, 2))
            using (SolidBrush ownFill = new SolidBrush(Color.FromArgb(45, ownColor)))
            using (SolidBrush compFill = new SolidBrush(Color.FromArgb(35, competitorColor)))
            {
                for (int step = 1; step <= 4; step += 1)
                {
                    PointF[] grid = RadarPoints(count, center, radius * step / 4f, null, true);
                    g.DrawPolygon(gridPen, grid);
                }

                for (int i = 0; i < count; i += 1)
                {
                    double angle = -Math.PI / 2 + (Math.PI * 2 * i / count);
                    PointF end = new PointF(center.X + (float)Math.Cos(angle) * radius, center.Y + (float)Math.Sin(angle) * radius);
                    g.DrawLine(gridPen, center, end);
                    string label = ShortLabel(data.Categories[i].Name);
                    PointF labelPoint = new PointF(center.X + (float)Math.Cos(angle) * (radius + 26), center.Y + (float)Math.Sin(angle) * (radius + 26));
                    DrawString(g, label, smallFont, muted, new RectangleF(labelPoint.X - 34, labelPoint.Y - 9, 68, 18), Center());
                }

                PointF[] own = RadarPoints(count, center, radius, data.Categories, false);
                PointF[] comp = RadarPoints(count, center, radius, data.Categories, false, true);
                g.FillPolygon(compFill, comp);
                g.FillPolygon(ownFill, own);
                g.DrawPolygon(compPen, comp);
                g.DrawPolygon(ownPen, own);
            }

            DrawLegend(g, card.X + 92, card.Bottom - 26, ownColor, "내 채널");
            DrawLegend(g, card.X + 190, card.Bottom - 26, competitorColor, data.CompetitorShortLabel);
        }

        private void DrawCoreDiagnosis(Graphics g)
        {
            Rectangle card = new Rectangle(760, 638, 340, 150);
            DrawCard(g, card, Color.FromArgb(239, 246, 255));
            DrawString(g, "핵심 진단", sectionFont, Color.FromArgb(29, 78, 216), new RectangleF(card.X + 20, card.Y + 16, card.Width - 40, 24));

            string text = data.CoreDiagnosis;
            if (string.IsNullOrWhiteSpace(text))
            {
                text = data.Gap > 0
                    ? data.CompetitorShortLabel + "와의 격차가 큰 항목부터 고치면 다음 방송의 진입 가능성을 높일 수 있습니다."
                    : "현재 강점은 유지하고 낮은 항목만 보강하세요.";
            }
            bool hasInterpretationNote = !string.IsNullOrWhiteSpace(data.InterpretationNote);
            RectangleF bodyRect = hasInterpretationNote
                ? new RectangleF(card.X + 20, card.Y + 48, card.Width - 40, card.Height - 104)
                : new RectangleF(card.X + 20, card.Y + 48, card.Width - 40, card.Height - 62);
            DrawMultiline(g, text, bodyFont, ink, bodyRect);
            if (hasInterpretationNote)
            {
                DrawMultiline(g, data.InterpretationNote, smallFont, Color.FromArgb(30, 64, 175), new RectangleF(card.X + 20, card.Bottom - 50, card.Width - 40, 42));
            }
        }

        private void DrawPriorities(Graphics g)
        {
            DrawString(g, "우선 개선 우선순위", sectionFont, ink, new RectangleF(32, 748, 360, 26));
            int x = 32;
            int y = 782;
            int w = 330;
            for (int i = 0; i < 3; i += 1)
            {
                string text = i < data.Priorities.Count ? data.Priorities[i] : "다음 방송에서 제목/설명/초반 반응을 점검하세요.";
                Rectangle card = new Rectangle(x + i * (w + 18), y, w, 82);
                Color accent = i == 0 ? ownColor : i == 1 ? Color.FromArgb(249, 115, 22) : Color.FromArgb(234, 179, 8);
                DrawCard(g, card, Color.White);
                using (SolidBrush brush = new SolidBrush(accent))
                {
                    g.FillEllipse(brush, card.X + 16, card.Y + 18, 34, 34);
                }
                DrawString(g, (i + 1).ToString(), sectionFont, Color.White, new RectangleF(card.X + 16, card.Y + 22, 34, 28), Center());
                DrawMultiline(g, text, bodyFont, ink, new RectangleF(card.X + 62, card.Y + 16, card.Width - 78, card.Height - 28));
            }
        }

        private void DrawReactionQuality(Graphics g)
        {
            Rectangle card = new Rectangle(32, 878, 1068, 72);
            DrawCard(g, card, Color.FromArgb(255, 251, 235));
            DrawString(g, "반응 품질 (참고)", bodyBoldFont, ink, new RectangleF(card.X + 18, card.Y + 12, 150, 20));
            DrawBadge(g, new Rectangle(card.X + 168, card.Y + 9, 98, 26), "총점 미반영", Color.FromArgb(254, 243, 199), Color.FromArgb(253, 230, 138), Color.FromArgb(146, 64, 14));
            string chat = data.ReactionQuality.Count > 0 ? data.ReactionQuality[0] : "채팅 참여율: 라이브 채팅 데이터 연결 전 단계";
            string like = data.ReactionQuality.Count > 1 ? data.ReactionQuality[1] : "좋아요 반응: 확인 전";
            DrawString(g, MarkReferenceOnly(chat), bodyFont, muted, new RectangleF(card.X + 288, card.Y + 12, card.Width - 310, 20));
            DrawString(g, MarkReferenceOnly(like), bodyFont, muted, new RectangleF(card.X + 288, card.Y + 40, card.Width - 310, 18));
        }

        private void DrawNotice(Graphics g)
        {
            Rectangle notice = new Rectangle(32, 968, 1068, 54);
            DrawCard(g, notice, Color.FromArgb(239, 246, 255));
            DrawString(g, "참고사항", bodyBoldFont, Color.FromArgb(29, 78, 216), new RectangleF(notice.X + 18, notice.Y + 10, 110, 20));
            DrawString(g, "LiveRank는 공개 데이터와 일반 진단 기준으로 비교 분석을 제공합니다. 1등 진입이나 조회수 상승을 보장하지 않습니다.", bodyFont, muted, new RectangleF(notice.X + 128, notice.Y + 10, notice.Width - 148, 22));
            DrawString(g, "본 리포트는 의사결정 참고 자료로 활용해 주세요.", bodyFont, muted, new RectangleF(notice.X + 128, notice.Y + 30, notice.Width - 148, 18));
        }

        private PointF[] RadarPoints(int count, PointF center, float radius, List<CategoryVisual> categories, bool full)
        {
            return RadarPoints(count, center, radius, categories, full, false);
        }

        private PointF[] RadarPoints(int count, PointF center, float radius, List<CategoryVisual> categories, bool full, bool competitor)
        {
            PointF[] points = new PointF[count];
            for (int i = 0; i < count; i += 1)
            {
                float value = full || categories == null ? 100f : (competitor ? categories[i].CompetitorScore : categories[i].OwnScore);
                float r = radius * Math.Max(0, Math.Min(100, value)) / 100f;
                double angle = -Math.PI / 2 + (Math.PI * 2 * i / count);
                points[i] = new PointF(center.X + (float)Math.Cos(angle) * r, center.Y + (float)Math.Sin(angle) * r);
            }
            return points;
        }

        private void DrawBar(Graphics g, Rectangle rect, int score, Color color)
        {
            using (SolidBrush back = new SolidBrush(Color.FromArgb(241, 245, 249)))
            using (SolidBrush fill = new SolidBrush(color))
            {
                g.FillRectangle(back, rect);
                int width = (int)Math.Round(rect.Width * Math.Max(0, Math.Min(100, score)) / 100.0);
                g.FillRectangle(fill, new Rectangle(rect.X, rect.Y, width, rect.Height));
            }
        }

        private void DrawCard(Graphics g, Rectangle rect, Color fill)
        {
            using (GraphicsPath path = RoundedRect(rect, 10))
            using (SolidBrush brush = new SolidBrush(fill))
            using (Pen pen = new Pen(border))
            {
                g.FillPath(brush, path);
                g.DrawPath(pen, path);
            }
        }

        private void DrawBadge(Graphics g, Rectangle rect, string text, Color fill, Color line, Color textColor)
        {
            using (GraphicsPath path = RoundedRect(rect, 8))
            using (SolidBrush brush = new SolidBrush(fill))
            using (Pen pen = new Pen(line))
            {
                g.FillPath(brush, path);
                g.DrawPath(pen, path);
            }
            DrawString(g, text, bodyBoldFont, textColor, new RectangleF(rect.X + 12, rect.Y + 9, rect.Width - 24, rect.Height - 16), Center());
        }

        private void DrawStatusBadge(Graphics g, Rectangle rect, string status)
        {
            Color fill = Color.FromArgb(240, 253, 244);
            Color line = Color.FromArgb(187, 247, 208);
            Color text = Color.FromArgb(22, 101, 52);
            if (status.Contains("핵심"))
            {
                fill = Color.FromArgb(254, 242, 242);
                line = Color.FromArgb(254, 202, 202);
                text = Color.FromArgb(185, 28, 28);
            }
            else if (status.Contains("개선"))
            {
                fill = Color.FromArgb(255, 251, 235);
                line = Color.FromArgb(253, 230, 138);
                text = Color.FromArgb(180, 83, 9);
            }
            else if (status.Contains("강점"))
            {
                fill = Color.FromArgb(240, 253, 244);
                line = Color.FromArgb(187, 247, 208);
                text = Color.FromArgb(21, 128, 61);
            }
            DrawBadge(g, rect, status, fill, line, text);
        }

        private void DrawGrade(Graphics g, string grade, Rectangle rect, Color color)
        {
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(35, color)))
            {
                g.FillEllipse(brush, rect);
            }
            DrawString(g, string.IsNullOrWhiteSpace(grade) ? "-" : grade, headerFont, color, new RectangleF(rect.X, rect.Y + 4, rect.Width, rect.Height - 6), Center());
        }

        private void DrawLegend(Graphics g, int x, int y, Color color, string label)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillRectangle(brush, x, y + 4, 14, 8);
            }
            DrawString(g, label, smallFont, muted, new RectangleF(x + 20, y, 80, 18));
        }

        private GraphicsPath RoundedRect(Rectangle bounds, int radius)
        {
            int diameter = radius * 2;
            GraphicsPath path = new GraphicsPath();
            path.AddArc(bounds.X, bounds.Y, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Y, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.X, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }

        private void DrawMultiline(Graphics g, string text, Font font, Color color, RectangleF rect)
        {
            using (SolidBrush brush = new SolidBrush(color))
            using (StringFormat format = new StringFormat())
            {
                format.Trimming = StringTrimming.EllipsisWord;
                format.FormatFlags = StringFormatFlags.LineLimit;
                g.DrawString(text ?? "", font, brush, rect, format);
            }
        }

        private void DrawString(Graphics g, string text, Font font, Color color, RectangleF rect)
        {
            DrawString(g, text, font, color, rect, Near());
        }

        private void DrawString(Graphics g, string text, Font font, Color color, RectangleF rect, StringFormat format)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.DrawString(text ?? "", font, brush, rect, format);
            }
        }

        private StringFormat Near()
        {
            return new StringFormat { Alignment = StringAlignment.Near, LineAlignment = StringAlignment.Near, Trimming = StringTrimming.EllipsisCharacter };
        }

        private StringFormat Center()
        {
            return new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center, Trimming = StringTrimming.EllipsisCharacter };
        }

        private StringFormat Far()
        {
            return new StringFormat { Alignment = StringAlignment.Far, LineAlignment = StringAlignment.Near, Trimming = StringTrimming.EllipsisCharacter };
        }

        private string ShortLabel(string label)
        {
            if (label.Contains("트래픽")) return "트래픽";
            if (label.Contains("채널")) return "채널";
            if (label.Contains("기본")) return "최적화";
            return "반응";
        }

        private string MarkReferenceOnly(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return "확인 전 · 총점 미반영";
            return text.Contains("총점 미반영") ? text : text + " · 총점 미반영";
        }

        private string Blank(string value, string fallback)
        {
            return string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();
        }
    }

    internal sealed class ReportVisualData
    {
        public string RawText = "";
        public string Keyword = "";
        public string OwnChannel = "";
        public string CompetitorChannel = "";
        public string ComparisonTitle = "";
        public string CompetitorLabel = "1위 채널";
        public string CompetitorShortLabel = "1위";
        public int OwnScore;
        public int CompetitorScore;
        public string OwnGrade = "";
        public string CompetitorGrade = "";
        public int Gap;
        public string CoreDiagnosis = "";
        public string InterpretationNote = "";
        public readonly List<CategoryVisual> Categories = new List<CategoryVisual>();
        public readonly List<string> Priorities = new List<string>();
        public readonly List<string> ReactionQuality = new List<string>();

        public static ReportVisualData Parse(string report)
        {
            ReportVisualData data = new ReportVisualData();
            data.RawText = report ?? "";
            string[] lines = data.RawText.Replace("\r\n", "\n").Split('\n');
            CategoryVisual current = null;
            bool inCore = false;
            bool inPriorities = false;
            bool inReactionQuality = false;

            foreach (string rawLine in lines)
            {
                string line = rawLine.Trim();
                if (line.Length == 0)
                {
                    if (inCore) inCore = false;
                    if (inReactionQuality) inReactionQuality = false;
                    continue;
                }

                if (line.StartsWith("키워드:")) data.Keyword = AfterColon(line);
                else if (line.StartsWith("내 채널") && line.Contains("노필터 라이브")) data.ComparisonTitle = line;
                else if (line.StartsWith("내 채널:") && data.OwnChannel.Length == 0) data.OwnChannel = AfterColon(line);
                else if (line.StartsWith("비교 대상:"))
                {
                    data.CompetitorLabel = AfterColon(line);
                    data.CompetitorShortLabel = ShortRankLabel(data.CompetitorLabel);
                }
                else if (line.StartsWith("노필터 라이브") && line.Contains(":")) data.CompetitorChannel = AfterColon(line);
                else if (line.StartsWith("- 내 채널:")) ParseScoreLine(line, true, data);
                else if (line.StartsWith("- 노필터 라이브") && line.Contains("점(")) ParseScoreLine(line, false, data);
                else if (line.StartsWith("- 1위 채널:")) ParseScoreLine(line, false, data);
                else if (line == "핵심 진단")
                {
                    inCore = true;
                    inPriorities = false;
                }
                else if (line == "항목별 진단")
                {
                    inCore = false;
                    inPriorities = false;
                    inReactionQuality = false;
                }
                else if (line == "오늘 바로 수정할 순서")
                {
                    inPriorities = true;
                    inCore = false;
                    inReactionQuality = false;
                }
                else if (line == "반응 품질 참고")
                {
                    inReactionQuality = true;
                    inPriorities = false;
                    inCore = false;
                    current = null;
                }
                else if (line == "최종 해석")
                {
                    inPriorities = false;
                    inCore = false;
                    inReactionQuality = false;
                }
                else
                {
                    if (inReactionQuality && line.StartsWith("-"))
                    {
                        data.ReactionQuality.Add(line.TrimStart('-', ' '));
                        continue;
                    }

                    Match category = Regex.Match(line, @"^\d+\.\s*(.+?):\s*내\s*(\d+)점\s*/\s*(.+?)\s*(\d+)점\s*/\s*(.+)$");
                    if (category.Success)
                    {
                        current = new CategoryVisual();
                        current.Name = category.Groups[1].Value.Trim();
                        current.OwnScore = IntValue(category.Groups[2].Value);
                        string categoryRankLabel = category.Groups[3].Value.Trim();
                        if (categoryRankLabel.Length > 0 && categoryRankLabel != "1위")
                        {
                            data.CompetitorShortLabel = categoryRankLabel;
                            data.CompetitorLabel = "노필터 라이브 " + categoryRankLabel;
                        }
                        current.CompetitorScore = IntValue(category.Groups[4].Value);
                        current.GapText = category.Groups[5].Value.Trim();
                        current.Status = StatusFromScores(current.OwnScore, current.CompetitorScore);
                        data.Categories.Add(current);
                        continue;
                    }

                    if (current != null && line.StartsWith("판정:"))
                    {
                        current.Status = AfterColon(line);
                        continue;
                    }

                    if (current != null && line.StartsWith("근거:"))
                    {
                        current.Evidence = AfterColon(line);
                        continue;
                    }

                    if (inCore && line.StartsWith("-"))
                    {
                        string coreLine = line.TrimStart('-', ' ');
                        if (coreLine.StartsWith("순위 해석:"))
                        {
                            data.InterpretationNote = coreLine;
                        }
                        else
                        {
                            if (data.CoreDiagnosis.Length > 0) data.CoreDiagnosis += "\n";
                            data.CoreDiagnosis += coreLine;
                        }
                        continue;
                    }

                    if (inPriorities && Regex.IsMatch(line, @"^\d+\."))
                    {
                        data.Priorities.Add(line);
                    }
                }
            }

            data.Gap = data.CompetitorScore - data.OwnScore;
            if (data.ComparisonTitle.Length == 0)
            {
                data.ComparisonTitle = "내 채널 vs " + data.CompetitorLabel;
            }
            return data;
        }

        private static string ShortRankLabel(string label)
        {
            Match match = Regex.Match(label ?? "", @"(\d+)위");
            return match.Success ? match.Groups[1].Value + "위" : "비교 대상";
        }

        private static void ParseScoreLine(string line, bool own, ReportVisualData data)
        {
            Match match = Regex.Match(line, @"(\d+)점\(([^)]+)\)");
            if (!match.Success) return;
            if (own)
            {
                data.OwnScore = IntValue(match.Groups[1].Value);
                data.OwnGrade = match.Groups[2].Value.Trim();
            }
            else
            {
                data.CompetitorScore = IntValue(match.Groups[1].Value);
                data.CompetitorGrade = match.Groups[2].Value.Trim();
            }
        }

        private static string AfterColon(string line)
        {
            int index = line.IndexOf(':');
            return index >= 0 ? line.Substring(index + 1).Trim() : "";
        }

        private static int IntValue(string value)
        {
            int parsed;
            return int.TryParse(value, out parsed) ? parsed : 0;
        }

        private static string StatusFromScores(int own, int competitor)
        {
            int gap = competitor - own;
            if (gap >= 20) return "핵심 약점";
            if (gap >= 6) return "개선 권장";
            if (gap <= -6) return "내 강점";
            return "동등";
        }
    }

    internal sealed class CategoryVisual
    {
        public string Name = "";
        public int OwnScore;
        public int CompetitorScore;
        public string GapText = "";
        public string Status = "";
        public string Evidence = "";
    }

    internal sealed class RankPopupForm : Form
    {
        private const uint WdaMonitor = 0x00000001;
        private const uint WdaExcludeFromCapture = 0x00000011;

        [DllImport("user32.dll")]
        private static extern bool SetWindowDisplayAffinity(IntPtr hWnd, uint affinity);

        public RankPopupForm(string title, string rank, string sub)
        {
            Text = "라이브 도우미 순위";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.Manual;
            ShowInTaskbar = false;
            TopMost = true;
            BackColor = Color.FromArgb(255, 229, 0);
            Size = new Size(500, 180);

            Rectangle area = Screen.PrimaryScreen.WorkingArea;
            Location = new Point(area.Right - Width - 28, area.Top + 48);

            Label titleLabel = new Label();
            titleLabel.Text = string.IsNullOrWhiteSpace(title) ? "라이브 순위" : title;
            titleLabel.AutoEllipsis = true;
            titleLabel.Font = new Font("Malgun Gothic", 11, FontStyle.Bold);
            titleLabel.ForeColor = Color.FromArgb(17, 24, 39);
            titleLabel.SetBounds(22, 14, 456, 24);
            Controls.Add(titleLabel);

            Label rankLabel = new Label();
            rankLabel.Text = rank;
            rankLabel.TextAlign = ContentAlignment.MiddleLeft;
            rankLabel.Font = new Font("Malgun Gothic", 18, FontStyle.Bold);
            rankLabel.ForeColor = Color.FromArgb(17, 24, 39);
            rankLabel.SetBounds(22, 44, 456, 86);
            Controls.Add(rankLabel);

            Label subLabel = new Label();
            subLabel.Text = sub;
            subLabel.AutoEllipsis = true;
            subLabel.Font = new Font("Malgun Gothic", 8, FontStyle.Regular);
            subLabel.ForeColor = Color.FromArgb(51, 65, 85);
            subLabel.SetBounds(24, 140, 452, 24);
            Controls.Add(subLabel);
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            if (!SetWindowDisplayAffinity(Handle, WdaExcludeFromCapture))
            {
                SetWindowDisplayAffinity(Handle, WdaMonitor);
            }
        }
    }

}
