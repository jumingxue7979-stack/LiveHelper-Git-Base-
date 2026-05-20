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
            builder.AppendLine("리포트 버전: work6-channel-analysis");
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
        public long? CurrentViewers;
    }

    internal sealed class ChannelStats
    {
        public string ChannelId = "";
        public string ChannelTitle = "";
        public long? SubscriberCount;
        public long? ViewCount;
        public long? VideoCount;
    }

    internal sealed class ComparisonCategory
    {
        public const string TrafficMassId = "traffic_mass";
        public const string ChannelInfluenceId = "channel_influence";
        public const string BasicOptimizationId = "basic_optimization";
        public const string ReactionQualityId = "reaction_quality";

        public static readonly ComparisonCategory TrafficMass =
            new ComparisonCategory(TrafficMassId, "트래픽 질량", "60점 묶음", true);
        public static readonly ComparisonCategory ChannelInfluence =
            new ComparisonCategory(ChannelInfluenceId, "채널 영향", "30점 묶음", true);
        public static readonly ComparisonCategory BasicOptimization =
            new ComparisonCategory(BasicOptimizationId, "기본 최적화", "10점 묶음", true);
        public static readonly ComparisonCategory ReactionQuality =
            new ComparisonCategory(ReactionQualityId, "반응 품질", "점수 분리", false);

        public static readonly ComparisonCategory[] ScoreCategories = new ComparisonCategory[]
        {
            TrafficMass,
            ChannelInfluence,
            BasicOptimization
        };

        public readonly string Id;
        public readonly string Label;
        public readonly string WeightLabel;
        public readonly bool Scored;

        private ComparisonCategory(string id, string label, string weightLabel, bool scored)
        {
            Id = id;
            Label = label;
            WeightLabel = weightLabel;
            Scored = scored;
        }
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
            int[] currentPair = RelativePair(ownVideo.CurrentViewers, competitorVideo.CurrentViewers);
            int[] subscriberPair = RelativePair(ownStats.SubscriberCount, competitorStats.SubscriberCount);
            int[] channelViewPair = RelativePair(ownStats.ViewCount, competitorStats.ViewCount);
            int[] channelVideoPair = RelativePair(ownStats.VideoCount, competitorStats.VideoCount);
            int ownTrafficMass = WeightedTotal(new int[] {
                currentPair[0],
                RankAccessScore(ownNoFilterRank),
                CcvRateScore(ownVideo.CurrentViewers, ownStats.SubscriberCount)
            }, new int[] { 40, 10, 10 });
            int competitorTrafficMass = WeightedTotal(new int[] {
                currentPair[1],
                RankAccessScore(competitorNoFilterRank),
                CcvRateScore(competitorVideo.CurrentViewers, competitorStats.SubscriberCount)
            }, new int[] { 40, 10, 10 });
            int ownChannelInfluence = WeightedTotal(new int[] {
                subscriberPair[0],
                Average(new int[] { channelViewPair[0], channelVideoPair[0] }),
                ChannelInfoScore(ownStats)
            }, new int[] { 15, 10, 5 });
            int competitorChannelInfluence = WeightedTotal(new int[] {
                subscriberPair[1],
                Average(new int[] { channelViewPair[1], channelVideoPair[1] }),
                ChannelInfoScore(competitorStats)
            }, new int[] { 15, 10, 5 });
            int ownBasicOptimization = WeightedTotal(new int[] { ownTitle, ownMeta, ownThumb }, new int[] { 4, 3, 3 });
            int competitorBasicOptimization = WeightedTotal(new int[] { competitorTitle, competitorMeta, competitorThumb }, new int[] { 4, 3, 3 });
            string[] labels = new string[] {
                ComparisonCategory.TrafficMass.Label,
                ComparisonCategory.ChannelInfluence.Label,
                ComparisonCategory.BasicOptimization.Label
            };
            int[] ownScores = new int[] {
                ownTrafficMass, ownChannelInfluence, ownBasicOptimization
            };
            int[] competitorScores = new int[] {
                competitorTrafficMass, competitorChannelInfluence, competitorBasicOptimization
            };
            int[] gaps = new int[] {
                competitorTrafficMass - ownTrafficMass,
                competitorChannelInfluence - ownChannelInfluence,
                competitorBasicOptimization - ownBasicOptimization
            };
            int[] weights = new int[] { 60, 30, 10 };

            report.OwnScore = WeightedTotal(ownScores, weights);
            report.CompetitorScore = WeightedTotal(competitorScores, weights);
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

        private static int RankAccessScore(int rank)
        {
            if (rank <= 0) return 0;
            if (rank == 1) return 100;
            if (rank <= 3) return 80;
            if (rank <= 5) return 60;
            if (rank <= 10) return 40;
            if (rank <= 20) return 20;
            return 0;
        }

        private static int ChannelInfoScore(ChannelStats stats)
        {
            int score = 0;
            if (stats != null && stats.SubscriberCount.HasValue) score += 40;
            if (stats != null && stats.ViewCount.HasValue) score += 30;
            if (stats != null && stats.VideoCount.HasValue) score += 30;
            return score;
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
                ? "큰 약점이 크게 잡히지 않았습니다.\r\n트래픽 질량, 채널 영향, 기본 최적화를 같은 구조로 계속 점검하세요."
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
            builder.AppendLine("리포트 버전: work6-channel-analysis");
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
            builder.AppendLine("리포트 버전: work6-channel-analysis");
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
            builder.AppendLine("1. 트래픽 질량 (60점 묶음): 현재 시청자, 노필터 순위 접근도, 구독자 대비 현재 시청자 효율");
            builder.AppendLine("2. 채널 영향 (30점 묶음): 구독자 규모, 채널 누적 조회/영상 기반, 채널 정보 확인성");
            builder.AppendLine("3. 기본 최적화 (10점 묶음): 제목, 설명, 썸네일 기본 신호");
            builder.AppendLine("4. 반응 품질: 채팅 참여율과 좋아요 반응은 점수와 분리해 참고");
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
            builder.AppendLine("반응 품질 참고");
            builder.AppendLine("- 채팅 참여율: 라이브 채팅 데이터 연결 전 단계");
            builder.AppendLine("- 좋아요 반응: " + ReactionQualityText(ownVideo, competitorVideo, report.CompetitorShortLabel));
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
            if (label.StartsWith("트래픽")) return "현재 켜져 있는 방송의 시청자 규모와 순위 접근도가 채널 비교의 가장 큰 축입니다.";
            if (label.StartsWith("채널")) return "같은 키워드라도 채널 체급과 운영 기반이 높으면 경쟁력이 안정적으로 쌓입니다.";
            if (label.StartsWith("기본")) return "제목, 설명, 썸네일은 YouTube와 시청자가 방송 내용을 빠르게 이해하는 기본 신호입니다.";
            return "반응 품질은 총점과 분리해서 다음 방송 운영 참고로만 봅니다.";
        }

        private static string CategoryAction(string label, string keyword)
        {
            string cleanKeyword = string.IsNullOrWhiteSpace(keyword) ? "핵심 키워드" : keyword.Trim();
            if (label.StartsWith("트래픽")) return "방송 시작 직후 유입 동선과 고정 멘트로 시청 이유를 분명히 제시하세요.";
            if (label.StartsWith("채널")) return "같은 키워드 방송을 반복 편성하고, 다음 방송 예고와 구독 이유를 고정 멘트로 넣으세요.";
            if (label.StartsWith("기본")) return "제목 앞 20자와 설명 첫 2줄에 '" + cleanKeyword + "', 오늘 다룰 내용, 참여 이유를 넣으세요.";
            return "채팅 참여율과 좋아요 반응은 점수와 분리해 다음 방송 운영에만 참고하세요.";
        }

        private static string DisplayLabel(string label)
        {
            if (label.StartsWith("트래픽")) return ComparisonCategory.TrafficMass.Label + " (" + ComparisonCategory.TrafficMass.WeightLabel + ")";
            if (label.StartsWith("채널")) return ComparisonCategory.ChannelInfluence.Label + " (" + ComparisonCategory.ChannelInfluence.WeightLabel + ")";
            if (label.StartsWith("기본")) return ComparisonCategory.BasicOptimization.Label + " (" + ComparisonCategory.BasicOptimization.WeightLabel + ")";
            return ComparisonCategory.ReactionQuality.Label + " (" + ComparisonCategory.ReactionQuality.WeightLabel + ")";
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
            if (label.StartsWith("트래픽")) return "내 방송 " + TrafficFacts(ownVideo, ownStats) + " / " + competitorLabel + " 방송 " + TrafficFacts(competitorVideo, competitorStats);
            if (label.StartsWith("채널")) return "내 채널 " + ChannelFacts(ownStats) + " / " + competitorLabel + " 채널 " + ChannelFacts(competitorStats);
            if (label.StartsWith("기본")) return "내 기본 신호 " + BasicFacts(ownVideo, keyword) + " / " + competitorLabel + " 기본 신호 " + BasicFacts(competitorVideo, keyword);
            return ReactionQualityText(ownVideo, competitorVideo, competitorLabel);
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

        private static string BasicFacts(VideoInfo video, string keyword)
        {
            return TitleFacts(video.Title, keyword) + ", " + MetadataFacts(video.Description, video.TagCount, keyword)
                + ", 썸네일 " + ThumbnailFacts(video.Thumbnail);
        }

        private static string TrafficFacts(VideoInfo video, ChannelStats stats)
        {
            return MetricText(video) + ", 구독자 대비 현재 시청자 효율 " + CcvRateText(video.CurrentViewers, stats == null ? null : stats.SubscriberCount);
        }

        private static string ChannelFacts(ChannelStats stats)
        {
            return "구독자 " + NumberText(stats == null ? null : stats.SubscriberCount)
                + ", 누적 조회 " + NumberText(stats == null ? null : stats.ViewCount)
                + ", 영상 수 " + NumberText(stats == null ? null : stats.VideoCount);
        }

        private static string ReactionQualityText(VideoInfo ownVideo, VideoInfo competitorVideo, string competitorLabel)
        {
            return "내 좋아요 반응 " + RatioText(ownVideo.LikeCount, ownVideo.ViewCount)
                + " / " + competitorLabel + " 좋아요 반응 " + RatioText(competitorVideo.LikeCount, competitorVideo.ViewCount);
        }

        private static string CcvRateText(long? currentViewers, long? subscribers)
        {
            if (!currentViewers.HasValue || !subscribers.HasValue || subscribers.Value <= 0) return "확인 불가";
            double percent = currentViewers.Value * 100.0 / subscribers.Value;
            return percent.ToString("0.00") + "%";
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
