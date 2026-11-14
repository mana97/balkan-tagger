package com.kevin.tiertagger;

import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TierCache {
    /** Requests for the API gamemode list before the built-in list is kept for the session (about 25 minutes). */
    static final int GAMEMODE_ATTEMPTS = 8;

    // replaced as a whole, never changed in place: the render thread reads it while a request thread sets it
    private static volatile List<GameMode> gamemodes = GameMode.BUILT_IN;
    // every init() takes a new number, so the retries of an older one (before a tierlist change) stop
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Map<UUID, Optional<Map<String, PlayerInfo.Ranking>>> TIERS = new ConcurrentHashMap<>();
    // failed rankings requests in a row per player, for the delay before the next one
    private static final Map<UUID, Integer> FAILURES = new ConcurrentHashMap<>();

    /**
     * Loads the gamemode list from the API in the background. The built-in list is used until it arrives and stays
     * if the API can't be reached; the request is tried again with a growing delay. Before, one failed request at
     * startup left the gamemode on "None" until the game was restarted.
     */
    public static void init() {
        fetchGamemodes(GENERATION.incrementAndGet(), 1);
    }

    private static void fetchGamemodes(int generation, int attempt) {
        CompletableFuture<List<GameMode>> request;
        try {
            request = GameMode.fetchGamemodes(TierTagger.getClient());
        } catch (RuntimeException e) {
            request = CompletableFuture.failedFuture(e);
        }

        request.whenComplete((list, t) -> {
            if (generation != GENERATION.get()) return;

            if (t == null && !list.isEmpty()) {
                gamemodes = List.copyOf(list);
                TierTagger.getLogger().info("Found {} tierlists: {}", list.size(), list.stream().map(GameMode::id).toList());
            } else if (attempt < GAMEMODE_ATTEMPTS) {
                long delay = retryDelaySeconds(15, attempt, 300);
                TierTagger.getLogger().warn("Could not load gamemodes ({}), using the built-in list, trying again in {} s",
                        t == null ? "empty list" : t.toString(), delay);
                CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute(() -> fetchGamemodes(generation, attempt + 1));
            } else {
                TierTagger.getLogger().warn("Could not load gamemodes, keeping the built-in list", t);
            }
        });
    }

    /** base, 2 x base, 4 x base ... for attempt 1, 2, 3 ..., never more than max. */
    static long retryDelaySeconds(long base, int attempt, long max) {
        return Math.min(max, base << Math.min(Math.max(attempt - 1, 0), 20));
    }

    public static List<GameMode> getGamemodes() {
        List<GameMode> list = gamemodes;
        return list.isEmpty() ? Collections.singletonList(GameMode.NONE) : list;
    }

    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(UUID uuid) {
        Optional<Map<String, PlayerInfo.Ranking>> cached = TIERS.get(uuid);
        if (cached != null) return cached;

        // only the caller that adds the placeholder sends the request, and it does so outside the map's lock
        if (TIERS.putIfAbsent(uuid, Optional.empty()) == null && isPlayerUuid(uuid)) {
            fetchRankings(uuid);
        }
        return Optional.empty();
    }

    private static void fetchRankings(UUID uuid) {
        CompletableFuture<Map<String, PlayerInfo.Ranking>> request;
        try {
            request = PlayerInfo.getRankings(TierTagger.getClient(), uuid);
        } catch (RuntimeException e) {
            request = CompletableFuture.failedFuture(e);
        }

        request.whenComplete((info, t) -> {
            if (t == null) {
                FAILURES.remove(uuid);
                TIERS.put(uuid, Optional.ofNullable(info));
            } else {
                // network error, timeout or API problem: no tier for now, and the player is asked for again later
                // (the placeholder is removed) instead of staying without a tier until the cache is cleared
                int failures = FAILURES.merge(uuid, 1, Integer::sum);
                long delay = retryDelaySeconds(30, failures, 600);
                CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS)
                        .execute(() -> TIERS.remove(uuid, Optional.empty()));
            }
        });
    }

    /**
     * Version 4 = premium (Mojang) account, version 3 = offline-mode player (cracked servers, and premium players on
     * servers with hybrid login), which Balkan Tiers knows by the offline UUID of the name. Other versions are NPCs
     * and fake entries and are never sent to the API.
     */
    static boolean isPlayerUuid(UUID uuid) {
        int version = uuid.version();
        return version == 4 || version == 3;
    }

    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        return PlayerInfo.search(TierTagger.getClient(), query).thenApply(p -> {
            UUID uuid = parseUUID(p.uuid());
            TIERS.put(uuid, Optional.of(p.rankings()));
            return p;
        });
    }

    public static void clearCache() {
        TIERS.clear();
        FAILURES.clear();
    }

    public static GameMode findNextMode(GameMode current) {
        List<GameMode> list = getGamemodes();
        return list.get((list.indexOf(current) + 1) % list.size());
    }

    public static Optional<GameMode> findMode(String id) {
        return gamemodes.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    public static GameMode findModeOrUgly(String id) {
        return findMode(id).orElseGet(() -> new GameMode(id, id));
    }

    private static UUID parseUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            long mostSignificant = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
            long leastSignificant = Long.parseUnsignedLong(uuid.substring(16), 16);
            return new UUID(mostSignificant, leastSignificant);
        }
    }

    private TierCache() {
    }
}
