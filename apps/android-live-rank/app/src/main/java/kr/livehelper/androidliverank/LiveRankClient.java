package kr.livehelper.androidliverank;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveRankClient {
    private final String apiKey;
    private String cachedChannelInput = "";
    private Channel cachedChannel;

    public LiveRankClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Result fetchLiveRank(String channelInput, String keyword) throws Exception {
        return fetchLiveRankForVideo(channelInput, keyword, "");
    }

    public Result fetchLiveRankForVideo(String channelInput, String keyword, String targetVideoId) throws Exception {
        Channel channel = resolveChannel(channelInput);
        Result result = new Result();
        result.keyword = keyword == null ? "" : keyword.trim();
        result.channelId = channel.id;
        result.channelTitle = channel.title;
        result.targetVideoId = targetVideoId == null ? "" : targetVideoId.trim();
        applySearchResult(result, fetchSearch(keyword, false), false);
        applySearchResult(result, fetchSearch(keyword, true), true);
        return result;
    }

    public ActiveLiveStatus inspectActiveLive(String channelInput) throws Exception {
        Channel channel = resolveChannel(channelInput);
        ActiveLiveStatus status = new ActiveLiveStatus();
        status.channelId = channel.id;
        status.channelTitle = channel.title;

        JSONObject payload = getJson("search", query(new String[][] {
            {"part", "snippet"},
            {"channelId", channel.id},
            {"type", "video"},
            {"eventType", "live"},
            {"maxResults", "5"},
            {"order", "date"},
            {"safeSearch", "none"},
            {"fields", "items(id/videoId,snippet(title,channelId,channelTitle,liveBroadcastContent))"}
        }));
        JSONArray items = payload.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return status;
        }

        for (int index = 0; index < items.length(); index += 1) {
            JSONObject item = items.getJSONObject(index);
            JSONObject snippet = item.optJSONObject("snippet");
            if (snippet == null) continue;
            if (channel.id.equals(snippet.optString("channelId", ""))
                && "live".equals(snippet.optString("liveBroadcastContent", ""))) {
                status.count += 1;
                if (status.firstVideoId.length() == 0) {
                    status.firstVideoId = videoId(item);
                    status.firstTitle = snippet.optString("title", "");
                }
            }
        }

        return status;
    }

    public boolean isVideoLive(String videoId) throws Exception {
        String cleanVideoId = videoId == null ? "" : videoId.trim();
        if (cleanVideoId.length() == 0) return false;

        JSONObject payload = getJson("videos", query(new String[][] {
            {"part", "snippet,liveStreamingDetails"},
            {"id", cleanVideoId},
            {"maxResults", "1"},
            {"fields", "items(id,snippet(liveBroadcastContent),liveStreamingDetails(actualEndTime))"}
        }));
        JSONArray items = payload.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return false;
        }

        JSONObject snippet = items.getJSONObject(0).optJSONObject("snippet");
        JSONObject live = items.getJSONObject(0).optJSONObject("liveStreamingDetails");
        if (live != null && live.optString("actualEndTime", "").length() > 0) {
            return false;
        }
        return snippet != null && "live".equals(snippet.optString("liveBroadcastContent", ""));
    }

    public boolean hasActiveLive(String channelId) throws Exception {
        String cleanChannelId = channelId == null ? "" : channelId.trim();
        if (cleanChannelId.length() == 0) return false;

        JSONObject payload = getJson("search", query(new String[][] {
            {"part", "snippet"},
            {"channelId", cleanChannelId},
            {"type", "video"},
            {"eventType", "live"},
            {"maxResults", "1"},
            {"order", "date"},
            {"safeSearch", "none"},
            {"fields", "items(id/videoId,snippet(channelId,liveBroadcastContent))"}
        }));
        JSONArray items = payload.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return false;
        }

        for (int index = 0; index < items.length(); index += 1) {
            JSONObject item = items.getJSONObject(index);
            JSONObject snippet = item.optJSONObject("snippet");
            if (snippet == null) continue;
            if (cleanChannelId.equals(snippet.optString("channelId", ""))
                && "live".equals(snippet.optString("liveBroadcastContent", ""))) {
                return true;
            }
        }
        return false;
    }

    public ComparisonReport fetchComparisonReport(String channelInput, String keyword, String targetVideoId) throws Exception {
        Channel ownChannel = resolveChannel(channelInput);
        JSONObject searchPayload = fetchSearch(keyword, false);
        JSONArray items = searchPayload.optJSONArray("items");
        String ownVideoId = targetVideoId == null ? "" : targetVideoId.trim();
        JSONObject ownSearchItem = null;
        JSONObject ownAnySearchItem = null;
        JSONObject competitorSearchItem = null;
        int ownNoFilterRank = -1;
        int ownAnyNoFilterRank = -1;
        int competitorNoFilterRank = -1;
        int noFilterLiveCount = 0;
        boolean ownNoFilterLive = false;

        if (items != null) {
            for (int index = 0; index < items.length(); index += 1) {
                JSONObject item = items.getJSONObject(index);
                JSONObject snippet = item.optJSONObject("snippet");
                if (snippet == null) continue;
                int noFilterRank = index + 1;
                String itemVideoId = videoId(item);
                boolean isLive = "live".equals(snippet.optString("liveBroadcastContent", ""));
                if (isLive) noFilterLiveCount += 1;
                if (ownChannel.id.equals(snippet.optString("channelId", ""))) {
                    if (ownAnySearchItem == null || itemVideoId.equals(ownVideoId)) {
                        ownAnySearchItem = item;
                        ownAnyNoFilterRank = noFilterRank;
                        if (ownVideoId.length() == 0) ownVideoId = itemVideoId;
                    }
                    if (isLive) {
                        ownNoFilterLive = true;
                        if (ownSearchItem == null || itemVideoId.equals(ownVideoId)) {
                            ownSearchItem = item;
                            ownNoFilterRank = noFilterRank;
                            if (ownVideoId.length() == 0) ownVideoId = itemVideoId;
                        }
                    }
                }
                if (isLive && !ownChannel.id.equals(snippet.optString("channelId", "")) && competitorSearchItem == null) {
                    competitorSearchItem = item;
                    competitorNoFilterRank = noFilterRank;
                }
            }
        }

        if (ownSearchItem == null && ownAnySearchItem != null) {
            ownSearchItem = ownAnySearchItem;
            ownVideoId = videoId(ownAnySearchItem);
            if (ownNoFilterRank < 0) ownNoFilterRank = ownAnyNoFilterRank;
        }

        ComparisonReport report = new ComparisonReport();
        report.keyword = keyword == null ? "" : keyword.trim();
        report.ownChannelTitle = ownChannel.title;
        if (competitorSearchItem == null) {
            String summary;
            if (noFilterLiveCount == 0) {
                summary = "노필터 검색 상위권에 실시간 라이브가 없어 비교 대상 산정을 건너뛰었습니다. 라이브 필터 결과는 참고만 합니다.";
            } else if (ownNoFilterLive) {
                summary = "노필터 검색 상위권의 실시간 라이브가 내 방송뿐이라 경쟁 채널 비교를 건너뛰었습니다.";
            } else {
                summary = "노필터 검색 상위권에서 비교 가능한 실시간 라이브 경쟁 채널을 찾지 못했습니다.";
            }
            return ComparisonAnalyzer.empty(
                report.keyword,
                ownChannel.title,
                summary,
                ownNoFilterRank
            );
        }

        String competitorVideoId = videoId(competitorSearchItem);
        Map<String, VideoInfo> videos = fetchVideoDetails(new String[] { ownVideoId, competitorVideoId });
        VideoInfo ownVideo = videos.containsKey(ownVideoId) ? videos.get(ownVideoId) : videoFromSearch(ownSearchItem);
        VideoInfo competitorVideo = videos.containsKey(competitorVideoId) ? videos.get(competitorVideoId) : videoFromSearch(competitorSearchItem);
        Map<String, ChannelStats> channels = fetchChannelStats(new String[] { ownChannel.id, competitorVideo.channelId });
        ChannelStats ownStats = channels.containsKey(ownChannel.id) ? channels.get(ownChannel.id) : new ChannelStats();
        ChannelStats competitorStats = channels.containsKey(competitorVideo.channelId) ? channels.get(competitorVideo.channelId) : new ChannelStats();

        return ComparisonAnalyzer.build(report.keyword, ownChannel.title, ownVideo, competitorVideo, ownStats, competitorStats, ownNoFilterRank, competitorNoFilterRank);
    }

    private JSONObject fetchSearch(String keyword, boolean liveOnly) throws Exception {
        return getJson("search", query(new String[][] {
            {"part", "snippet"},
            {"q", keyword},
            {"type", "video"},
            {"maxResults", "20"},
            {"order", "relevance"},
            {"safeSearch", "none"},
            {"eventType", liveOnly ? "live" : ""},
            {"regionCode", "KR"},
            {"relevanceLanguage", "ko"},
            {"fields", "items(id/videoId,snippet(title,channelId,channelTitle,liveBroadcastContent))"}
        }));
    }

    private Map<String, VideoInfo> fetchVideoDetails(String[] videoIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (String videoId : videoIds) {
            if (videoId == null || videoId.trim().length() == 0) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(videoId.trim());
        }
        Map<String, VideoInfo> map = new HashMap<>();
        if (ids.length() == 0) return map;

        JSONObject payload = getJson("videos", query(new String[][] {
            {"part", "snippet,statistics,liveStreamingDetails"},
            {"id", ids.toString()},
            {"fields", "items(id,snippet(title,description,tags,channelId,channelTitle,liveBroadcastContent,thumbnails),statistics(viewCount,likeCount,commentCount),liveStreamingDetails(concurrentViewers,actualStartTime))"}
        }));
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return map;
        for (int index = 0; index < items.length(); index += 1) {
            JSONObject item = items.getJSONObject(index);
            VideoInfo info = videoFromDetail(item);
            map.put(info.videoId, info);
        }
        return map;
    }

    private Map<String, ChannelStats> fetchChannelStats(String[] channelIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (String channelId : channelIds) {
            if (channelId == null || channelId.trim().length() == 0) continue;
            if (ids.indexOf(channelId.trim()) >= 0) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(channelId.trim());
        }
        Map<String, ChannelStats> map = new HashMap<>();
        if (ids.length() == 0) return map;

        JSONObject payload = getJson("channels", query(new String[][] {
            {"part", "snippet,statistics"},
            {"id", ids.toString()},
            {"fields", "items(id,snippet(title,publishedAt),statistics(subscriberCount,hiddenSubscriberCount,videoCount,viewCount))"}
        }));
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return map;
        for (int index = 0; index < items.length(); index += 1) {
            JSONObject item = items.getJSONObject(index);
            JSONObject snippet = item.optJSONObject("snippet");
            JSONObject stats = item.optJSONObject("statistics");
            ChannelStats info = new ChannelStats();
            info.channelId = item.optString("id", "");
            info.channelTitle = snippet == null ? "" : snippet.optString("title", "");
            info.subscriberCount = stats == null || stats.optBoolean("hiddenSubscriberCount", false) ? null : optLong(stats, "subscriberCount");
            map.put(info.channelId, info);
        }
        return map;
    }

    private VideoInfo videoFromSearch(JSONObject item) {
        VideoInfo info = new VideoInfo();
        if (item == null) return info;
        JSONObject snippet = item.optJSONObject("snippet");
        info.videoId = videoId(item);
        if (snippet != null) {
            info.title = snippet.optString("title", "");
            info.channelId = snippet.optString("channelId", "");
            info.channelTitle = snippet.optString("channelTitle", "");
            info.thumbnail = thumbnailUrl(snippet);
        }
        return info;
    }

    private VideoInfo videoFromDetail(JSONObject item) {
        VideoInfo info = new VideoInfo();
        JSONObject snippet = item.optJSONObject("snippet");
        JSONObject stats = item.optJSONObject("statistics");
        JSONObject live = item.optJSONObject("liveStreamingDetails");
        info.videoId = item.optString("id", "");
        if (snippet != null) {
            info.title = snippet.optString("title", "");
            info.description = snippet.optString("description", "");
            info.channelId = snippet.optString("channelId", "");
            info.channelTitle = snippet.optString("channelTitle", "");
            info.thumbnail = thumbnailUrl(snippet);
            JSONArray tags = snippet.optJSONArray("tags");
            info.tagCount = tags == null ? 0 : tags.length();
        }
        if (stats != null) {
            info.viewCount = optLong(stats, "viewCount");
            info.likeCount = optLong(stats, "likeCount");
            info.commentCount = optLong(stats, "commentCount");
        }
        if (live != null) {
            info.currentViewers = optLong(live, "concurrentViewers");
        }
        return info;
    }

    private Long optLong(JSONObject object, String key) {
        if (object == null || !object.has(key)) return null;
        try {
            return Long.valueOf(object.optString(key, "0"));
        } catch (Exception error) {
            return null;
        }
    }

    private String thumbnailUrl(JSONObject snippet) {
        JSONObject thumbs = snippet == null ? null : snippet.optJSONObject("thumbnails");
        if (thumbs == null) return "";
        String[] keys = { "maxres", "standard", "high", "medium", "default" };
        for (String key : keys) {
            JSONObject thumb = thumbs.optJSONObject(key);
            if (thumb != null && thumb.optString("url", "").length() > 0) return thumb.optString("url", "");
        }
        return "";
    }

    private void applySearchResult(Result result, JSONObject payload, boolean liveOnly) throws Exception {
        JSONArray items = payload.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return;
        }

        JSONObject topSnippet = items.getJSONObject(0).optJSONObject("snippet");
        if (topSnippet != null) {
            if (liveOnly) {
                result.topLiveChannelTitle = topSnippet.optString("channelTitle", "");
            } else {
                result.topNoFilterChannelTitle = topSnippet.optString("channelTitle", "");
            }
        }

        int fallbackRank = -1;
        JSONObject fallbackItem = null;
        for (int index = 0; index < items.length(); index += 1) {
            JSONObject item = items.getJSONObject(index);
            JSONObject snippet = item.optJSONObject("snippet");
            if (snippet == null) continue;
            String itemVideoId = videoId(item);
            boolean isTargetChannel = result.channelId.equals(snippet.optString("channelId", ""));
            boolean isLockedVideo = result.targetVideoId.length() > 0 && result.targetVideoId.equals(itemVideoId);
            boolean isSearchLive = liveOnly || "live".equals(snippet.optString("liveBroadcastContent", ""));
            boolean exactTarget = isTargetChannel && result.targetVideoId.length() > 0 && isLockedVideo;
            boolean channelLiveFallback = isTargetChannel && isSearchLive;

            if (exactTarget || (result.targetVideoId.length() == 0 && channelLiveFallback)) {
                applyMatchedSearchResult(result, item, liveOnly, index + 1);
                return;
            }

            // YouTube can expose the same live channel in no-filter search before the locked video id lines up.
            if (fallbackRank < 0 && channelLiveFallback) {
                fallbackRank = index + 1;
                fallbackItem = item;
            }
        }

        if (fallbackItem != null) {
            applyMatchedSearchResult(result, fallbackItem, liveOnly, fallbackRank);
        }
    }

    private void applyMatchedSearchResult(Result result, JSONObject item, boolean liveOnly, int rank) {
        JSONObject snippet = item.optJSONObject("snippet");
        if (snippet == null) return;
        if (liveOnly) {
            result.liveRank = rank;
        } else {
            result.noFilterRank = rank;
        }
        result.targetTitle = snippet.optString("title", "");
        result.targetVideoId = videoId(item);
        result.channelTitle = snippet.optString("channelTitle", result.channelTitle);
    }

    private String videoId(JSONObject item) {
        JSONObject id = item.optJSONObject("id");
        return id == null ? item.optString("id", "") : id.optString("videoId", "");
    }

    private Channel resolveChannel(String input) throws Exception {
        String cleanInput = input == null ? "" : input.trim();
        if (cachedChannel != null && cachedChannelInput.equals(cleanInput)) {
            return cachedChannel;
        }

        List<String[]> candidates = channelCandidates(input);
        if (candidates.size() == 0) {
            throw new Exception("채널 주소를 확인해 주세요.");
        }

        for (String[] candidate : candidates) {
            JSONObject payload = getJson("channels", query(new String[][] {
                {"part", "snippet"},
                {"maxResults", "1"},
                {"fields", "items(id,snippet(title,customUrl))"},
                {candidate[0], candidate[1]}
            }));
            JSONArray items = payload.optJSONArray("items");
            if (items != null && items.length() > 0) {
                JSONObject item = items.getJSONObject(0);
                JSONObject snippet = item.optJSONObject("snippet");
                Channel channel = new Channel();
                channel.id = item.optString("id", "");
                channel.title = snippet == null ? "" : snippet.optString("title", "");
                if (channel.id.length() > 0) {
                    cachedChannelInput = cleanInput;
                    cachedChannel = channel;
                    return channel;
                }
            }
        }

        throw new Exception("내 채널을 찾지 못했습니다.");
    }

    private List<String[]> channelCandidates(String value) throws Exception {
        String raw = value == null ? "" : value.trim();
        List<String[]> candidates = new ArrayList<>();
        if (raw.length() == 0) return candidates;

        Matcher channelUrl = Pattern.compile("youtube\\.com/channel/(UC[\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (channelUrl.find()) {
            addCandidate(candidates, "id", channelUrl.group(1));
            return candidates;
        }

        if (raw.matches("(?i)^UC[\\w-]{20,}$")) {
            addCandidate(candidates, "id", raw);
            return candidates;
        }

        Matcher handleUrl = Pattern.compile("youtube\\.com/@([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (handleUrl.find()) {
            addCandidate(candidates, "forHandle", "@" + handleUrl.group(1));
        }

        Matcher userUrl = Pattern.compile("youtube\\.com/(?:user|c)/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (userUrl.find()) {
            addCandidate(candidates, "forHandle", "@" + userUrl.group(1));
            addCandidate(candidates, "forUsername", userUrl.group(1));
        }

        if (raw.startsWith("@")) {
            addCandidate(candidates, "forHandle", raw);
        }

        if (candidates.size() == 0) {
            String clean = raw
                .replaceFirst("(?i)^https?://", "")
                .replaceFirst("(?i)^www\\.", "")
                .replaceFirst("(?i)^youtube\\.com/", "")
                .replaceFirst("^@", "")
                .split("[/?#]")[0]
                .trim();
            if (clean.length() > 0) {
                addCandidate(candidates, "forHandle", "@" + clean);
                addCandidate(candidates, "forUsername", clean);
            }
        }

        return candidates;
    }

    private void addCandidate(List<String[]> candidates, String key, String value) {
        for (String[] candidate : candidates) {
            if (candidate[0].equals(key) && candidate[1].equals(value)) return;
        }
        candidates.add(new String[] { key, value });
    }

    private String query(String[][] params) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String[] pair : params) {
            if (pair[1] == null || pair[1].trim().length() == 0) continue;
            if (builder.length() > 0) builder.append('&');
            builder.append(URLEncoder.encode(pair[0], "UTF-8"));
            builder.append('=');
            builder.append(URLEncoder.encode(pair[1], "UTF-8"));
        }
        if (builder.length() > 0) builder.append('&');
        builder.append("key=");
        builder.append(URLEncoder.encode(apiKey, "UTF-8"));
        return builder.toString();
    }

    private JSONObject getJson(String resource, String query) throws Exception {
        URL url = new URL("https://www.googleapis.com/youtube/v3/" + resource + "?" + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");

        int code = connection.getResponseCode();
        String body = readAll(code >= 200 && code < 300
            ? connection.getInputStream()
            : connection.getErrorStream());
        JSONObject payload = body.length() == 0 ? new JSONObject() : new JSONObject(body);
        if (code < 200 || code >= 300) {
            JSONObject error = payload.optJSONObject("error");
            String message = error == null ? "YouTube API 요청 실패" : error.optString("message", "YouTube API 요청 실패");
            throw new Exception(message);
        }
        return payload;
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private static final class Channel {
        String id = "";
        String title = "";
    }

    public static final class Result {
        public String keyword = "";
        public String channelId = "";
        public String channelTitle = "";
        public String targetVideoId = "";
        public String topNoFilterChannelTitle = "";
        public String topLiveChannelTitle = "";
        public String targetTitle = "";
        public int noFilterRank = -1;
        public int liveRank = -1;
    }

    public static final class ActiveLiveStatus {
        public String channelId = "";
        public String channelTitle = "";
        public String firstVideoId = "";
        public String firstTitle = "";
        public int count = 0;
    }
}
