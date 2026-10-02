# Balkan Tagger

**Display every player's official [Balkan Tiers](https://balkantiers.com) rank directly in-game.**

Balkan Tagger is a Fabric mod for Minecraft **1.21.11**. It retrieves official tier data from the Balkan Tiers ranking platform and displays player rankings (HT1–LT5) above nametags and in the player list.

Balkan Tiers is the PvP tier testing platform of **Seiky Network**: certified testers rank players across ten gamemodes (Sword, Axe, Mace, DiaPot, NethPot, UHC, SMP, DiaSMP, Crystal, Spear).

## Commands

Client-side commands that work on every server:

* `/bktiers <player>` opens the player's Balkan Tiers profile (skin, tiers, points, global ranking, region, title).
* `/bktiers` opens the player search.
* `/tiertagger <player>` prints the player's tiers in chat.

## Installation

1. Install Fabric Loader for Minecraft **1.21.11**.
2. Place Balkan Tagger, [Fabric API](https://modrinth.com/mod/fabric-api) and [ukulib](https://modrinth.com/mod/ukulib) into your `mods` directory.
3. Launch Minecraft.

Balkan Tagger should not be installed together with the original TierTagger or other mods based on it, as they share internal components and are not compatible with each other.

Configuration is available through **Mod Menu → Balkan Tagger → Configure**.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`.

## Links

* Leaderboard: https://balkantiers.com
* Testing Discord: https://discord.gg/blkntiers
* Seiky Network Discord: https://discord.gg/seiky

## Credits and License

Balkan Tagger is based on [TierTagger](https://github.com/mctiers-dev/TierTagger) by uku (mctiers-dev), originally created by netiyiy, and has been adapted for the Balkan Tiers platform. Licensed under **MPL-2.0**, see [LICENSE](LICENSE).
