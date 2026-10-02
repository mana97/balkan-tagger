package com.kevin.tiertagger.config;

import com.google.gson.internal.LinkedTreeMap;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.model.GameMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.uku3lig.ukulib.config.option.StringTranslatable;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "vanilla";
    private boolean showRetired = true;
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private boolean showIcons = true;
    private boolean playerList = true;
    private int retiredColor = 0xa2d6ff;
    // note: this is a GSON internal class and may change in a future GSON release
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();

    // internal

    /**
     * <p>the field was renamed so that existing player configs fall back to the default value</p>
     * <p>previous name(s): {@code baseUrl}</p>
     */
    private String apiUrl = "https://balkantiers.com";

    public GameMode getGameMode() {
        Optional<GameMode> opt = TierCache.findMode(this.gameMode);
        if (opt.isPresent()) {
            return opt.get();
        } else {
            GameMode first = TierCache.getGamemodes().getFirst();
            if (!first.isNone()) this.gameMode = first.id();
            return first;
        }
    }

    /**
     * Tier colors of balkantiers.com (tier 1 gold, 2 silver, 3 bronze, 4 purple, 5 gray); LT = HT a bit darker.
     */
    public static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xffb02e);
        colors.put("LT1", 0xd99627);
        colors.put("HT2", 0xb9c6e4);
        colors.put("LT2", 0x9da8c2);
        colors.put("HT3", 0xff7a4d);
        colors.put("LT3", 0xd96841);
        colors.put("HT4", 0x9d8cff);
        colors.put("LT4", 0x8577d9);
        colors.put("HT5", 0x7f8296);
        colors.put("LT5", 0x6c6f80);

        return colors;
    }

    // previous default colors (the original MCTiers colors)
    private static final Map<String, Integer> OLD_DEFAULT_COLORS = Map.of(
            "HT1", 0xe8ba3a, "LT1", 0xd5b355, "HT2", 0xc4d3e7, "LT2", 0xa0a7b2, "HT3", 0xf89f5a,
            "LT3", 0xc67b42, "HT4", 0x81749a, "LT4", 0x655b79, "HT5", 0x8f82a8, "LT5", 0x655b79);

    /**
     * A config that still has the untouched previous default colors switches to the site colors.
     * Colors a player picked are left alone. Returns true when something changed (caller saves).
     */
    public boolean migrateOldDefaultColors() {
        if (this.tierColors == null || this.tierColors.size() != OLD_DEFAULT_COLORS.size()) return false;
        for (Map.Entry<String, Integer> e : OLD_DEFAULT_COLORS.entrySet()) {
            Object v = this.tierColors.get(e.getKey());
            if (!(v instanceof Number n) || n.intValue() != e.getValue()) return false;
        }
        this.tierColors = defaultColors();
        return true;
    }

    @Getter
    @AllArgsConstructor
    public enum HighestMode implements StringTranslatable {
        NEVER("never", "tiertagger.highest.never"),
        NOT_FOUND("not_found", "tiertagger.highest.not_found"),
        ALWAYS("always", "tiertagger.highest.always"),
        ;

        private final String name;
        private final String translationKey;
    }
}
