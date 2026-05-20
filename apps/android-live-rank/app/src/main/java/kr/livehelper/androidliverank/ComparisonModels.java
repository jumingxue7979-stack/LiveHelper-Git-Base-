package kr.livehelper.androidliverank;

final class VideoInfo {
    String videoId = "";
    String title = "";
    String description = "";
    String channelId = "";
    String channelTitle = "";
    String thumbnail = "";
    int tagCount = 0;
    Long viewCount;
    Long likeCount;
    Long currentViewers;
}

final class ChannelStats {
    String channelId = "";
    String channelTitle = "";
    Long subscriberCount;
    Long viewCount;
    Long videoCount;
}

final class ComparisonCategory {
    static final String TRAFFIC_MASS_ID = "traffic_mass";
    static final String CHANNEL_INFLUENCE_ID = "channel_influence";
    static final String BASIC_OPTIMIZATION_ID = "basic_optimization";
    static final String REACTION_QUALITY_ID = "reaction_quality";

    static final ComparisonCategory TRAFFIC_MASS =
        new ComparisonCategory(TRAFFIC_MASS_ID, "트래픽 질량", "60점 묶음", true);
    static final ComparisonCategory CHANNEL_INFLUENCE =
        new ComparisonCategory(CHANNEL_INFLUENCE_ID, "채널 영향", "30점 묶음", true);
    static final ComparisonCategory BASIC_OPTIMIZATION =
        new ComparisonCategory(BASIC_OPTIMIZATION_ID, "기본 최적화", "10점 묶음", true);
    static final ComparisonCategory REACTION_QUALITY =
        new ComparisonCategory(REACTION_QUALITY_ID, "반응 품질", "점수 분리", false);

    static final ComparisonCategory[] SCORE_CATEGORIES = {
        TRAFFIC_MASS,
        CHANNEL_INFLUENCE,
        BASIC_OPTIMIZATION
    };

    final String id;
    final String label;
    final String weightLabel;
    final boolean scored;

    private ComparisonCategory(String id, String label, String weightLabel, boolean scored) {
        this.id = id;
        this.label = label;
        this.weightLabel = weightLabel;
        this.scored = scored;
    }
}
