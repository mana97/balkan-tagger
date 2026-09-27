package com.kevin.tiertagger.model;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PlayerInfo(String uuid, String name, Map<String, Ranking> rankings, @Nullable String region,
                         @Nullable @SerializedName("region_name") String regionName, int points,
                         int overall, List<Badge> badges, @SerializedName("combat_master") boolean combatMaster) {
    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
                          @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
                          boolean retired) {

        /**
         * Lower is better.
         */
        public int comparableTier() {
            return tier * 2 + pos;
        }

        /**
         * Lower is better.
         */
        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return peakTier * 2 + peakPos;
            }
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    // Balkan Tiers regions, same colors as balkantiers.com (--rg-* in style.css)
    private static final Map<String, Integer> REGION_COLORS = Map.ofEntries(
            Map.entry("CRO", 0x5ca0ff),
            Map.entry("SRB", 0xe05cff),
            Map.entry("BIH", 0xffd75e),
            Map.entry("SVN", 0x3fd6c2),
            Map.entry("MKD", 0xffb02e),
            Map.entry("MNE", 0xff8c6b),
            Map.entry("KOS", 0x9d8cff),
            Map.entry("ALB", 0xff5c5c),
            Map.entry("BGR", 0x4ade80),
            Map.entry("GRC", 0x38e8ff),
            Map.entry("INT", 0x8a8da3)
    );

    public static CompletableFuture<PlayerInfo> get(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/" + uuid;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> TierTagger.GSON.fromJson(s, PlayerInfo.class))
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error getting player info ({})", uuid, t);
                });
    }

    public static CompletableFuture<Map<String, Ranking>> getRankings(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/" + uuid + "/rankings";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> TierTagger.GSON.fromJson(s, new TypeToken<Map<String, Ranking>>() {}))
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error getting player rankings ({})", uuid, t);
                });
    }

    public static CompletableFuture<PlayerInfo> search(HttpClient client, String query) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/by-name/" + query;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    // 1.0.5: 404 = not on Balkan Tiers, anything else but 200 = API problem (/bktiers shows which one)
                    if (r.statusCode() == 404) throw new SearchException("Player not found: " + query, true);
                    if (r.statusCode() != 200) throw new SearchException("Balkan Tiers API HTTP " + r.statusCode(), false);
                    return r.body();
                })
                .thenApply(s -> TierTagger.GSON.fromJson(s, PlayerInfo.class))
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error searching player {}", query, t);
                });
    }

    /** Search failure; notFound = the player simply isn't on Balkan Tiers (HTTP 404), otherwise the API failed. */
    public static final class SearchException extends RuntimeException {
        private final boolean notFound;

        public SearchException(String message, boolean notFound) {
            super(message);
            this.notFound = notFound;
        }

        public boolean isNotFound() {
            return notFound;
        }
    }

    public int getRegionColor() {
        if (this.region == null) return 0x8a8da3;
        return REGION_COLORS.getOrDefault(this.region.toUpperCase(Locale.ROOT), 0xffffff);
    }

    /**
     * "Croatia" when the API sends the full name, otherwise the region code ("CRO"), or "-" for no region.
     */
    public String getRegionLabel() {
        if (this.regionName != null && !this.regionName.isBlank()) return this.regionName;
        return this.region == null || this.region.isBlank() ? "-" : this.region;
    }

    public static Optional<NamedRanking> getHighestRanking(Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())));
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        // Balkan Tiers titles (no "Combat" prefix since 26.09.2026), colors = balkantiers.com title colors
        GRANDMASTER("Grandmaster", 0xFFC861, 0xFFC861),
        MASTER("Master", 0xFF5C8A, 0xFF5C8A),
        ACE("Ace", 0xE05CFF, 0xE05CFF),
        SPECIALIST("Specialist", 0x9D7BFF, 0x9D7BFF),
        CADET("Cadet", 0x5CA0FF, 0x5CA0FF),
        NOVICE("Novice", 0x3FD6C2, 0x3FD6C2),
        ROOKIE("Rookie", 0x8A8DA3, 0x8A8DA3),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (this.points >= 400) {
            return PointInfo.GRANDMASTER;
        } else if (this.points >= 250) {
            return PointInfo.MASTER;
        } else if (this.points >= 100) {
            return PointInfo.ACE;
        } else if (this.points >= 50) {
            return PointInfo.SPECIALIST;
        } else if (this.points >= 20) {
            return PointInfo.CADET;
        } else if (this.points >= 10) {
            return PointInfo.NOVICE;
        } else if (this.points >= 1) {
            return PointInfo.ROOKIE;
        } else {
            return PointInfo.UNRANKED;
        }
    }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())))
                .toList());

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.tier)
                .thenComparingInt(a -> a.ranking.pos));

        return tiers;
    }
}