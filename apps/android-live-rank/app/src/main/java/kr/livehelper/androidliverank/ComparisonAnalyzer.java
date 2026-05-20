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
        int[] currentPair = relativePair(
            ownVideo.currentViewers != null ? ownVideo.currentViewers : ownVideo.viewCount,
            competitorVideo.currentViewers != null ? competitorVideo.currentViewers : competitorVideo.viewCount
        );
        int[] subscriberPair = relativePair(ownStats.subscriberCount, competitorStats.subscriberCount);
        int ownAuthority = average(new int[] { subscriberPair[0], ccvRateScore(ownVideo.currentViewers, ownStats.subscriberCount) });
        int competitorAuthority = average(new int[] { subscriberPair[1], ccvRateScore(competitorVideo.currentViewers, competitorStats.subscriberCount) });
        int ownEngagement = engagementScore(ownVideo);
        int competitorEngagement = engagementScore(competitorVideo);
        int ownPublishing = 50;
        int competitorPublishing = 50;
        String[] labels = new String[] {
            "라이브 현재 성과", "채널 신뢰도", "메타데이터", "제목 최적화", "썸네일 기본 평가", "시청자 반응", "발행 전략"
        };
        int[] ownScores = new int[] {
            currentPair[0], ownAuthority, ownMeta, ownTitle, ownThumb, ownEngagement, ownPublishing
        };
        int[] competitorScores = new int[] {
            currentPair[1], competitorAuthority, competitorMeta, competitorTitle, competitorThumb, competitorEngagement, competitorPublishing
        };
        int[] gaps = new int[] {
            currentPair[1] - currentPair[0],
            competitorAuthority - ownAuthority,
            competitorMeta - ownMeta,
            competitorTitle - ownTitle,
            competitorThumb - ownThumb,
            competitorEngagement - ownEngagement,
            competitorPublishing - ownPublishing
        };
        int[] weights = new int[] { 25, 15, 10, 15, 10, 15, 10 };

        report.ownScore = weightedTotal(new int[] { ownTitle, ownThumb, ownMeta, currentPair[0], ownAuthority, ownEngagement, ownPublishing });
        report.competitorScore = weightedTotal(new int[] { competitorTitle, competitorThumb, competitorMeta, currentPair[1], competitorAuthority, competitorEngagement, competitorPublishing });
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

    private static int engagementScore(VideoInfo video) {
        int likeScore = ratioScore(video.likeCount, video.viewCount, new double[] { 0.05, 0.03, 0.01 }, new int[] { 100, 80, 60, 30 });
        int commentScore = ratioScore(video.commentCount, video.viewCount, new double[] { 0.01, 0.005, 0.001 }, new int[] { 100, 75, 50, 25 });
        return weightedTotal(new int[] { likeScore, commentScore }, new int[] { 40, 30 });
    }

    private static int ratioScore(Long numerator, Long denominator, double[] thresholds, int[] scores) {
        if (numerator == null || denominator == null || denominator.longValue() <= 0) return 0;
        double ratio = numerator.doubleValue() / denominator.doubleValue();
        for (int index = 0; index < thresholds.length; index += 1) {
            if (ratio >= thresholds[index]) return scores[index];
        }
        return scores[scores.length - 1];
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
            ? "큰 약점이 크게 잡히지 않았습니다.\n현재 점수표를 유지하면서 제목, 설명, 시청자 반응을 계속 점검하세요."
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

        builder.append("카테고리별 점수표\n");
        for (int index = 0; index < labels.length; index += 1) {
            builder.append(index + 1).append(". ").append(compactLabel(labels[index])).append("  ").append(statusText(gaps[index])).append("\n");
            builder.append("   내  ").append(scoreText(ownScores[index])).append(" ").append(scoreBar(ownScores[index])).append("\n");
            builder.append("   ").append(report.competitorShortLabel).append(" ").append(scoreText(competitorScores[index])).append(" ").append(scoreBar(competitorScores[index])).append("\n");
            builder.append("   핵심: ").append(compactEvidence(labels[index], report.keyword, ownVideo, competitorVideo, ownStats, competitorStats)).append("\n");
        }

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
        if (label.startsWith("제목")) return "키워드 위치와 클릭 표현 차이";
        if (label.startsWith("썸네일")) return "모바일 첫인상과 가독성 차이";
        if (label.startsWith("메타")) return "설명 첫 2줄, 해시태그, 키워드 매칭 차이";
        if (label.startsWith("라이브")) return "내 " + metricText(ownVideo) + " / 비교대상 " + metricText(competitorVideo);
        if (label.startsWith("채널")) return "내 구독자 " + numberText(ownStats.subscriberCount) + " / 비교대상 구독자 " + numberText(competitorStats.subscriberCount);
        if (label.startsWith("시청자")) return "좋아요율과 댓글률 차이";
        return "방송 시간과 반복 편성 안정성";
    }

    private static String tipTitle(String label) {
        if (label.startsWith("라이브")) return "시청자 수 부족";
        if (label.startsWith("채널")) return "채널 신뢰도 부족";
        if (label.startsWith("메타")) return "검색 노출 부족";
        if (label.startsWith("제목")) return "제목 최적화 부족";
        if (label.startsWith("썸네일")) return "썸네일 클릭 요소 부족";
        if (label.startsWith("시청자")) return "시청자 참여 부족";
        return "방송 시간대 보강";
    }

    private static String tipBody(String label, int gap, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "검색 키워드" : keyword.trim();
        if (label.startsWith("라이브")) return "라이브 초반 트래픽이 1위 대비 " + gap + "점 낮음. 방송 시작 직후 실시청자 유입 확보가 필요합니다.";
        if (label.startsWith("채널")) return "채널 SEO 및 구독자 규모·운영 기간이 1위보다 " + gap + "점 낮음. 꾸준한 라이브 방송으로 채널 최적화가 필요합니다.";
        if (label.startsWith("메타")) return "메타데이터·키워드 매칭이 1위보다 " + gap + "점 낮음. 제목·태그·설명에 [" + cleanKeyword + "] 관련 검색어 보강이 필요합니다.";
        if (label.startsWith("제목")) return "제목 검색 노출력이 1위보다 " + gap + "점 낮음. 제목 앞부분에 [" + cleanKeyword + "]와 방송 내용을 넣으세요.";
        if (label.startsWith("썸네일")) return "썸네일 클릭 유도가 1위보다 " + gap + "점 낮음. 제목과 맞는 큰 글자와 핵심 장면을 보여주세요.";
        if (label.startsWith("시청자")) return "댓글·좋아요 참여가 1위보다 " + gap + "점 낮음. 진행 멘트와 고정댓글로 참여를 요청하세요.";
        return "방송 시간대 재방문 신호가 1위보다 " + gap + "점 낮음. 같은 요일과 시간대 반복 방송이 필요합니다.";
    }

    private static String shortAction(String label, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "핵심 키워드" : keyword.trim();
        if (label.startsWith("제목")) return "제목 앞 20자 안에 [" + cleanKeyword + "]와 오늘의 차별점을 넣기";
        if (label.startsWith("썸네일")) return "큰 글자 3~5단어와 핵심 장면이 바로 보이게 바꾸기";
        if (label.startsWith("메타")) return "설명 첫 2줄에 키워드, 방송 내용, 얻는 이득 넣기";
        if (label.startsWith("라이브")) return "초반 10분 질문/이벤트/고정댓글로 반응 끌어올리기";
        if (label.startsWith("채널")) return "다음 방송 예고와 구독 이유를 고정 멘트로 넣기";
        if (label.startsWith("시청자")) return "좋아요/댓글 요청 문장을 화면과 진행 멘트에 넣기";
        return "같은 요일과 시간대에 반복 방송하기";
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
        builder.append("1. 시청자 수 (초반 트래픽): 현재 시청자 또는 공개 조회수 상대 비교\n");
        builder.append("2. 채널 SEO 및 (구독자 규모·운영 기간): 구독자 수와 채널 반응 신호\n");
        builder.append("3. 검색 노출 (메타·키워드): 설명 첫 부분, 해시태그, 태그 수\n");
        builder.append("4. 제목최적화 (검색 노출력): 키워드 포함, 앞부분 배치, 길이, 클릭 이유\n");
        builder.append("5. 썸네일 제목과일치 (클릭 유도): API로 확인 가능한 썸네일 존재와 기본 품질 신호\n");
        builder.append("6. 시청자 참여 (댓글·좋아요): 좋아요/댓글 비율\n");
        builder.append("7. 방송 시간대 (재방문율): 이번 모바일 버전은 기본값으로 두고 다음 고도화 대상\n\n");

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
        if (label.startsWith("제목")) return "검색 결과에서 가장 먼저 보이는 신호라 키워드 위치와 클릭 이유가 순위 진입에 중요합니다.";
        if (label.startsWith("썸네일")) return "모바일 검색에서는 썸네일이 클릭 여부를 크게 좌우하지만, 현재는 API로 확인 가능한 기본 신호만 봅니다.";
        if (label.startsWith("메타")) return "설명 앞부분과 해시태그는 YouTube가 방송 내용을 이해하는 보조 신호입니다.";
        if (label.startsWith("라이브")) return "지금 켜져 있는 방송의 시청자/조회수 신호가 강하면 노필터 상위 라이브와의 격차가 줄어듭니다.";
        if (label.startsWith("채널")) return "같은 키워드라도 채널 체급과 구독자 대비 반응이 높으면 신뢰 신호가 좋아집니다.";
        if (label.startsWith("시청자")) return "좋아요와 댓글은 방송 반응이 살아 있는지 보여주는 공개 신호입니다.";
        return "정해진 시간대와 반복 방송 패턴은 장기적으로 검색/구독자 재방문에 도움이 됩니다.";
    }

    private static String categoryAction(String label, String keyword) {
        String cleanKeyword = keyword == null || keyword.trim().length() == 0 ? "핵심 키워드" : keyword.trim();
        if (label.startsWith("제목")) return "제목 앞 20자 안에 '" + cleanKeyword + "'와 오늘 방송의 차별점을 넣으세요.";
        if (label.startsWith("썸네일")) return "모바일에서 한눈에 보이는 큰 글자 3~5단어, 상품/얼굴/핵심 장면을 분명히 확인하세요.";
        if (label.startsWith("메타")) return "설명 첫 2줄에 '" + cleanKeyword + "', 오늘 다룰 내용, 참여 이유를 넣고 해시태그 3~5개로 정리하세요.";
        if (label.startsWith("라이브")) return "방송 초반 10분에 질문, 이벤트, 고정댓글로 좋아요/채팅/체류를 먼저 끌어올리세요.";
        if (label.startsWith("채널")) return "같은 키워드 방송을 반복 편성하고, 다음 방송 예고와 구독 이유를 고정 멘트로 넣으세요.";
        if (label.startsWith("시청자")) return "좋아요와 댓글을 요청하는 문장을 방송 화면/고정댓글/진행 멘트에 넣으세요.";
        return "가능하면 같은 요일과 비슷한 시간대에 반복 방송해 재방문 패턴을 만드세요.";
    }

    private static String displayLabel(String label) {
        if (label.startsWith("라이브")) return "시청자 수 (초반 트래픽)";
        if (label.startsWith("채널")) return "채널 SEO 및 (구독자 규모·운영 기간)";
        if (label.startsWith("메타")) return "검색 노출 (메타·키워드)";
        if (label.startsWith("제목")) return "제목최적화 (검색 노출력)";
        if (label.startsWith("썸네일")) return "썸네일 제목과일치 (클릭 유도)";
        if (label.startsWith("시청자")) return "시청자 참여 (댓글·좋아요)";
        return "방송 시간대 (재방문율)";
    }

    private static String statusText(int competitorMinusOwn) {
        if (competitorMinusOwn >= 20) return "핵심 약점";
        if (competitorMinusOwn >= 6) return "개선 권장";
        if (competitorMinusOwn <= -6) return "내 강점";
        return "동등";
    }

    private static String categoryEvidence(String label, String keyword, VideoInfo ownVideo, VideoInfo competitorVideo, ChannelStats ownStats, ChannelStats competitorStats) {
        if (label.startsWith("제목")) {
            return "내 제목 " + titleFacts(ownVideo.title, keyword) + " / 1위 제목 " + titleFacts(competitorVideo.title, keyword);
        }
        if (label.startsWith("썸네일")) {
            return "내 썸네일 " + thumbnailFacts(ownVideo.thumbnail) + " / 1위 썸네일 " + thumbnailFacts(competitorVideo.thumbnail);
        }
        if (label.startsWith("메타")) {
            return "내 설명 " + metadataFacts(ownVideo.description, ownVideo.tagCount, keyword) + " / 1위 설명 " + metadataFacts(competitorVideo.description, competitorVideo.tagCount, keyword);
        }
        if (label.startsWith("라이브")) {
            return "내 방송 " + liveFacts(ownVideo, ownStats) + " / 1위 방송 " + liveFacts(competitorVideo, competitorStats);
        }
        if (label.startsWith("채널")) {
            return "내 채널 구독자 " + numberText(ownStats.subscriberCount) + ", 구독자 대비 반응 " + ccvRateText(ownVideo.currentViewers, ownStats.subscriberCount)
                + " / 1위 구독자 " + numberText(competitorStats.subscriberCount) + ", 구독자 대비 반응 " + ccvRateText(competitorVideo.currentViewers, competitorStats.subscriberCount);
        }
        if (label.startsWith("시청자")) {
            return "내 반응 " + engagementFacts(ownVideo) + " / 1위 반응 " + engagementFacts(competitorVideo);
        }
        return "이번 모바일 버전은 반복 방송 시간대 상세 분석 전 단계라 기본값으로 비교했습니다.";
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

    private static String liveFacts(VideoInfo video, ChannelStats stats) {
        return metricText(video) + ", 구독자 대비 현재 반응 " + ccvRateText(video.currentViewers, stats.subscriberCount);
    }

    private static String ccvRateText(Long currentViewers, Long subscribers) {
        if (currentViewers == null || subscribers == null || subscribers.longValue() <= 0) return "확인 불가";
        double percent = currentViewers.doubleValue() * 100.0 / subscribers.doubleValue();
        return String.format("%.2f%%", percent);
    }

    private static String engagementFacts(VideoInfo video) {
        return "좋아요율 " + ratioText(video.likeCount, video.viewCount) + ", 댓글률 " + ratioText(video.commentCount, video.viewCount);
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
