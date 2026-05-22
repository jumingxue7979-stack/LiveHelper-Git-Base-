using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;

namespace LiveHelperWindowsObsRank
{
    internal sealed class YoutubeClient
    {
        private readonly string apiKey;
        private readonly JavaScriptSerializer serializer = new JavaScriptSerializer();
        private string cachedChannelInput = "";
        private ChannelInfo cachedChannel;

        public YoutubeClient(string apiKey)
        {
            this.apiKey = apiKey == null ? "" : apiKey.Trim();
        }

        public ActiveLiveStatus InspectActiveLive(string channelInput)
        {
            ChannelInfo channel = ResolveChannel(channelInput);
            ActiveLiveStatus status = new ActiveLiveStatus();
            status.ChannelId = channel.Id;
            status.ChannelTitle = channel.Title;

            Dictionary<string, object> payload = GetJson("search", Query(new Dictionary<string, string>
            {
                {"part", "snippet"},
                {"channelId", channel.Id},
                {"type", "video"},
                {"eventType", "live"},
                {"maxResults", "5"},
                {"order", "date"},
                {"safeSearch", "none"},
                {"fields", "items(id/videoId,snippet(title,channelId,channelTitle,liveBroadcastContent))"}
            }));

            foreach (Dictionary<string, object> item in Items(payload))
            {
                Dictionary<string, object> snippet = Dict(item, "snippet");
                if (Str(snippet, "channelId") == channel.Id)
                {
                    status.Count += 1;
                    if (status.FirstVideoId.Length == 0)
                    {
                        status.FirstVideoId = VideoId(item);
                        status.FirstTitle = Str(snippet, "title");
                    }
                }
            }
            return status;
        }

        public ActiveLiveStatus InspectOwnLiveFromKeyword(string channelInput, string keyword)
        {
            ChannelInfo channel = ResolveChannel(channelInput);
            ActiveLiveStatus status = new ActiveLiveStatus();
            status.ChannelId = channel.Id;
            status.ChannelTitle = channel.Title;

            Dictionary<string, bool> seen = new Dictionary<string, bool>();
            AddOwnLivesFromSearch(status, seen, FetchSearch(keyword, true), true);
            AddOwnLivesFromSearch(status, seen, FetchSearch(keyword, false), false);
            return status;
        }

        public RankResult FetchLiveRankForVideo(string channelInput, string keyword, string targetVideoId)
        {
            ChannelInfo channel = ResolveChannel(channelInput);
            RankResult result = new RankResult();
            result.Keyword = keyword == null ? "" : keyword.Trim();
            result.ChannelId = channel.Id;
            result.ChannelTitle = channel.Title;
            result.TargetVideoId = targetVideoId == null ? "" : targetVideoId.Trim();

            ApplySearchResult(result, FetchSearch(result.Keyword, false), false);
            ApplySearchResult(result, FetchSearch(result.Keyword, true), true);
            return result;
        }

        public ComparisonReport FetchComparisonReport(string channelInput, string keyword, string targetVideoId)
        {
            ChannelInfo ownChannel = ResolveChannel(channelInput);
            Dictionary<string, object> searchPayload = FetchSearch(keyword, false);
            string ownVideoId = targetVideoId == null ? "" : targetVideoId.Trim();
            Dictionary<string, object> ownSearchItem = null;
            Dictionary<string, object> ownAnySearchItem = null;
            Dictionary<string, object> competitorSearchItem = null;
            int ownNoFilterRank = -1;
            int ownAnyNoFilterRank = -1;
            int competitorNoFilterRank = -1;
            int noFilterLiveCount = 0;
            bool ownNoFilterLive = false;

            int noFilterIndex = 0;
            foreach (Dictionary<string, object> item in Items(searchPayload))
            {
                noFilterIndex += 1;
                Dictionary<string, object> snippet = Dict(item, "snippet");
                string itemVideoId = VideoId(item);
                bool isLive = Str(snippet, "liveBroadcastContent") == "live";
                if (isLive) noFilterLiveCount += 1;
                if (Str(snippet, "channelId") == ownChannel.Id)
                {
                    if (ownAnySearchItem == null || itemVideoId == ownVideoId)
                    {
                        ownAnySearchItem = item;
                        ownAnyNoFilterRank = noFilterIndex;
                        if (ownVideoId.Length == 0) ownVideoId = itemVideoId;
                    }
                    if (isLive)
                    {
                        ownNoFilterLive = true;
                        if (ownSearchItem == null || itemVideoId == ownVideoId)
                        {
                            ownSearchItem = item;
                            ownNoFilterRank = noFilterIndex;
                            if (ownVideoId.Length == 0) ownVideoId = itemVideoId;
                        }
                    }
                }
                if (isLive && Str(snippet, "channelId") != ownChannel.Id && competitorSearchItem == null)
                {
                    competitorSearchItem = item;
                    competitorNoFilterRank = noFilterIndex;
                }
            }

            if (ownSearchItem == null && ownAnySearchItem != null)
            {
                ownSearchItem = ownAnySearchItem;
                ownVideoId = VideoId(ownAnySearchItem);
                if (ownNoFilterRank < 0) ownNoFilterRank = ownAnyNoFilterRank;
            }

            ComparisonReport report = new ComparisonReport();
            report.Keyword = keyword == null ? "" : keyword.Trim();
            report.OwnChannelTitle = ownChannel.Title;
            if (competitorSearchItem == null)
            {
                string summary;
                if (noFilterLiveCount == 0)
                {
                    summary = "노필터 검색 상위권에 실시간 라이브가 없어 비교 대상 산정을 건너뛰었습니다. 라이브 필터 결과는 참고만 합니다.";
                }
                else if (ownNoFilterLive)
                {
                    summary = "노필터 검색 상위권의 실시간 라이브가 내 방송뿐이라 경쟁 채널 비교를 건너뛰었습니다.";
                }
                else
                {
                    summary = "노필터 검색 상위권에서 비교 가능한 실시간 라이브 경쟁 채널을 찾지 못했습니다.";
                }
                return ComparisonAnalyzer.Empty(
                    report.Keyword,
                    ownChannel.Title,
                    summary,
                    ownNoFilterRank
                );
            }

            string competitorVideoId = VideoId(competitorSearchItem);
            Dictionary<string, VideoInfo> videos = FetchVideoDetails(new string[] { ownVideoId, competitorVideoId });
            VideoInfo ownVideo = videos.ContainsKey(ownVideoId) ? videos[ownVideoId] : VideoFromSearch(ownSearchItem);
            VideoInfo competitorVideo = videos.ContainsKey(competitorVideoId) ? videos[competitorVideoId] : VideoFromSearch(competitorSearchItem);
            Dictionary<string, ChannelStats> channels = FetchChannelStats(new string[] { ownChannel.Id, competitorVideo.ChannelId });
            ChannelStats ownStats = channels.ContainsKey(ownChannel.Id) ? channels[ownChannel.Id] : new ChannelStats();
            ChannelStats competitorStats = channels.ContainsKey(competitorVideo.ChannelId) ? channels[competitorVideo.ChannelId] : new ChannelStats();

            return ComparisonAnalyzer.Build(report.Keyword, ownChannel.Title, ownVideo, competitorVideo, ownStats, competitorStats, ownNoFilterRank, competitorNoFilterRank);
        }

        public bool IsVideoLive(string videoId)
        {
            Dictionary<string, object> payload = GetJson("videos", Query(new Dictionary<string, string>
            {
                {"part", "snippet,liveStreamingDetails"},
                {"id", videoId},
                {"maxResults", "1"},
                {"fields", "items(id,snippet(liveBroadcastContent),liveStreamingDetails(actualEndTime))"}
            }));

            foreach (Dictionary<string, object> item in Items(payload))
            {
                Dictionary<string, object> snippet = Dict(item, "snippet");
                Dictionary<string, object> live = Dict(item, "liveStreamingDetails");
                if (Str(live, "actualEndTime").Length > 0) return false;
                return Str(snippet, "liveBroadcastContent") == "live";
            }
            return false;
        }

        private Dictionary<string, object> FetchSearch(string keyword, bool liveOnly)
        {
            Dictionary<string, string> parameters = new Dictionary<string, string>
            {
                {"part", "snippet"},
                {"q", keyword},
                {"type", "video"},
                {"maxResults", "20"},
                {"order", "relevance"},
                {"safeSearch", "none"},
                {"regionCode", "KR"},
                {"relevanceLanguage", "ko"},
                {"fields", "items(id/videoId,snippet(title,channelId,channelTitle,liveBroadcastContent))"}
            };
            if (liveOnly) parameters["eventType"] = "live";
            return GetJson("search", Query(parameters));
        }

        private Dictionary<string, VideoInfo> FetchVideoDetails(string[] videoIds)
        {
            StringBuilder ids = new StringBuilder();
            foreach (string videoId in videoIds)
            {
                if (string.IsNullOrWhiteSpace(videoId)) continue;
                if (ids.Length > 0) ids.Append(",");
                ids.Append(videoId.Trim());
            }
            Dictionary<string, VideoInfo> map = new Dictionary<string, VideoInfo>();
            if (ids.Length == 0) return map;

            Dictionary<string, object> payload = GetJson("videos", Query(new Dictionary<string, string>
            {
                {"part", "snippet,statistics,liveStreamingDetails"},
                {"id", ids.ToString()},
                {"fields", "items(id,snippet(title,description,tags,channelId,channelTitle,liveBroadcastContent,thumbnails),statistics(viewCount,likeCount,commentCount),liveStreamingDetails(concurrentViewers,actualStartTime))"}
            }));
            foreach (Dictionary<string, object> item in Items(payload))
            {
                VideoInfo info = VideoFromDetail(item);
                map[info.VideoId] = info;
            }
            return map;
        }

        private Dictionary<string, ChannelStats> FetchChannelStats(string[] channelIds)
        {
            StringBuilder ids = new StringBuilder();
            foreach (string channelId in channelIds)
            {
                if (string.IsNullOrWhiteSpace(channelId)) continue;
                if (ids.ToString().Contains(channelId.Trim())) continue;
                if (ids.Length > 0) ids.Append(",");
                ids.Append(channelId.Trim());
            }
            Dictionary<string, ChannelStats> map = new Dictionary<string, ChannelStats>();
            if (ids.Length == 0) return map;

            Dictionary<string, object> payload = GetJson("channels", Query(new Dictionary<string, string>
            {
                {"part", "snippet,statistics"},
                {"id", ids.ToString()},
                {"fields", "items(id,snippet(title,publishedAt),statistics(subscriberCount,hiddenSubscriberCount,videoCount,viewCount))"}
            }));
            foreach (Dictionary<string, object> item in Items(payload))
            {
                Dictionary<string, object> snippet = Dict(item, "snippet");
                Dictionary<string, object> stats = Dict(item, "statistics");
                ChannelStats info = new ChannelStats();
                info.ChannelId = Str(item, "id");
                info.ChannelTitle = Str(snippet, "title");
                info.SubscriberCount = string.Equals(Str(stats, "hiddenSubscriberCount"), "true", StringComparison.OrdinalIgnoreCase) ? null : LongValue(stats, "subscriberCount");
                map[info.ChannelId] = info;
            }
            return map;
        }

        private VideoInfo VideoFromSearch(Dictionary<string, object> item)
        {
            VideoInfo info = new VideoInfo();
            if (item == null) return info;
            Dictionary<string, object> snippet = Dict(item, "snippet");
            info.VideoId = VideoId(item);
            info.Title = Str(snippet, "title");
            info.ChannelId = Str(snippet, "channelId");
            info.ChannelTitle = Str(snippet, "channelTitle");
            info.Thumbnail = ThumbnailUrl(snippet);
            return info;
        }

        private VideoInfo VideoFromDetail(Dictionary<string, object> item)
        {
            VideoInfo info = new VideoInfo();
            Dictionary<string, object> snippet = Dict(item, "snippet");
            Dictionary<string, object> stats = Dict(item, "statistics");
            Dictionary<string, object> live = Dict(item, "liveStreamingDetails");
            info.VideoId = Str(item, "id");
            info.Title = Str(snippet, "title");
            info.Description = Str(snippet, "description");
            info.ChannelId = Str(snippet, "channelId");
            info.ChannelTitle = Str(snippet, "channelTitle");
            info.Thumbnail = ThumbnailUrl(snippet);
            info.TagCount = CountArray(snippet, "tags");
            info.ViewCount = LongValue(stats, "viewCount");
            info.LikeCount = LongValue(stats, "likeCount");
            info.CommentCount = LongValue(stats, "commentCount");
            info.CurrentViewers = LongValue(live, "concurrentViewers");
            return info;
        }

        private string ThumbnailUrl(Dictionary<string, object> snippet)
        {
            Dictionary<string, object> thumbs = Dict(snippet, "thumbnails");
            string[] keys = new string[] { "maxres", "standard", "high", "medium", "default" };
            foreach (string key in keys)
            {
                Dictionary<string, object> thumb = Dict(thumbs, key);
                string url = Str(thumb, "url");
                if (url.Length > 0) return url;
            }
            return "";
        }

        private int CountArray(Dictionary<string, object> source, string key)
        {
            if (source == null || !source.ContainsKey(key)) return 0;
            IEnumerable values = source[key] as IEnumerable;
            if (values == null || source[key] is string) return 0;
            int count = 0;
            foreach (object _ in values) count += 1;
            return count;
        }

        private long? LongValue(Dictionary<string, object> source, string key)
        {
            string value = Str(source, key);
            long number;
            return long.TryParse(value, out number) ? (long?)number : null;
        }

        private void ApplySearchResult(RankResult result, Dictionary<string, object> payload, bool liveOnly)
        {
            int index = 0;
            int fallbackRank = -1;
            Dictionary<string, object> fallbackItem = null;
            foreach (Dictionary<string, object> item in Items(payload))
            {
                Dictionary<string, object> snippet = Dict(item, "snippet");
                if (index == 0)
                {
                    if (liveOnly) result.TopLiveChannelTitle = Str(snippet, "channelTitle");
                    else result.TopNoFilterChannelTitle = Str(snippet, "channelTitle");
                }

                string itemVideoId = VideoId(item);
                bool isTargetChannel = Str(snippet, "channelId") == result.ChannelId;
                bool isLockedVideo = result.TargetVideoId.Length > 0 && result.TargetVideoId == itemVideoId;
                bool isSearchLive = liveOnly || Str(snippet, "liveBroadcastContent") == "live";
                bool exactTarget = isTargetChannel && result.TargetVideoId.Length > 0 && isLockedVideo;
                bool channelLiveFallback = isTargetChannel && isSearchLive;

                if (exactTarget || (result.TargetVideoId.Length == 0 && channelLiveFallback))
                {
                    ApplyMatchedSearchResult(result, item, liveOnly, index + 1);
                    return;
                }

                if (fallbackRank < 0 && channelLiveFallback)
                {
                    fallbackRank = index + 1;
                    fallbackItem = item;
                }
                index += 1;
            }

            if (fallbackItem != null)
            {
                ApplyMatchedSearchResult(result, fallbackItem, liveOnly, fallbackRank, result.TargetVideoId.Length == 0);
            }
        }

        private void ApplyMatchedSearchResult(RankResult result, Dictionary<string, object> item, bool liveOnly, int rank, bool updateVideoId = true)
        {
            Dictionary<string, object> snippet = Dict(item, "snippet");
            if (liveOnly) result.LiveRank = rank;
            else result.NoFilterRank = rank;
            if (updateVideoId) result.TargetVideoId = VideoId(item);
            result.TargetTitle = Str(snippet, "title");
            result.ChannelTitle = Str(snippet, "channelTitle");
        }

        private void AddOwnLivesFromSearch(ActiveLiveStatus status, Dictionary<string, bool> seen, Dictionary<string, object> payload, bool liveOnly)
        {
            foreach (Dictionary<string, object> item in Items(payload))
            {
                Dictionary<string, object> snippet = Dict(item, "snippet");
                if (Str(snippet, "channelId") != status.ChannelId) continue;

                string videoId = VideoId(item);
                if (videoId.Length == 0 || seen.ContainsKey(videoId)) continue;

                bool isLive = liveOnly || Str(snippet, "liveBroadcastContent") == "live";
                if (!isLive) continue;

                seen[videoId] = true;
                status.Count += 1;
                if (status.FirstVideoId.Length == 0)
                {
                    status.FirstVideoId = videoId;
                    status.FirstTitle = Str(snippet, "title");
                }
            }
        }

        private ChannelInfo ResolveChannel(string input)
        {
            string cleanInput = input == null ? "" : input.Trim();
            if (cachedChannel != null && cachedChannelInput == cleanInput) return cachedChannel;

            foreach (KeyValuePair<string, string> candidate in ChannelCandidates(cleanInput))
            {
                Dictionary<string, string> parameters = new Dictionary<string, string>
                {
                    {"part", "snippet"},
                    {"maxResults", "1"},
                    {"fields", "items(id,snippet(title,customUrl))"},
                    {candidate.Key, candidate.Value}
                };
                Dictionary<string, object> payload = GetJson("channels", Query(parameters));
                foreach (Dictionary<string, object> item in Items(payload))
                {
                    Dictionary<string, object> snippet = Dict(item, "snippet");
                    ChannelInfo channel = new ChannelInfo();
                    channel.Id = Str(item, "id");
                    channel.Title = Str(snippet, "title");
                    if (channel.Id.Length == 0) continue;
                    cachedChannelInput = cleanInput;
                    cachedChannel = channel;
                    return channel;
                }
            }
            throw new Exception("내 채널을 찾지 못했습니다.");
        }

        private IEnumerable<KeyValuePair<string, string>> ChannelCandidates(string raw)
        {
            List<KeyValuePair<string, string>> list = new List<KeyValuePair<string, string>>();
            if (raw.Length == 0) return list;

            int channelIndex = raw.IndexOf("youtube.com/channel/", StringComparison.OrdinalIgnoreCase);
            if (channelIndex >= 0)
            {
                string id = raw.Substring(channelIndex + "youtube.com/channel/".Length).Split('/', '?', '#')[0];
                list.Add(new KeyValuePair<string, string>("id", id));
                return list;
            }

            if (raw.StartsWith("UC", StringComparison.OrdinalIgnoreCase) && raw.Length >= 20)
            {
                list.Add(new KeyValuePair<string, string>("id", raw));
                return list;
            }

            string handle = "";
            int handleIndex = raw.IndexOf("youtube.com/@", StringComparison.OrdinalIgnoreCase);
            if (handleIndex >= 0)
            {
                handle = raw.Substring(handleIndex + "youtube.com/".Length).Split('/', '?', '#')[0];
            }
            else if (raw.StartsWith("@"))
            {
                handle = raw.Split('/', '?', '#')[0];
            }
            else
            {
                string clean = raw.Replace("https://", "").Replace("http://", "").Replace("www.", "");
                clean = clean.Replace("youtube.com/", "").Trim().Split('/', '?', '#')[0];
                if (clean.Length > 0) handle = clean.StartsWith("@") ? clean : "@" + clean;
            }

            if (handle.Length > 0) list.Add(new KeyValuePair<string, string>("forHandle", handle));
            return list;
        }

        private string Query(Dictionary<string, string> parameters)
        {
            StringBuilder builder = new StringBuilder();
            foreach (KeyValuePair<string, string> pair in parameters)
            {
                if (string.IsNullOrWhiteSpace(pair.Value)) continue;
                if (builder.Length > 0) builder.Append("&");
                builder.Append(Uri.EscapeDataString(pair.Key));
                builder.Append("=");
                builder.Append(Uri.EscapeDataString(pair.Value));
            }
            if (builder.Length > 0) builder.Append("&");
            builder.Append("key=");
            builder.Append(Uri.EscapeDataString(apiKey));
            return builder.ToString();
        }

        private Dictionary<string, object> GetJson(string resource, string query)
        {
            string url = "https://www.googleapis.com/youtube/v3/" + resource + "?" + query;
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = "GET";
            request.Timeout = 15000;
            request.ReadWriteTimeout = 15000;

            try
            {
                using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
                using (Stream stream = response.GetResponseStream())
                using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                {
                    return serializer.Deserialize<Dictionary<string, object>>(reader.ReadToEnd());
                }
            }
            catch (WebException ex)
            {
                string message = "YouTube API 요청 실패";
                if (ex.Response != null)
                {
                    using (Stream stream = ex.Response.GetResponseStream())
                    using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                    {
                        string body = reader.ReadToEnd();
                        try
                        {
                            Dictionary<string, object> payload = serializer.Deserialize<Dictionary<string, object>>(body);
                            Dictionary<string, object> error = Dict(payload, "error");
                            if (error.Count > 0) message = Str(error, "message");
                        }
                        catch
                        {
                        }
                    }
                }
                throw new Exception(message);
            }
        }

        private IEnumerable<Dictionary<string, object>> Items(Dictionary<string, object> payload)
        {
            if (payload == null || !payload.ContainsKey("items")) yield break;
            IEnumerable values = payload["items"] as IEnumerable;
            if (values == null) yield break;
            foreach (object value in values)
            {
                Dictionary<string, object> item = value as Dictionary<string, object>;
                if (item != null) yield return item;
            }
        }

        private Dictionary<string, object> Dict(Dictionary<string, object> source, string key)
        {
            if (source == null || !source.ContainsKey(key)) return new Dictionary<string, object>();
            return source[key] as Dictionary<string, object> ?? new Dictionary<string, object>();
        }

        private string Str(Dictionary<string, object> source, string key)
        {
            if (source == null || !source.ContainsKey(key) || source[key] == null) return "";
            return Convert.ToString(source[key]);
        }

        private string VideoId(Dictionary<string, object> item)
        {
            Dictionary<string, object> id = Dict(item, "id");
            string videoId = Str(id, "videoId");
            return videoId.Length > 0 ? videoId : Str(item, "id");
        }
    }

    internal sealed class ChannelInfo
    {
        public string Id = "";
        public string Title = "";
    }

    internal sealed class ActiveLiveStatus
    {
        public string ChannelId = "";
        public string ChannelTitle = "";
        public string FirstVideoId = "";
        public string FirstTitle = "";
        public int Count;
    }

    internal sealed class RankResult
    {
        public string Keyword = "";
        public string ChannelId = "";
        public string ChannelTitle = "";
        public string TargetVideoId = "";
        public string TargetTitle = "";
        public string TopNoFilterChannelTitle = "";
        public string TopLiveChannelTitle = "";
        public int NoFilterRank = -1;
        public int LiveRank = -1;
    }
}
