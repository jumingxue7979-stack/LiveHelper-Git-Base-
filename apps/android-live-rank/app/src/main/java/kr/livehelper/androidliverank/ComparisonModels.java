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
    Long commentCount;
    Long currentViewers;
}

final class ChannelStats {
    String channelId = "";
    String channelTitle = "";
    Long subscriberCount;
}
