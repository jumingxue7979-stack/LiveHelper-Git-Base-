using System;
using System.Text;
using System.Text.RegularExpressions;

namespace LiveHelperWindowsObsRank
{
    internal sealed class ComparisonReport
    {
        public string Keyword = "";
        public string OwnChannelTitle = "";
        public string CompetitorChannelTitle = "";
        public string OwnTitle = "";
        public string CompetitorTitle = "";
        public int OwnNoFilterRank = -1;
        public int CompetitorNoFilterRank = -1;
        public string ComparisonTitle = "내 채널 vs 노필터 라이브 1위";
        public string CompetitorRankLabel = "노필터 라이브 1위";
        public string CompetitorShortLabel = "1위";
        public int OwnScore;
        public int CompetitorScore;
        public string OwnGrade = "F";
        public string CompetitorGrade = "F";
        public string Summary = "";
        public string Priorities = "";
        public string TitleSuggestion = "";
        public string Detail = "";

        public string ToDisplayText()
        {
            if (!string.IsNullOrWhiteSpace(Detail)) return Detail;
            StringBuilder builder = new StringBuilder();
            builder.AppendLine("LiveRank 비교 분석");
            builder.AppendLine("키워드: " + Keyword);
            builder.AppendLine("내 채널: " + OwnChannelTitle);
            builder.AppendLine("내 노필터 순위: " + RankValueText(OwnNoFilterRank));
            builder.AppendLine("비교 대상: " + (CompetitorChannelTitle.Length == 0 ? "비교 대상 없음" : CompetitorRankLabel));
            builder.AppendLine(CompetitorRankLabel + ": " + (CompetitorChannelTitle.Length == 0 ? "비교 대상 없음" : CompetitorChannelTitle));
            builder.AppendLine();
            builder.AppendLine(Summary);
            builder.AppendLine();
            builder.AppendLine("핵심 개선 우선순위");
            builder.AppendLine(Priorities);
            builder.AppendLine();
            builder.AppendLine("제목 개선 예시");
            builder.AppendLine(TitleSuggestion);
            builder.AppendLine();
            builder.AppendLine("참고: 공개 데이터 기준 비교이며 1등 노출이나 조회수 상승을 보장하지 않습니다.");
            return builder.ToString();
        }

        private static string RankValueText(int rank)
        {
            return rank > 0 ? rank + "위" : "확인 불가";
        }
    }

    internal sealed class VideoInfo
    {
        public string VideoId = "";
        public string Title = "";
        public string Description = "";
        public string ChannelId = "";
        public string ChannelTitle = "";
        public string Thumbnail = "";
        public int TagCount;
        public long? ViewCount;
        public long? LikeCount;
        public long? CommentCount;
        public long? CurrentViewers;
    }

    internal sealed class ChannelStats
    {
        public string ChannelId = "";
        public string ChannelTitle = "";
        public long? SubscriberCount;
    }

    internal static class ComparisonAnalyzer
    {
        private const string InterpretationCaseA = "현재 내 채널은 노필터 라이브 1위입니다. 다만 채널/콘텐츠 진단 점수는 2위 채널이 더 높습니다. 따라서 현재 1위는 제목·메타 우위보다 실시간 유입 품질과 반응 신호 영향으로 해석됩니다.";
        private const string InterpretationCaseB = "진단 점수는 내 채널이 우위지만, 실제 노필터 순위는 낮습니다. 이 경우 콘텐츠 점수보다 실시간 유입 품질, 시청 유지, 반응 신호에서 차이가 난 것으로 해석됩니다.";
        private const string InterpretationDefault = "순위 차이의 핵심 원인은 콘텐츠 점수보다 실시간 유입 품질과 반응 신호 차이로 보입니다.";

        public static ComparisonReport Empty(string keyword, string ownChannelTitle, string summary, int ownNoFilterRank)
        {
            ComparisonReport report = new ComparisonReport();
            report.Keyword = keyword == null ? "" : keyword.Trim();
            report.OwnChannelTitle = ownChannelTitle ?? "";
            report.OwnNoFilterRank = ownNoFilterRank;
            report.ComparisonTitle = ownNoFilterRank == 1
                ? "내 채널(노필터 1위) - 비교 대상 없음"
                : "LiveRank 비교 분석";
            report.CompetitorRankLabel = "비교 대상 없음";
            report.CompetitorShortLabel = "비교 대상";
            report.Summary = summary;
            report.Priorities = "비교 대상이 없어 점수 격차와 개선 우선순위는 산정하지 않았습니다.";
            report.TitleSuggestion = TitleSuggestion(report.Keyword);
            report.Detail = EmptyDetail(report, summary);
            return report;
        }

        public static ComparisonReport Build(string keyword, string ownChannelTitle, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats, int ownNoFilterRank, int competitorNoFilterRank)
        {
            ComparisonReport report = new ComparisonReport();
            report.Keyword = keyword == null ? "" : keyword.Trim();
            report.OwnChannelTitle = ownChannelTitle ?? "";
            report.OwnNoFilterRank = ownNoFilterRank;
            report.CompetitorNoFilterRank = competitorNoFilterRank;
            report.CompetitorRankLabel = RankLabel(competitorNoFilterRank);
            report.CompetitorShortLabel = ShortRankLabel(competitorNoFilterRank);
            report.ComparisonTitle = ComparisonTitle(ownNoFilterRank, report.CompetitorRankLabel);

            int ownTitle = TitleScore(ownVideo.Title, report.Keyword);
            int competitorTitle = TitleScore(competitorVideo.Title, report.Keyword);
            int ownThumb = ThumbnailScore(ownVideo.Thumbnail);
            int competitorThumb = ThumbnailScore(competitorVideo.Thumbnail);
            int ownMeta = MetadataScore(ownVideo.Description, ownVideo.TagCount, report.Keyword);
            int competitorMeta = MetadataScore(competitorVideo.Description, competitorVideo.TagCount, report.Keyword);
            int[] currentPair = RelativePair(ownVideo.CurrentViewers ?? ownVideo.ViewCount, competitorVideo.CurrentViewers ?? competitorVideo.ViewCount);
            int[] subscriberPair = RelativePair(ownStats.SubscriberCount, competitorStats.SubscriberCount);
            int ownAuthority = Average(new int[] { subscriberPair[0], CcvRateScore(ownVideo.CurrentViewers, ownStats.SubscriberCount) });
            int competitorAuthority = Average(new int[] { subscriberPair[1], CcvRateScore(competitorVideo.CurrentViewers, competitorStats.SubscriberCount) });
            int ownEngagement = EngagementScore(ownVideo);
            int competitorEngagement = EngagementScore(competitorVideo);
            int ownPublishing = 50;
            int competitorPublishing = 50;
            string[] labels = new string[] {
                "제목 최적화", "썸네일 기본 평가", "메타데이터", "라이브 현재 성과", "채널 신뢰도", "시청자 반응", "발행 전략"
            };
            int[] ownScores = new int[] {
                ownTitle, ownThumb, ownMeta, currentPair[0], ownAuthority, ownEngagement, ownPublishing
            };
            int[] competitorScores = new int[] {
                competitorTitle, competitorThumb, competitorMeta, currentPair[1], competitorAuthority, competitorEngagement, competitorPublishing
            };
            int[] gaps = new int[] {
                competitorTitle - ownTitle,
                competitorThumb - ownThumb,
                competitorMeta - ownMeta,
                currentPair[1] - currentPair[0],
                competitorAuthority - ownAuthority,
                competitorEngagement - ownEngagement,
                competitorPublishing - ownPublishing
            };
            int[] weights = new int[] { 15, 10, 10, 25, 15, 15, 10 };

            report.OwnScore = WeightedTotal(new int[] { ownTitle, ownThumb, ownMeta, currentPair[0], ownAuthority, ownEngagement, ownPublishing });
            report.CompetitorScore = WeightedTotal(new int[] { competitorTitle, competitorThumb, competitorMeta, currentPair[1], competitorAuthority, competitorEngagement, competitorPublishing });
            report.OwnGrade = Grade(report.OwnScore);
            report.CompetitorGrade = Grade(report.CompetitorScore);
            report.CompetitorChannelTitle = competitorVideo.ChannelTitle;
            report.CompetitorTitle = competitorVideo.Title;
            report.OwnTitle = ownVideo.Title;
            report.TitleSuggestion = TitleSuggestion(report.Keyword);
            report.Summary = "내 채널 " + report.OwnScore + "점(" + report.OwnGrade + ") / " + report.CompetitorRankLabel + " "
                + report.CompetitorScore + "점(" + report.CompetitorGrade + ")";
            report.Priorities = PriorityText(labels, gaps, weights, report.Keyword, report.CompetitorShortLabel);
            report.Detail = DetailReport(report, ownVideo, competitorVideo, ownStats, competitorStats, labels, ownScores, competitorScores, gaps, weights);
            return report;
        }

        private static string RankLabel(int rank)
        {
            return rank > 0 ? "노필터 라이브 " + rank + "위" : "노필터 라이브 비교 대상";
        }

        private static string ShortRankLabel(int rank)
        {
            return rank > 0 ? rank + "위" : "비교 대상";
        }

        private static string RankValueText(int rank)
        {
            return rank > 0 ? rank + "위" : "확인 불가";
        }

        private static string ComparisonTitle(int ownRank, string competitorLabel)
        {
            if (ownRank > 0)
            {
                return "내 채널(노필터 " + ownRank + "위) vs " + competitorLabel;
            }
            return "내 채널 vs " + competitorLabel;
        }

        private static int TitleScore(string title, string keyword)
        {
            string cleanTitle = title == null ? "" : title;
            string lowerTitle = cleanTitle.ToLowerInvariant();
            string lowerKeyword = keyword == null ? "" : keyword.Trim().ToLowerInvariant();
            int keywordScore = lowerKeyword.Length > 0 && lowerTitle.Contains(lowerKeyword) ? 100 : 50;
            int position = lowerKeyword.Length > 0 ? lowerTitle.IndexOf(lowerKeyword, StringComparison.Ordinal) : -1;
            int positionScore = position < 0 ? 25 : position <= 10 ? 100 : position <= 20 ? 75 : position <= 30 ? 50 : 25;
            int length = cleanTitle.Length;
            int lengthScore = length >= 40 && length <= 60 ? 100 : ((length >= 30 && length < 40) || (length > 60 && length <= 70)) ? 75 : 50;
            int triggerCount = 0;
            string[] triggers = new string[] { "실시간", "라이브", "긴급", "속보", "오늘", "분석", "급등", "급락", "전략", "핵심", "LIVE" };
            foreach (string trigger in triggers) if (lowerTitle.Contains(trigger.ToLowerInvariant())) triggerCount += 1;
            int triggerScore = triggerCount >= 2 ? 100 : triggerCount == 1 ? 70 : 40;
            int separatorScore = Regex.IsMatch(cleanTitle, "[0-9\\[\\]【】|｜:：]") ? 70 : 40;
            return WeightedTotal(new int[] { keywordScore, positionScore, lengthScore, triggerScore, separatorScore }, new int[] { 30, 20, 15, 20, 15 });
        }

        private static int ThumbnailScore(string thumbnail)
        {
            if (string.IsNullOrWhiteSpace(thumbnail)) return 0;
            int custom = thumbnail.Contains("default.jpg") ? 40 : 70;
            return WeightedTotal(new int[] { 100, custom, 60 }, new int[] { 40, 30, 30 });
        }

        private static int MetadataScore(string description, int tagCount, string keyword)
        {
            string desc = description == null ? "" : description;
            string lower = desc.ToLowerInvariant();
            string lowerKeyword = keyword == null ? "" : keyword.Trim().ToLowerInvariant();
            string first100 = lower.Substring(0, Math.Min(100, lower.Length));
            string first200 = lower.Substring(0, Math.Min(200, lower.Length));
            int keywordScore = lowerKeyword.Length > 0 && first100.Contains(lowerKeyword) ? 100 : lowerKeyword.Length > 0 && first200.Contains(lowerKeyword) ? 60 : 0;
            int length = desc.Length;
            int lengthScore = length >= 500 ? 100 : length >= 200 ? 75 : length >= 100 ? 50 : 25;
            int hashtags = Regex.Matches(desc, "#[\\p{L}\\p{N}_-]+").Count;
            int hashScore = hashtags >= 3 && hashtags <= 5 ? 100 : ((hashtags >= 1 && hashtags <= 2) || (hashtags >= 6 && hashtags <= 10)) ? 70 : 30;
            int tagScore = tagCount >= 10 && tagCount <= 15 ? 100 : ((tagCount >= 5 && tagCount < 10) || (tagCount > 15 && tagCount <= 20)) ? 75 : 50;
            return WeightedTotal(new int[] { keywordScore, lengthScore, hashScore, tagScore }, new int[] { 35, 25, 25, 15 });
        }

        private static int[] RelativePair(long? own, long? competitor)
        {
            long left = own.HasValue ? own.Value : 0L;
            long right = competitor.HasValue ? competitor.Value : 0L;
            long max = Math.Max(left, right);
            if (max <= 0) return new int[] { 0, 0 };
            return new int[] {
                (int)Math.Round(left * 100.0 / max),
                (int)Math.Round(right * 100.0 / max)
            };
        }

        private static int CcvRateScore(long? currentViewers, long? subscribers)
        {
            if (!currentViewers.HasValue || !subscribers.HasValue || subscribers.Value <= 0) return 0;
            double rate = currentViewers.Value / (double)subscribers.Value;
            if (rate >= 0.01) return 100;
            if (rate >= 0.005) return 80;
            if (rate >= 0.001) return 60;
            return 30;
        }

        private static int EngagementScore(VideoInfo video)
        {
            int likeScore = RatioScore(video.LikeCount, video.ViewCount, new double[] { 0.05, 0.03, 0.01 }, new int[] { 100, 80, 60, 30 });
            int commentScore = RatioScore(video.CommentCount, video.ViewCount, new double[] { 0.01, 0.005, 0.001 }, new int[] { 100, 75, 50, 25 });
            return WeightedTotal(new int[] { likeScore, commentScore }, new int[] { 40, 30 });
        }

        private static int RatioScore(long? numerator, long? denominator, double[] thresholds, int[] scores)
        {
            if (!numerator.HasValue || !denominator.HasValue || denominator.Value <= 0) return 0;
            double ratio = numerator.Value / (double)denominator.Value;
            for (int index = 0; index < thresholds.Length; index += 1)
            {
                if (ratio >= thresholds[index]) return scores[index];
            }
            return scores[scores.Length - 1];
        }

        private static int Average(int[] values)
        {
            if (values.Length == 0) return 0;
            int sum = 0;
            foreach (int value in values) sum += value;
            return (int)Math.Round(sum / (double)values.Length);
        }

        private static int WeightedTotal(int[] scores)
        {
            return WeightedTotal(scores, new int[] { 15, 10, 10, 25, 15, 15, 10 });
        }

        private static int WeightedTotal(int[] scores, int[] weights)
        {
            int totalWeight = 0;
            int total = 0;
            for (int index = 0; index < scores.Length && index < weights.Length; index += 1)
            {
                total += scores[index] * weights[index];
                totalWeight += weights[index];
            }
            return totalWeight == 0 ? 0 : (int)Math.Round(total / (double)totalWeight);
        }

        private static string Grade(int score)
        {
            if (score >= 85) return "A";
            if (score >= 70) return "B";
            if (score >= 55) return "C";
            if (score >= 40) return "D";
            return "F";
        }

        private static string PriorityText(string[] labels, int[] gaps, int[] weights, string keyword, string competitorLabel)
        {
            StringBuilder builder = new StringBuilder();
            bool[] used = new bool[labels.Length];
            for (int pick = 0; pick < 3; pick += 1)
            {
                int best = -1;
                int bestImpact = 0;
                for (int index = 0; index < labels.Length; index += 1)
                {
                    int impact = Math.Max(gaps[index], 0) * weights[index];
                    if (!used[index] && impact > bestImpact)
                    {
                        bestImpact = impact;
                        best = index;
                    }
                }
                if (best < 0) break;
                used[best] = true;
                if (builder.Length > 0) builder.Append("\r\n");
                builder.Append(pick + 1).Append(". ").Append(labels[best])
                    .Append(": ").Append(competitorLabel).Append("보다 ").Append(gaps[best]).Append("점 낮음\r\n")
                    .Append("   이유: ").Append(CategoryReason(labels[best])).Append("\r\n")
                    .Append("   할 일: ").Append(CategoryAction(labels[best], keyword));
            }
            return builder.Length == 0
                ? "큰 약점이 크게 잡히지 않았습니다.\r\n현재 점수표를 유지하면서 제목, 설명, 시청자 반응을 계속 점검하세요."
                : builder.ToString();
        }

        private static string TitleSuggestion(string keyword)
        {
            string cleanKeyword = string.IsNullOrWhiteSpace(keyword) ? "핵심 키워드" : keyword.Trim();
            return cleanKeyword + " 실시간 | 오늘 핵심 구간 바로 분석\r\n"
                + cleanKeyword + " 라이브 | 지금 가장 많이 묻는 내용 정리\r\n"
                + cleanKeyword + " 오늘 라이브 | 초반 10분 핵심 혜택 공개";
        }

        private static string EmptyDetail(ComparisonReport report, string summary)
        {
            StringBuilder builder = new StringBuilder();
            builder.AppendLine("LiveRank 비교 분석");
            builder.AppendLine("키워드: " + report.Keyword);
            builder.AppendLine("내 채널: " + report.OwnChannelTitle);
            builder.AppendLine("내 노필터 순위: " + RankValueText(report.OwnNoFilterRank));
            builder.AppendLine("비교 대상: 비교 대상 없음");
            builder.AppendLine();
            builder.AppendLine("이번 결과의 의미");
            builder.AppendLine("- " + summary);
            builder.AppendLine("- 지난 영상이나 라이브 필터 1위를 억지 비교 대상으로 바꾸지 않았습니다.");
            builder.AppendLine("- 그래서 점수 격차와 개선 우선순위는 산정하지 않는 것이 맞습니다.");
            builder.AppendLine();
            builder.AppendLine("그래도 오늘 확인할 것");
            builder.AppendLine("1. 같은 키워드로 노필터 검색했을 때 실시간 라이브가 실제로 없는지 확인");
            builder.AppendLine("2. 내 제목 앞부분에 키워드와 방송 내용을 분명하게 배치");
            builder.AppendLine("3. 설명 첫 2줄에 키워드, 오늘 방송 내용, 참여 이유를 추가");
            builder.AppendLine();
            builder.AppendLine("제목 개선 예시");
            builder.AppendLine(report.TitleSuggestion);
            builder.AppendLine();
            builder.AppendLine("참고: 공개 데이터 기준 분석이며 1등 노출이나 조회수 상승을 보장하지 않습니다.");
            return builder.ToString();
        }

        private static string DetailReport(
            ComparisonReport report,
            VideoInfo ownVideo,
            VideoInfo competitorVideo,
            ChannelStats ownStats,
            ChannelStats competitorStats,
            string[] labels,
            int[] ownScores,
            int[] competitorScores,
            int[] gaps,
            int[] weights)
        {
            int overallGap = report.CompetitorScore - report.OwnScore;
            StringBuilder builder = new StringBuilder();
            builder.AppendLine("유튜브 라이브 경쟁력 진단");
            builder.AppendLine(report.ComparisonTitle);
            builder.AppendLine("================================");
            builder.AppendLine("키워드: " + report.Keyword);
            builder.AppendLine("내 채널: " + report.OwnChannelTitle);
            builder.AppendLine("내 노필터 순위: " + RankValueText(report.OwnNoFilterRank));
            builder.AppendLine("비교 대상: " + report.CompetitorRankLabel);
            builder.AppendLine(report.CompetitorRankLabel + ": " + report.CompetitorChannelTitle);
            builder.AppendLine();
            builder.AppendLine("종합 점수");
            builder.AppendLine("- 내 채널: " + report.OwnScore + "점(" + report.OwnGrade + ")");
            builder.AppendLine("- " + report.CompetitorRankLabel + ": " + report.CompetitorScore + "점(" + report.CompetitorGrade + ")");
            builder.AppendLine("- 격차: " + (overallGap > 0 ? "-" + overallGap : "+" + Math.Abs(overallGap)) + "점");
            builder.AppendLine();
            builder.AppendLine("결론");
            builder.AppendLine("- " + report.Summary);
            if (overallGap > 0)
            {
                builder.AppendLine("- 현재 기준으로 " + report.CompetitorRankLabel + "가 " + overallGap + "점 앞섭니다. 아래 약점부터 고치면 상위 진입 가능성을 높일 수 있습니다.");
            }
            else if (overallGap < 0)
            {
                builder.AppendLine("- 현재 기준으로 내 채널이 " + report.CompetitorRankLabel + "보다 " + Math.Abs(overallGap) + "점 앞섭니다. 강점은 유지하고 낮은 항목만 보강하세요.");
                if (report.OwnNoFilterRank == 1)
                {
                    builder.AppendLine("- 내 채널이 노필터 라이브 1위라서, 이번 리포트는 아래 순위의 비교 가능한 라이브를 참고 대상으로 삼았습니다.");
                }
            }
            else
            {
                builder.AppendLine("- 종합 점수는 비슷합니다. 작은 차이를 만드는 제목, 설명, 초반 반응을 우선 점검하세요.");
            }
            builder.AppendLine("- 이 리포트는 보장이 아니라 공개 API 기준 진단입니다.");
            builder.AppendLine();
            builder.AppendLine("이번에 비교한 것");
            builder.AppendLine("1. 제목: 키워드 포함, 앞부분 배치, 길이, 클릭 이유");
            builder.AppendLine("2. 썸네일: API로 확인 가능한 썸네일 존재와 기본 품질 신호");
            builder.AppendLine("3. 메타데이터: 설명 첫 부분, 설명 길이, 해시태그, 태그 수");
            builder.AppendLine("4. 라이브 현재 성과: 현재 시청자 또는 공개 조회수 상대 비교");
            builder.AppendLine("5. 채널 신뢰도: 구독자 수와 구독자 대비 현재 반응");
            builder.AppendLine("6. 시청자 반응: 좋아요/댓글 비율");
            builder.AppendLine("7. 발행 전략: 이번 Windows 소스 버전은 기본값으로 두고 다음 고도화 대상");
            builder.AppendLine();
            builder.AppendLine("실제 비교 대상");
            builder.AppendLine("- 내 제목: " + Safe(ownVideo.Title));
            builder.AppendLine("- " + report.CompetitorShortLabel + " 제목: " + Safe(competitorVideo.Title));
            builder.AppendLine("- 내 현재 시청자/조회수: " + MetricText(ownVideo));
            builder.AppendLine("- " + report.CompetitorShortLabel + " 현재 시청자/조회수: " + MetricText(competitorVideo));
            builder.AppendLine("- 내 구독자 수: " + NumberText(ownStats.SubscriberCount));
            builder.AppendLine("- " + report.CompetitorShortLabel + " 구독자 수: " + NumberText(competitorStats.SubscriberCount));
            builder.AppendLine();
            AppendCoreDiagnosis(builder, labels, gaps, report);
            builder.AppendLine("항목별 진단");
            for (int index = 0; index < labels.Length; index += 1)
            {
                builder.AppendLine((index + 1) + ". " + DisplayLabel(labels[index]) + ": 내 " + ownScores[index] + "점 / " + report.CompetitorShortLabel + " " + competitorScores[index] + "점 / " + GapText(gaps[index], report.CompetitorShortLabel));
                builder.AppendLine("   판정: " + StatusText(gaps[index]));
                builder.AppendLine("   근거: " + CategoryEvidence(labels[index], report.Keyword, ownVideo, competitorVideo, ownStats, competitorStats, report.CompetitorShortLabel));
                builder.AppendLine("   의미: " + CategoryReason(labels[index]));
                builder.AppendLine("   할 일: " + CategoryAction(labels[index], report.Keyword));
            }
            builder.AppendLine();
            builder.AppendLine("오늘 바로 수정할 순서");
            builder.AppendLine(report.Priorities);
            builder.AppendLine();
            builder.AppendLine("최종 해석");
            builder.AppendLine(FinalInterpretation(overallGap, labels, gaps, report.CompetitorRankLabel));
            builder.AppendLine();
            builder.AppendLine("제목 개선 예시");
            builder.AppendLine(report.TitleSuggestion);
            builder.AppendLine();
            builder.AppendLine("주의");
            builder.AppendLine("- 1등 보장, 조회수 상승 보장으로 해석하면 안 됩니다.");
            builder.AppendLine("- YouTube 내부 알고리즘, CTR, 노출수, 평균 시청 시간은 현재 리포트에 포함되지 않습니다.");
            builder.AppendLine("- 썸네일 이미지는 API로 정밀 판독하지 못하므로 화면에서 직접 확인해야 합니다.");
            return builder.ToString();
        }

        private static void AppendCoreDiagnosis(StringBuilder builder, string[] labels, int[] gaps, ComparisonReport report)
        {
            int worst = -1;
            int second = -1;
            int best = -1;
            for (int index = 0; index < labels.Length; index += 1)
            {
                if (gaps[index] > 0 && (worst < 0 || gaps[index] > gaps[worst]))
                {
                    second = worst;
                    worst = index;
                }
                else if (gaps[index] > 0 && (second < 0 || gaps[index] > gaps[second]))
                {
                    second = index;
                }
                if (gaps[index] < 0 && (best < 0 || gaps[index] < gaps[best]))
                {
                    best = index;
                }
            }
            builder.AppendLine("핵심 진단");
            if (best >= 0) builder.AppendLine("- 강점: " + DisplayLabel(labels[best]) + "은 내 채널이 " + Math.Abs(gaps[best]) + "점 앞섭니다.");
            else builder.AppendLine("- 강점: 현재 공개 지표상 " + report.CompetitorShortLabel + "보다 확실히 앞선 항목은 크지 않습니다.");
            if (worst >= 0) builder.AppendLine("- 치명적 격차: " + DisplayLabel(labels[worst]) + "에서 " + report.CompetitorShortLabel + "가 " + gaps[worst] + "점 앞섭니다.");
            if (second >= 0) builder.AppendLine("- 두 번째 약점: " + DisplayLabel(labels[second]) + "도 " + gaps[second] + "점 차이가 납니다.");
            builder.AppendLine("- 결론: 점수를 보는 화면이 아니라, 위 약점부터 고치기 위한 실행 순서표입니다.");
            builder.AppendLine("- 순위 해석: " + InterpretationNote(report));
            builder.AppendLine();
        }

        private static string InterpretationNote(ComparisonReport report)
        {
            if (report.OwnNoFilterRank == 1 && report.CompetitorScore > report.OwnScore)
            {
                return InterpretationCaseA;
            }
            if (report.OwnScore > report.CompetitorScore
                && report.OwnNoFilterRank > 0
                && report.CompetitorNoFilterRank > 0
                && report.OwnNoFilterRank > report.CompetitorNoFilterRank)
            {
                return InterpretationCaseB;
            }
            return InterpretationDefault;
        }

        private static string CategoryReason(string label)
        {
            if (label.StartsWith("제목")) return "검색 결과에서 가장 먼저 보이는 신호라 키워드 위치와 클릭 이유가 순위 진입에 중요합니다.";
            if (label.StartsWith("썸네일")) return "모바일 검색에서는 썸네일이 클릭 여부를 크게 좌우하지만, 현재는 API로 확인 가능한 기본 신호만 봅니다.";
            if (label.StartsWith("메타")) return "설명 앞부분과 해시태그는 YouTube가 방송 내용을 이해하는 보조 신호입니다.";
            if (label.StartsWith("라이브")) return "지금 켜져 있는 방송의 시청자/조회수 신호가 강하면 노필터 상위 라이브와의 격차가 줄어듭니다.";
            if (label.StartsWith("채널")) return "같은 키워드라도 채널 체급과 구독자 대비 반응이 높으면 신뢰 신호가 좋아집니다.";
            if (label.StartsWith("시청자")) return "좋아요와 댓글은 방송 반응이 살아 있는지 보여주는 공개 신호입니다.";
            return "정해진 시간대와 반복 방송 패턴은 장기적으로 검색/구독자 재방문에 도움이 됩니다.";
        }

        private static string CategoryAction(string label, string keyword)
        {
            string cleanKeyword = string.IsNullOrWhiteSpace(keyword) ? "핵심 키워드" : keyword.Trim();
            if (label.StartsWith("제목")) return "제목 앞 20자 안에 '" + cleanKeyword + "'와 오늘 방송의 차별점을 넣으세요.";
            if (label.StartsWith("썸네일")) return "모바일에서 한눈에 보이는 큰 글자 3~5단어, 상품/얼굴/핵심 장면을 분명히 확인하세요.";
            if (label.StartsWith("메타")) return "설명 첫 2줄에 '" + cleanKeyword + "', 오늘 다룰 내용, 참여 이유를 넣고 해시태그 3~5개로 정리하세요.";
            if (label.StartsWith("라이브")) return "방송 초반 10분에 질문, 이벤트, 고정댓글로 좋아요/채팅/체류를 먼저 끌어올리세요.";
            if (label.StartsWith("채널")) return "같은 키워드 방송을 반복 편성하고, 다음 방송 예고와 구독 이유를 고정 멘트로 넣으세요.";
            if (label.StartsWith("시청자")) return "좋아요와 댓글을 요청하는 문장을 방송 화면/고정댓글/진행 멘트에 넣으세요.";
            return "가능하면 같은 요일과 비슷한 시간대에 반복 방송해 재방문 패턴을 만드세요.";
        }

        private static string DisplayLabel(string label)
        {
            if (label.StartsWith("제목")) return "제목 최적화(노출력)";
            if (label.StartsWith("썸네일")) return "썸네일(클릭력)";
            if (label.StartsWith("메타")) return "메타데이터(검색 이해도)";
            if (label.StartsWith("라이브")) return "라이브 현재 성과(초반 화력)";
            if (label.StartsWith("채널")) return "채널 신뢰도";
            if (label.StartsWith("시청자")) return "시청자 반응(참여도)";
            return "발행 전략(재방문력)";
        }

        private static string StatusText(int competitorMinusOwn)
        {
            if (competitorMinusOwn >= 20) return "핵심 약점";
            if (competitorMinusOwn >= 6) return "개선 권장";
            if (competitorMinusOwn <= -6) return "내 강점";
            return "동등";
        }

        private static string CategoryEvidence(string label, string keyword, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats, string competitorLabel)
        {
            if (label.StartsWith("제목")) return "내 제목 " + TitleFacts(ownVideo.Title, keyword) + " / " + competitorLabel + " 제목 " + TitleFacts(competitorVideo.Title, keyword);
            if (label.StartsWith("썸네일")) return "내 썸네일 " + ThumbnailFacts(ownVideo.Thumbnail) + " / " + competitorLabel + " 썸네일 " + ThumbnailFacts(competitorVideo.Thumbnail);
            if (label.StartsWith("메타")) return "내 설명 " + MetadataFacts(ownVideo.Description, ownVideo.TagCount, keyword) + " / " + competitorLabel + " 설명 " + MetadataFacts(competitorVideo.Description, competitorVideo.TagCount, keyword);
            if (label.StartsWith("라이브")) return "내 방송 " + MetricText(ownVideo) + " / " + competitorLabel + " 방송 " + MetricText(competitorVideo);
            if (label.StartsWith("채널")) return "내 구독자 " + NumberText(ownStats.SubscriberCount) + " / " + competitorLabel + " 구독자 " + NumberText(competitorStats.SubscriberCount);
            if (label.StartsWith("시청자")) return "내 반응 " + EngagementFacts(ownVideo) + " / " + competitorLabel + " 반응 " + EngagementFacts(competitorVideo);
            return "이번 Windows 소스 버전은 반복 방송 시간대 상세 분석 전 단계라 기본값으로 비교했습니다.";
        }

        private static string TitleFacts(string title, string keyword)
        {
            string cleanTitle = title ?? "";
            string lowerTitle = cleanTitle.ToLowerInvariant();
            string lowerKeyword = string.IsNullOrWhiteSpace(keyword) ? "" : keyword.Trim().ToLowerInvariant();
            int position = lowerKeyword.Length > 0 ? lowerTitle.IndexOf(lowerKeyword, StringComparison.Ordinal) : -1;
            string keywordText = position < 0 ? "키워드 앞부분 미확인" : "키워드 " + (position + 1) + "번째 위치";
            return keywordText + ", 길이 " + cleanTitle.Length + "자";
        }

        private static string ThumbnailFacts(string thumbnail)
        {
            if (string.IsNullOrWhiteSpace(thumbnail)) return "이미지 확인 불가";
            return thumbnail.Contains("default.jpg") ? "기본 썸네일 가능성 있음" : "썸네일 이미지 확인됨";
        }

        private static string MetadataFacts(string description, int tagCount, string keyword)
        {
            string desc = description ?? "";
            string lower = desc.ToLowerInvariant();
            string lowerKeyword = string.IsNullOrWhiteSpace(keyword) ? "" : keyword.Trim().ToLowerInvariant();
            string first100 = lower.Substring(0, Math.Min(100, lower.Length));
            bool hasKeyword = lowerKeyword.Length > 0 && first100.Contains(lowerKeyword);
            int hashtags = Regex.Matches(desc, "#[\\p{L}\\p{N}_-]+").Count;
            return "첫 100자 키워드 " + (hasKeyword ? "있음" : "부족") + ", 설명 " + desc.Length + "자, 해시태그 " + hashtags + "개, 태그 " + tagCount + "개";
        }

        private static string EngagementFacts(VideoInfo video)
        {
            return "좋아요율 " + RatioText(video.LikeCount, video.ViewCount) + ", 댓글률 " + RatioText(video.CommentCount, video.ViewCount);
        }

        private static string RatioText(long? numerator, long? denominator)
        {
            if (!numerator.HasValue || !denominator.HasValue || denominator.Value <= 0) return "확인 불가";
            double percent = numerator.Value * 100.0 / denominator.Value;
            return percent.ToString("0.00") + "%";
        }

        private static string FinalInterpretation(int overallGap, string[] labels, int[] gaps, string competitorLabel)
        {
            int worst = -1;
            int second = -1;
            for (int index = 0; index < labels.Length; index += 1)
            {
                if (gaps[index] <= 0) continue;
                if (worst < 0 || gaps[index] > gaps[worst])
                {
                    second = worst;
                    worst = index;
                }
                else if (second < 0 || gaps[index] > gaps[second])
                {
                    second = index;
                }
            }
            StringBuilder builder = new StringBuilder();
            builder.Append(overallGap > 0 ? "내 채널은 현재 " + competitorLabel + "보다 종합 경쟁력이 부족합니다. " : "내 채널은 " + competitorLabel + "보다 종합 점수에서 밀리지 않지만 세부 약점은 계속 보강해야 합니다. ");
            if (worst >= 0)
            {
                builder.Append("1순위 원인은 ").Append(DisplayLabel(labels[worst])).Append("입니다.");
                if (second >= 0) builder.Append(" 그다음은 ").Append(DisplayLabel(labels[second])).Append("입니다.");
                builder.Append(" ");
            }
            builder.Append("오늘은 우선순위 1~2개를 방송 제목/설명/초반 진행에 바로 반영하세요.");
            return builder.ToString();
        }

        private static string GapText(int competitorMinusOwn, string competitorLabel)
        {
            if (competitorMinusOwn > 0) return competitorLabel + "가 " + competitorMinusOwn + "점 앞섬";
            if (competitorMinusOwn < 0) return "내 채널이 " + Math.Abs(competitorMinusOwn) + "점 앞섬";
            return "동점";
        }

        private static string MetricText(VideoInfo video)
        {
            if (video.CurrentViewers.HasValue) return "현재 시청자 " + NumberText(video.CurrentViewers);
            if (video.ViewCount.HasValue) return "공개 조회수 " + NumberText(video.ViewCount);
            return "확인 불가";
        }

        private static string NumberText(long? value)
        {
            return value.HasValue ? value.Value.ToString() : "확인 불가";
        }

        private static string Safe(string value)
        {
            return string.IsNullOrWhiteSpace(value) ? "확인 불가" : value;
        }
    }
}
