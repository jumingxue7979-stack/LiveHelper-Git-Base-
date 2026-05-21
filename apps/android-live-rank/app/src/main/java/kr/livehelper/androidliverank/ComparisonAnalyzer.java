package kr.livehelper.androidliverank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ComparisonAnalyzer {
    private static final String INTERPRETATION_CASE_A = "현재 내 채널은 노필터 라이브 1위입니다. 다만 채널/콘텐츠 진단 점수는 2위 채널이 더 높습니다. 따라서 현재 1위는 제목·메타 우위보다 실시간 유입 품질과 반응 신호 영향으로 해석됩니다.";
    private static final String INTERPRETATION_CASE_B = "진단 점수는 내 채널이 우위지만, 실제 노필터 순위는 낮습니다. 이 경우 콘텐츠 점수보다 실시간 유입 품질, 시청 유지, 반응 신호에서 차이가 난 것으로 해석됩니다.";
    private static final String INTERPRETATION_DEFAULT = "순위 차이의 핵심 원인은 콘텐츠 점수보다 실시간 유입 품질과 반응 신호 차이로 보입니다.";

    private ComparisonAnalyzer() {
    }

    static ComparisonReport empty(String keyword, String ownChannelTitle, String summary, int ownNoFilterRank) {
        ComparisonReport report = new ComparisonReport();
        report.keyword = keyword == null ? "" : keyword.trim();
        report.ownChannelTitle = ownChannelTitle == null ? "" : ownChannelTitle;
        report.ownNoFilterRank = ownNoFilterRank;
        report.comparisonTitle = ownNoFilterRank == 1 ? "내 채널(노필터 1위) - 비교 대상 없음" : "LiveRank 비교 분석";
        report.competitorRankLabel = "비교 대상 없음";
        report.competitorShortLabel = "비교 대상";
        report.summary = summary;
        report.priorities = "비교 대상이 없어 점수 격차와 개선 우선순위는 산정하지 않았습니다.";
        report.titleSuggestion = titleSuggestion(report.keyword);
        report.detail = emptyDetail(report, summary);
        return report;
    }

    static ComparisonReport build(String keyword, String ownChannelTitle, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats, int ownNoFilterRank, int competitorNoFilterRank) {
        ComparisonReport report = new ComparisonReport();
        report.keyword = keyword == null ? "" : keyword.trim();
        report.ownChannelTitle = ownChannelTitle == null ? "" : ownChannelTitle;
        report.ownNoFilterRank = ownNoFilterRank;
        report.competitorNoFilterRank = competitorNoFilterRank;
        report.competitorRankLabel = rankLabel(competitorNoFilterRank);
        report.competitorShortLabel = shortRankLabel(competitorNoFilterRank);
        report.comparisonTitle = comparisonTitle(ownNoFilterRank, report.competitorRankLabel);

        int ownTitle = titleScore(ownVideo.title, report.keyword);
        int competitorTitle = titleScore(competitorVideo.title, report.keyword);
        int ownThumb = thumbnailScore(ownVideo.thumbnail);
        int competitorThumb = thumbnailScore(competitorVideo.thumbnail);
        int ownMeta = metadataScore(ownVideo.description, ownVideo.tagCount, report.keyword);
        int competitorMeta = metadataScore(competitorVideo.description, competitorVideo.tagCount, report.keyword);
        int[] currentPair = relativePair(ownVideo.currentViewers, competitorVideo.currentViewers);
        int[] subscriberPair = relativePair(ownStats.subscriberCount, competitorStats.subscriberCount);
        int[] channelViewPair = relativePair(ownStats.viewCount, competitorStats.viewCount);
        int[] channelVideoPair = relativePair(ownStats.videoCount, competitorStats.videoCount);
        int ownRankAccess = rankAccessScore(ownNoFilterRank);
        int competitorRankAccess = rankAccessScore(competitorNoFilterRank);
        int ownCcvRate = ccvRateScore(ownVideo.currentViewers, ownStats.subscriberCount);
        int competitorCcvRate = ccvRateScore(competitorVideo.currentViewers, competitorStats.subscriberCount);
        int ownChannelBase = average(new int[] { channelViewPair[0], channelVideoPair[0] });
        int competitorChannelBase = average(new int[] { channelViewPair[1], channelVideoPair[1] });
        int ownChannelInfo = channelInfoScore(ownStats);
        int competitorChannelInfo = channelInfoScore(competitorStats);
        int ownTrafficMass = weightedTotal(new int[] {
            currentPair[0],
            ownRankAccess,
            ownCcvRate
        }, new int[] { 40, 10, 10 });
        int competitorTrafficMass = weightedTotal(new int[] {
            currentPair[1],
            competitorRankAccess,
            competitorCcvRate
        }, new int[] { 40, 10, 10 });
        int ownChannelInfluence = weightedTotal(new int[] {
            subscriberPair[0],
            ownChannelBase,
            ownChannelInfo
        }, new int[] { 15, 10, 5 });
        int competitorChannelInfluence = weightedTotal(new int[] {
            subscriberPair[1],
            competitorChannelBase,
            competitorChannelInfo
        }, new int[] { 15, 10, 5 });
        int ownBasicOptimization = weightedTotal(new int[] { ownTitle, ownMeta, ownThumb }, new int[] { 4, 3, 3 });
        int competitorBasicOptimization = weightedTotal(new int[] { competitorTitle, competitorMeta, competitorThumb }, new int[] { 4, 3, 3 });
        String[] labels = new String[] {
            "트래픽: 현재 시청자 상대 규모",
            "트래픽: 노필터 순위 접근도",
            "트래픽: 구독자 대비 현재 시청자 효율",
            "채널: 구독자 상대 규모",
            "채널: 누적 조회/영상 기반",
            "채널: 정보 확인성",
            "기본: 제목/설명/썸네일 최적화"
        };
        int[] ownScores = new int[] {
            currentPair[0], ownRankAccess, ownCcvRate, subscriberPair[0], ownChannelBase, ownChannelInfo, ownBasicOptimization
        };
        int[] competitorScores = new int[] {
            currentPair[1], competitorRankAccess, competitorCcvRate, subscriberPair[1], competitorChannelBase, competitorChannelInfo, competitorBasicOptimization
        };
        int[] gaps = new int[] {
            currentPair[1] - currentPair[0],
            competitorRankAccess - ownRankAccess,
            competitorCcvRate - ownCcvRate,
            subscriberPair[1] - subscriberPair[0],
            competitorChannelBase - ownChannelBase,
            competitorChannelInfo - ownChannelInfo,
            competitorBasicOptimization - ownBasicOptimization
        };
        int[] weights = new int[] { 40, 10, 10, 15, 10, 5, 10 };

        report.ownScore = weightedTotal(new int[] { ownTrafficMass, ownChannelInfluence, ownBasicOptimization }, new int[] { 60, 30, 10 });
        report.competitorScore = weightedTotal(new int[] { competitorTrafficMass, competitorChannelInfluence, competitorBasicOptimization }, new int[] { 60, 30, 10 });
        report.ownGrade = grade(report.ownScore);
        report.competitorGrade = grade(report.competitorScore);
        report.competitorChannelTitle = competitorVideo.channelTitle;
        report.competitorTitle = competitorVideo.title;
        report.ownTitle = ownVideo.title;
        report.titleSuggestion = titleSuggestion(report.keyword);
        report.summary = "내 채널 " + report.ownScore + "점(" + report.ownGrade + ") / " + report.competitorRankLabel + " "
            + report.competitorScore + "점(" + report.competitorGrade + ")";
        report.priorities = priorityText(labels, gaps, weights, report.keyword, report.competitorShortLabel);
        report.detail = compactDashboard(report, ownVideo, competitorVideo, ownStats, competitorStats, labels, ownScores, competitorScores, gaps, weights);
        return report;
    }


    private static String rankLabel(int rank) {
        return rank > 0 ? "노필터 라이브 " + rank + "위" : "노필터 라이브 비교 대상";
    }

    private static String shortRankLabel(int rank) {
        return rank > 0 ? rank + "위" : "비교 대상";
    }

    private static String rankValueText(int rank) {
        return rank > 0 ? rank + "위" : "확인 불가";
    }

    private static String comparisonTitle(int ownRank, String competitorLabel) {
        if (ownRank > 0) {
            return "내 채널(노필터 " + ownRank + "위) vs " + competitorLabel;
        }
        return "내 채널 vs " + competitorLabel;
    }

    private static int titleScore(String title, String keyword) {
        String cleanTitle = title == null ? "" : title;
        String lowerTitle = cleanTitle.toLowerCase();
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        int keywordScore = lowerKeyword.length() > 0 && lowerTitle.contains(lowerKeyword) ? 100 : 50;
        int position = lowerKeyword.length() > 0 ? lowerTitle.indexOf(lowerKeyword) : -1;
        int positionScore = position < 0 ? 25 : position <= 10 ? 100 : position <= 20 ? 75 : position <= 30 ? 50 : 25;
        int length = cleanTitle.length();
        int lengthScore = length >= 40 && length <= 60 ? 100 : ((length >= 30 && length < 40) || (length > 60 && length <= 70)) ? 75 : 50;
        int triggerCount = 0;
        String[] triggers = { "실시간", "라이브", "긴급", "속보", "오늘", "분석", "급등", "급락", "전략", "핵심", "LIVE" };
        for (String trigger : triggers) if (lowerTitle.contains(trigger.toLowerCase())) triggerCount += 1;
        int triggerScore = triggerCount >= 2 ? 100 : triggerCount == 1 ? 70 : 40;
        int separatorScore = cleanTitle.matches(".*[0-9\\[\\]【】|｜:：].*") ? 70 : 40;
        return weightedTotal(new int[] { keywordScore, positionScore, lengthScore, triggerScore, separatorScore }, new int[] { 30, 20, 15, 20, 15 });
    }

    private static int thumbnailScore(String thumbnail) {
        if (thumbnail == null || thumbnail.length() == 0) return 0;
        int custom = thumbnail.contains("default.jpg") ? 40 : 70;
        return weightedTotal(new int[] { 100, custom, 60 }, new int[] { 40, 30, 30 });
    }

    private static int metadataScore(String description, int tagCount, String keyword) {
        String desc = description == null ? "" : description;
        String lower = desc.toLowerCase();
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        int keywordScore = lowerKeyword.length() > 0 && lower.substring(0, Math.min(100, lower.length())).contains(lowerKeyword) ? 100
            : lowerKeyword.length() > 0 && lower.substring(0, Math.min(200, lower.length())).contains(lowerKeyword) ? 60 : 0;
        int length = desc.length();
        int lengthScore = length >= 500 ? 100 : length >= 200 ? 75 : length >= 100 ? 50 : 25;
        int hashtags = 0;
        Matcher matcher = Pattern.compile("#[\\p{L}\\p{N}_-]+").matcher(desc);
        while (matcher.find()) hashtags += 1;
        int hashScore = hashtags >= 3 && hashtags <= 5 ? 100 : ((hashtags >= 1 && hashtags <= 2) || (hashtags >= 6 && hashtags <= 10)) ? 70 : 30;
        int tagScore = tagCount >= 10 && tagCount <= 15 ? 100 : ((tagCount >= 5 && tagCount < 10) || (tagCount > 15 && tagCount <= 20)) ? 75 : 50;
        return weightedTotal(new int[] { keywordScore, lengthScore, hashScore, tagScore }, new int[] { 35, 25, 25, 15 });
    }

    private static int[] relativePair(Long own, Long competitor) {
        if (own == null && competitor == null) return new int[] { 0, 0 };
        long left = own == null ? 0L : own.longValue();
        long right = competitor == null ? 0L : competitor.longValue();
        long max = Math.max(left, right);
        if (max <= 0) return new int[] { 0, 0 };
        return new int[] {
            (int) Math.round(left * 100.0 / max),
            (int) Math.round(right * 100.0 / max)
        };
    }

    private static int ccvRateScore(Long currentViewers, Long subscribers) {
        if (currentViewers == null || subscribers == null || subscribers.longValue() <= 0) return 0;
        double rate = currentViewers.doubleValue() / subscribers.doubleValue();
        if (rate >= 0.01) return 100;
        if (rate >= 0.005) return 80;
        if (rate >= 0.001) return 60;
        return 30;
    }

    private static int rankAccessScore(int rank) {
        if (rank <= 0) return 0;
        if (rank == 1) return 100;
        if (rank <= 3) return 80;
        if (rank <= 5) return 60;
        if (rank <= 10) return 40;
        if (rank <= 20) return 20;
        return 0;
    }

    private static int channelInfoScore(ChannelStats stats) {
        int score = 0;
        if (stats != null && stats.subscriberCount != null) score += 40;
        if (stats != null && stats.viewCount != null) score += 30;
        if (stats != null && stats.videoCount != null) score += 30;
        return score;
    }

    private static int average(int[] values) {
        if (values.length == 0) return 0;
        int sum = 0;
        for (int value : values) sum += value;
        return Math.round(sum / (float) values.length);
    }

    private static int weightedTotal(int[] scores) {
        return weightedTotal(scores, new int[] { 15, 10, 10, 25, 15, 15, 10 });
    }

    private static int weightedTotal(int[] scores, int[] weights) {
        int totalWeight = 0;
        int total = 0;
        for (int index = 0; index < scores.length && index < weights.length; index += 1) {
            total += scores[index] * weights[index];
            totalWeight += weights[index];
        }
        return totalWeight == 0 ? 0 : Math.round(total / (float) totalWeight);
    }

    private static String grade(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "F";
    }

    private static String priorityText(String[] labels, int[] gaps, int[] weights, String keyword, String competitorLabel) {
        StringBuilder builder = new StringBuilder();
        int printed = 0;
        for (int index = 0; index < labels.length && printed < 3; index += 1) {
            if (gaps[index] <= 0) continue;
            if (builder.length() > 0) builder.append("\n");
            printed += 1;
            builder.append(printed).append(". ").append(tipTitle(labels[index]))
                .append(" - ").append(tipBody(labels[index], gaps[index], keyword))
                .append(" 격차 기여도 +").append(effectText(gaps[index], weights[index])).append("점");
        }
        return builder.length() == 0
            ? "큰 약점이 크게 잡히지 않았습니다.\n트래픽 질량, 채널 영향, 기본 최적화를 같은 구조로 계속 점검하세요."
            : builder.toString();
    }

    private static String titleSuggestion(String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "핵심 키워드" : keyword.trim();
        return cleanKeyword + " 실시간 | 오늘 핵심 구간 바로 분석\n"
            + cleanKeyword + " 라이브 | 지금 가장 많이 묻는 내용 정리\n"
            + cleanKeyword + " 오늘 라이브 | 초반 10분 핵심 혜택 공개";
    }

    private static String emptyDetail(ComparisonReport report, String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("LiveRank 비교 분석\n");
        builder.append("리포트 버전: work6-channel-analysis\n");
        builder.append("키워드: ").append(report.keyword).append("\n");
        builder.append("내 채널: ").append(report.ownChannelTitle).append("\n");
        builder.append("내 노필터 순위: ").append(rankValueText(report.ownNoFilterRank)).append("\n");
        builder.append("비교 대상: 비교 대상 없음\n\n");
        builder.append("이번 결과의 의미\n");
        builder.append("- ").append(summary).append("\n");
        builder.append("- 지난 영상이나 라이브 필터 1위를 억지 비교 대상으로 바꾸지 않았습니다.\n");
        builder.append("- 그래서 점수 격차와 개선 우선순위는 산정하지 않는 것이 맞습니다.\n\n");
        builder.append("그래도 오늘 확인할 것\n");
        builder.append("1. 같은 키워드로 노필터 검색했을 때 실시간 라이브가 실제로 없는지 확인\n");
        builder.append("2. 내 제목 앞부분에 키워드와 방송 내용을 분명하게 배치\n");
        builder.append("3. 설명 첫 2줄에 키워드, 오늘 방송 내용, 참여 이유를 추가\n\n");
        builder.append("제목 개선 예시\n").append(report.titleSuggestion).append("\n\n");
        builder.append("참고: 공개 데이터 기준 분석이며 1등 노출이나 조회수 상승을 보장하지 않습니다.");
        return builder.toString();
    }

    private static String compactDashboard(
        ComparisonReport report,
        VideoInfo ownVideo,
        VideoInfo competitorVideo,
        ChannelStats ownStats,
        ChannelStats competitorStats,
        String[] labels,
        int[] ownScores,
        int[] competitorScores,
        int[] gaps,
        int[] weights
    ) {
        int overallGap = report.competitorScore - report.ownScore;
        StringBuilder builder = new StringBuilder();
        builder.append("LIVERANK 비교 리포트\n");
        builder.append("리포트 버전: work6-channel-analysis\n");
        builder.append(report.comparisonTitle).append("\n");
        builder.append("==============================\n");
        builder.append("키워드: ").append(report.keyword).append("\n");
        builder.append("내 채널: ").append(report.ownChannelTitle).append("\n");
        builder.append("내 노필터 순위: ").append(rankValueText(report.ownNoFilterRank)).append("\n");
        builder.append("비교 대상: ").append(report.competitorRankLabel).append("\n");
        builder.append(report.competitorRankLabel).append(": ").append(report.competitorChannelTitle).append("\n\n");

        builder.append("종합 요약\n");
        builder.append("내 채널  ").append(report.ownScore).append("점(").append(report.ownGrade).append(")\n");
        builder.append(report.competitorRankLabel).append(" ").append(report.competitorScore).append("점(").append(report.competitorGrade).append(")\n");
        builder.append("격차 ").append(overallGap > 0 ? "-" + overallGap : "+" + Math.abs(overallGap)).append("점\n");
        builder.append("판정: ").append(overallGap > 20 ? "핵심 개선 필요" : overallGap > 0 ? "개선 권장" : "경쟁 가능").append("\n");
        if (overallGap < 0 && report.ownNoFilterRank == 1) {
            builder.append("해석: 내 채널이 노필터 라이브 1위라 아래 순위의 비교 가능한 라이브를 참고 대상으로 봅니다.\n");
        }
        builder.append("순위 해석: ").append(interpretationNote(report)).append("\n");
        builder.append("\n");

        builder.append("세부 항목 점수표\n");
        for (int index = 0; index < labels.length; index += 1) {
            builder.append(index + 1).append(". ").append(compactLabel(labels[index])).append("  ").append(statusText(gaps[index])).append("\n");
            builder.append("   내  ").append(scoreText(ownScores[index])).append(" ").append(scoreBar(ownScores[index])).append("\n");
            builder.append("   ").append(report.competitorShortLabel).append(" ").append(scoreText(competitorScores[index])).append(" ").append(scoreBar(competitorScores[index])).append("\n");
            builder.append("   핵심: ").append(compactEvidence(labels[index], report.keyword, ownVideo, competitorVideo, ownStats, competitorStats)).append("\n");
        }

        builder.append("\n반응 품질 참고\n");
        builder.append("- 채팅 참여율: 라이브 채팅 데이터 연결 전 단계\n");
        builder.append("- 좋아요 반응: ").append(reactionQualityText(ownVideo, competitorVideo, report.competitorShortLabel)).append("\n");

        builder.append("\n개선 팁 (1위 따라잡기 전략)\n");
        appendCompactPriorities(builder, labels, gaps, weights, report.keyword);

        builder.append("\n오늘 바로 할 일\n");
        appendTopActions(builder, labels, gaps, weights, report.keyword);

        builder.append("\n제목 예시\n").append(report.titleSuggestion).append("\n\n");
        builder.append("참고: 공개 API 기준 진단입니다. 1등 노출이나 조회수 상승을 보장하지 않습니다.");
        return builder.toString();
    }

    private static String interpretationNote(ComparisonReport report) {
        if (report.ownNoFilterRank == 1 && report.competitorScore > report.ownScore) {
            return INTERPRETATION_CASE_A;
        }
        if (report.ownScore > report.competitorScore
            && report.ownNoFilterRank > 0
            && report.competitorNoFilterRank > 0
            && report.ownNoFilterRank > report.competitorNoFilterRank) {
            return INTERPRETATION_CASE_B;
        }
        return INTERPRETATION_DEFAULT;
    }

    private static void appendCompactPriorities(StringBuilder builder, String[] labels, int[] gaps, int[] weights, String keyword) {
        int printed = 0;
        for (int index = 0; index < labels.length && printed < 3; index += 1) {
            if (gaps[index] <= 0) continue;
            printed += 1;
            builder.append(printed).append(". ").append(tipTitle(labels[index]))
                .append(" - ").append(tipBody(labels[index], gaps[index], keyword))
                .append(" 격차 기여도 +").append(effectText(gaps[index], weights[index])).append("점\n");
        }
        if (printed == 0) {
            builder.append("- 큰 약점은 없습니다. 낮은 점수 항목 1개만 보강하세요.\n");
        }
    }

    private static void appendTopActions(StringBuilder builder, String[] labels, int[] gaps, int[] weights, String keyword) {
        boolean[] used = new boolean[labels.length];
        int printed = 0;
        for (int pick = 0; pick < 3; pick += 1) {
            int best = -1;
            int bestImpact = 0;
            for (int index = 0; index < labels.length; index += 1) {
                int impact = gaps[index] * weights[index];
                if (!used[index] && gaps[index] > 0 && impact > bestImpact) {
                    best = index;
                    bestImpact = impact;
                }
            }
            if (best < 0) break;
            used[best] = true;
            printed += 1;
            builder.append(printed).append(". ").append(shortAction(labels[best], keyword)).append("\n");
        }
        if (printed == 0) {
            builder.append("1. 현재 강점을 유지하고 제목/설명 첫 2줄만 재점검하세요.\n");
        }
    }

    private static String compactLabel(String label) {
        return displayLabel(label);
    }

    private static String scoreText(int score) {
        return String.valueOf(score) + "점(" + grade(score) + ")";
    }

    private static String scoreBar(int score) {
        int filled = Math.max(0, Math.min(10, (int)Math.round(score / 10.0)));
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < 10; index += 1) {
            builder.append(index < filled ? "#" : "-");
        }
        builder.append("]");
        return builder.toString();
    }

    private static String effectText(int gap, int weight) {
        return String.format("%.1f", gap * weight / 100.0);
    }

    private static String compactEvidence(String label, String keyword, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats) {
        if (label.startsWith("트래픽")) return "내 " + trafficFacts(ownVideo, ownStats) + " / 비교대상 " + trafficFacts(competitorVideo, competitorStats);
        if (label.startsWith("채널")) return "내 " + channelFacts(ownStats) + " / 비교대상 " + channelFacts(competitorStats);
        if (label.startsWith("기본")) return "제목 키워드, 설명 첫 부분, 썸네일 기본 신호";
        return "채팅 참여율과 좋아요 반응은 점수와 분리해 참고합니다.";
    }

    private static String reactionQualityText(VideoInfo ownVideo, VideoInfo competitorVideo, String competitorLabel) {
        return "내 좋아요 반응 " + ratioText(ownVideo.likeCount, ownVideo.viewCount)
            + " / " + competitorLabel + " 좋아요 반응 " + ratioText(competitorVideo.likeCount, competitorVideo.viewCount);
    }

    private static String tipTitle(String label) {
        if (label.startsWith("트래픽")) return "트래픽 질량 부족";
        if (label.startsWith("채널")) return "채널 영향 부족";
        if (label.startsWith("기본")) return "기본 최적화 부족";
        return "반응 품질 참고";
    }

    private static String tipBody(String label, int gap, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "검색 키워드" : keyword.trim();
        if (label.startsWith("트래픽")) return "현재 시청자와 순위 접근도가 비교 대상보다 " + gap + "점 낮음. 방송 시작 직후 실시청자 유입 확보가 필요합니다.";
        if (label.startsWith("채널")) return "채널 규모와 운영 기반 신호가 비교 대상보다 " + gap + "점 낮음. 같은 키워드 방송을 꾸준히 쌓아야 합니다.";
        if (label.startsWith("기본")) return "제목·설명·썸네일 기본 신호가 비교 대상보다 " + gap + "점 낮음. [" + cleanKeyword + "]가 앞부분에 보이게 정리하세요.";
        return "반응 품질은 총점과 분리해 참고하세요.";
    }

    private static String shortAction(String label, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "핵심 키워드" : keyword.trim();
        if (label.startsWith("트래픽")) return "방송 시작 직후 유입 동선을 점검하고 고정 멘트로 시청 이유를 제시하기";
        if (label.startsWith("채널")) return "다음 방송 예고와 구독 이유를 고정 멘트로 넣기";
        if (label.startsWith("기본")) return "제목 앞 20자와 설명 첫 2줄에 [" + cleanKeyword + "]와 오늘의 차별점 넣기";
        return "채팅 참여율과 좋아요 반응은 참고 지표로만 확인하기";
    }

    private static String detailReport(
        ComparisonReport report,
        VideoInfo ownVideo,
        VideoInfo competitorVideo,
        ChannelStats ownStats,
        ChannelStats competitorStats,
        String[] labels,
        int[] ownScores,
        int[] competitorScores,
        int[] gaps,
        int[] weights
    ) {
        int overallGap = report.competitorScore - report.ownScore;
        StringBuilder builder = new StringBuilder();
        builder.append("유튜브 라이브 경쟁력 진단\n");
        builder.append("리포트 버전: work6-channel-analysis\n");
        builder.append(report.comparisonTitle).append("\n");
        builder.append("================================\n");
        builder.append("키워드: ").append(report.keyword).append("\n");
        builder.append("내 채널: ").append(report.ownChannelTitle).append("\n");
        builder.append("노필터 라이브 1위: ").append(report.competitorChannelTitle).append("\n\n");

        builder.append("종합 점수\n");
        builder.append("- 내 채널: ").append(report.ownScore).append("점(").append(report.ownGrade).append(")\n");
        builder.append("- 1위 채널: ").append(report.competitorScore).append("점(").append(report.competitorGrade).append(")\n");
        builder.append("- 격차: ").append(overallGap > 0 ? "-" + overallGap : "+" + Math.abs(overallGap)).append("점\n\n");

        builder.append("결론\n");
        builder.append("- ").append(report.summary).append("\n");
        if (overallGap > 0) {
            builder.append("- 현재 기준으로 1위 채널이 ").append(overallGap).append("점 앞섭니다. 아래 약점부터 고치면 1위 가능성을 높일 수 있습니다.\n");
        } else if (overallGap < 0) {
            builder.append("- 현재 기준으로 내 채널이 ").append(Math.abs(overallGap)).append("점 앞섭니다. 강점은 유지하고 낮은 항목만 보강하세요.\n");
        } else {
            builder.append("- 종합 점수는 비슷합니다. 작은 차이를 만드는 제목, 설명, 초반 반응을 우선 점검하세요.\n");
        }
        builder.append("- 이 리포트는 보장이 아니라 공개 API 기준 진단입니다.\n\n");

        builder.append("이번에 비교한 것\n");
        builder.append("1. 트래픽 질량 (60점 묶음): 현재 시청자, 노필터 순위 접근도, 구독자 대비 현재 시청자 효율\n");
        builder.append("2. 채널 영향 (30점 묶음): 구독자 규모, 채널 누적 조회/영상 기반, 채널 정보 확인성\n");
        builder.append("3. 기본 최적화 (10점 묶음): 제목, 설명, 썸네일 기본 신호\n");
        builder.append("4. 세부 점수표: 위 3대 묶음을 7개 하위 항목으로 펼쳐 표시\n");
        builder.append("5. 반응 품질: 채팅 참여율과 좋아요 반응은 점수와 분리해 참고\n\n");

        builder.append("실제 비교 대상\n");
        builder.append("- 내 제목: ").append(safe(ownVideo.title)).append("\n");
        builder.append("- 1위 제목: ").append(safe(competitorVideo.title)).append("\n");
        builder.append("- 내 현재 시청자/조회수: ").append(metricText(ownVideo)).append("\n");
        builder.append("- 1위 현재 시청자/조회수: ").append(metricText(competitorVideo)).append("\n");
        builder.append("- 내 구독자 수: ").append(numberText(ownStats.subscriberCount)).append("\n");
        builder.append("- 1위 구독자 수: ").append(numberText(competitorStats.subscriberCount)).append("\n\n");

        appendCoreDiagnosis(builder, labels, ownScores, competitorScores, gaps);

        builder.append("항목별 진단\n");
        for (int index = 0; index < labels.length; index += 1) {
            builder.append(index + 1).append(". ").append(displayLabel(labels[index])).append(": 내 ")
                .append(ownScores[index]).append("점 / 1위 ").append(competitorScores[index]).append("점 / ")
                .append(gapText(gaps[index])).append("\n");
            builder.append("   판정: ").append(statusText(gaps[index])).append("\n");
            builder.append("   근거: ").append(categoryEvidence(labels[index], report.keyword, ownVideo, competitorVideo, ownStats, competitorStats)).append("\n");
            builder.append("   의미: ").append(categoryReason(labels[index])).append("\n");
            builder.append("   할 일: ").append(categoryAction(labels[index], report.keyword)).append("\n");
        }

        builder.append("\n반응 품질 참고\n");
        builder.append("- 채팅 참여율: 라이브 채팅 데이터 연결 전 단계\n");
        builder.append("- 좋아요 반응: ").append(reactionQualityText(ownVideo, competitorVideo, report.competitorShortLabel)).append("\n");

        builder.append("\n개선 팁 (1위 따라잡기 전략)\n");
        builder.append(report.priorities).append("\n\n");
        builder.append("최종 해석\n");
        builder.append(finalInterpretation(overallGap, labels, gaps)).append("\n\n");
        builder.append("제목 개선 예시\n").append(report.titleSuggestion).append("\n\n");
        builder.append("주의\n");
        builder.append("- 1등 보장, 조회수 상승 보장으로 해석하면 안 됩니다.\n");
        builder.append("- YouTube 내부 알고리즘, CTR, 노출수, 평균 시청 시간은 현재 리포트에 포함되지 않습니다.\n");
        builder.append("- 썸네일 이미지는 API로 정밀 판독하지 못하므로 화면에서 직접 확인해야 합니다.");
        return builder.toString();
    }

    private static void appendCoreDiagnosis(StringBuilder builder, String[] labels, int[] ownScores, int[] competitorScores, int[] gaps) {
        int worst = -1;
        int second = -1;
        int best = -1;
        for (int index = 0; index < labels.length; index += 1) {
            if (gaps[index] > 0 && (worst < 0 || gaps[index] > gaps[worst])) {
                second = worst;
                worst = index;
            } else if (gaps[index] > 0 && (second < 0 || gaps[index] > gaps[second])) {
                second = index;
            }
            if (gaps[index] < 0 && (best < 0 || gaps[index] < gaps[best])) {
                best = index;
            }
        }
        builder.append("핵심 진단\n");
        if (best >= 0) {
            builder.append("- 강점: ").append(displayLabel(labels[best])).append("은 내 채널이 ")
                .append(Math.abs(gaps[best])).append("점 앞섭니다.\n");
        } else {
            builder.append("- 강점: 현재 공개 지표상 1위보다 확실히 앞선 항목은 크지 않습니다.\n");
        }
        if (worst >= 0) {
            builder.append("- 치명적 격차: ").append(displayLabel(labels[worst])).append("에서 1위가 ")
                .append(gaps[worst]).append("점 앞섭니다.\n");
        }
        if (second >= 0) {
            builder.append("- 두 번째 약점: ").append(displayLabel(labels[second])).append("도 ")
                .append(gaps[second]).append("점 차이가 납니다.\n");
        }
        builder.append("- 결론: 점수를 보는 화면이 아니라, 위 약점부터 고치기 위한 실행 순서표입니다.\n\n");
    }

    private static String categoryReason(String label) {
        if (label.startsWith("트래픽")) return "현재 켜져 있는 방송의 시청자 규모와 순위 접근도가 채널 비교의 가장 큰 축입니다.";
        if (label.startsWith("채널")) return "같은 키워드라도 채널 체급과 운영 기반이 높으면 경쟁력이 안정적으로 쌓입니다.";
        if (label.startsWith("기본")) return "제목, 설명, 썸네일은 YouTube와 시청자가 방송 내용을 빠르게 이해하는 기본 신호입니다.";
        return "반응 품질은 총점과 분리해서 다음 방송 운영 참고로만 봅니다.";
    }

    private static String categoryAction(String label, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "핵심 키워드" : keyword.trim();
        if (label.startsWith("트래픽")) return "방송 시작 직후 유입 동선과 고정 멘트로 시청 이유를 분명히 제시하세요.";
        if (label.startsWith("채널")) return "같은 키워드 방송을 반복 편성하고, 다음 방송 예고와 구독 이유를 고정 멘트로 넣으세요.";
        if (label.startsWith("기본")) return "제목 앞 20자와 설명 첫 2줄에 '" + cleanKeyword + "', 오늘 다룰 내용, 참여 이유를 넣으세요.";
        return "채팅 참여율과 좋아요 반응은 점수와 분리해 다음 방송 운영에만 참고하세요.";
    }

    private static String displayLabel(String label) {
        if (label.startsWith("트래픽")) return label + " (트래픽 60)";
        if (label.startsWith("채널")) return label + " (채널 30)";
        if (label.startsWith("기본")) return label + " (기본 10)";
        return ComparisonCategory.REACTION_QUALITY.label + " (" + ComparisonCategory.REACTION_QUALITY.weightLabel + ")";
    }

    private static String statusText(int competitorMinusOwn) {
        if (competitorMinusOwn >= 20) return "핵심 약점";
        if (competitorMinusOwn >= 6) return "개선 권장";
        if (competitorMinusOwn <= -6) return "내 강점";
        return "동등";
    }

    private static String categoryEvidence(String label, String keyword, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats) {
        if (label.startsWith("트래픽")) {
            return "내 방송 " + trafficFacts(ownVideo, ownStats) + " / 1위 방송 " + trafficFacts(competitorVideo, competitorStats);
        }
        if (label.startsWith("채널")) {
            return "내 채널 " + channelFacts(ownStats) + " / 1위 채널 " + channelFacts(competitorStats);
        }
        if (label.startsWith("기본")) {
            return "내 기본 신호 " + basicFacts(ownVideo, keyword) + " / 1위 기본 신호 " + basicFacts(competitorVideo, keyword);
        }
        return reactionQualityText(ownVideo, competitorVideo, "1위");
    }

    private static String titleFacts(String title, String keyword) {
        String cleanTitle = title == null ? "" : title;
        String lowerTitle = cleanTitle.toLowerCase();
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        int position = lowerKeyword.length() > 0 ? lowerTitle.indexOf(lowerKeyword) : -1;
        int triggerCount = triggerCount(cleanTitle);
        String keywordText = position < 0 ? "키워드 앞부분 미확인" : "키워드 " + (position + 1) + "번째 위치";
        return keywordText + ", 길이 " + cleanTitle.length() + "자, 클릭 표현 " + triggerCount + "개";
    }

    private static int triggerCount(String title) {
        int count = 0;
        String lowerTitle = title == null ? "" : title.toLowerCase();
        String[] triggers = { "실시간", "라이브", "긴급", "속보", "오늘", "분석", "급등", "급락", "전략", "핵심", "LIVE" };
        for (String trigger : triggers) if (lowerTitle.contains(trigger.toLowerCase())) count += 1;
        return count;
    }

    private static String thumbnailFacts(String thumbnail) {
        if (thumbnail == null || thumbnail.length() == 0) return "이미지 확인 불가";
        return thumbnail.contains("default.jpg") ? "기본 썸네일 가능성 있음" : "썸네일 이미지 확인됨";
    }

    private static String metadataFacts(String description, int tagCount, String keyword) {
        String desc = description == null ? "" : description;
        String lower = desc.toLowerCase();
        String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        String first100 = lower.substring(0, Math.min(100, lower.length()));
        boolean hasKeyword = lowerKeyword.length() > 0 && first100.contains(lowerKeyword);
        int hashtags = 0;
        Matcher matcher = Pattern.compile("#[\\p{L}\\p{N}_-]+").matcher(desc);
        while (matcher.find()) hashtags += 1;
        return "첫 100자 키워드 " + (hasKeyword ? "있음" : "부족") + ", 설명 " + desc.length() + "자, 해시태그 " + hashtags + "개, 태그 " + tagCount + "개";
    }

    private static String basicFacts(VideoInfo video, String keyword) {
        return titleFacts(video.title, keyword) + ", " + metadataFacts(video.description, video.tagCount, keyword)
            + ", 썸네일 " + thumbnailFacts(video.thumbnail);
    }

    private static String trafficFacts(VideoInfo video, ChannelStats stats) {
        return metricText(video) + ", 구독자 대비 현재 시청자 효율 "
            + ccvRateText(video.currentViewers, stats == null ? null : stats.subscriberCount);
    }

    private static String channelFacts(ChannelStats stats) {
        return "구독자 " + numberText(stats == null ? null : stats.subscriberCount)
            + ", 누적 조회 " + numberText(stats == null ? null : stats.viewCount)
            + ", 영상 수 " + numberText(stats == null ? null : stats.videoCount);
    }

    private static String ccvRateText(Long currentViewers, Long subscribers) {
        if (currentViewers == null || subscribers == null || subscribers.longValue() <= 0) return "확인 불가";
        double percent = currentViewers.doubleValue() * 100.0 / subscribers.doubleValue();
        return String.format("%.2f%%", percent);
    }

    private static String engagementFacts(VideoInfo video) {
        return "좋아요 반응 " + ratioText(video.likeCount, video.viewCount);
    }

    private static String ratioText(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator.longValue() <= 0) return "확인 불가";
        double percent = numerator.doubleValue() * 100.0 / denominator.doubleValue();
        return String.format("%.2f%%", percent);
    }

    private static String finalInterpretation(int overallGap, String[] labels, int[] gaps) {
        int worst = -1;
        int second = -1;
        for (int index = 0; index < labels.length; index += 1) {
            if (gaps[index] <= 0) continue;
            if (worst < 0 || gaps[index] > gaps[worst]) {
                second = worst;
                worst = index;
            } else if (second < 0 || gaps[index] > gaps[second]) {
                second = index;
            }
        }
        StringBuilder builder = new StringBuilder();
        if (overallGap > 0) {
            builder.append("- 내 채널은 현재 1위보다 종합 경쟁력이 부족합니다.\n");
        } else {
            builder.append("- 내 채널은 종합 점수에서 밀리지 않지만, 세부 약점은 계속 보강해야 합니다.\n");
        }
        if (worst >= 0) {
            builder.append("- 1순위 원인은 ").append(displayLabel(labels[worst])).append("입니다.");
            if (second >= 0) builder.append(" 그다음은 ").append(displayLabel(labels[second])).append("입니다.");
            builder.append("\n");
        }
        builder.append("- 따라서 오늘의 목표는 모든 것을 고치는 것이 아니라, 우선순위 1~2개를 방송 제목/설명/초반 진행에 바로 반영하는 것입니다.");
        return builder.toString();
    }

    private static String gapText(int competitorMinusOwn) {
        if (competitorMinusOwn > 0) return "1위가 " + competitorMinusOwn + "점 앞섬";
        if (competitorMinusOwn < 0) return "내 채널이 " + Math.abs(competitorMinusOwn) + "점 앞섬";
        return "동점";
    }

    private static String metricText(VideoInfo video) {
        if (video.currentViewers != null) return "현재 시청자 " + numberText(video.currentViewers);
        if (video.viewCount != null) return "공개 조회수 " + numberText(video.viewCount);
        return "확인 불가";
    }

    private static String numberText(Long value) {
        return value == null ? "확인 불가" : String.valueOf(value.longValue());
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "확인 불가" : value;
    }
}
