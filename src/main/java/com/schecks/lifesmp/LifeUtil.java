package com.schecks.lifesmp;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LifeUtil {
    private LifeUtil() {}

    /**
     * Resolves a player name to a NameAndId record.
     *
     * Order of lookup:
     *  1. Online ServerPlayer via PlayerList.getPlayerByName
     *  2. Cached UUID + last known name in LivesData (covers banned/offline players we've seen)
     *
     * Returns null if the player has never joined this server.
     */
    public static NameAndId resolveNameAndId(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new NameAndId(online.getUUID(), online.getGameProfile().name());
        }
        UUID cached = LivesData.get(server).findByName(name);
        if (cached != null) {
            String cachedName = LivesData.get(server).getOrCreate(cached).lastKnownName;
            return new NameAndId(cached, cachedName.isEmpty() ? name : cachedName);
        }
        return null;
    }

    public static boolean isBanned(MinecraftServer server, NameAndId nameAndId) {
        return server.getPlayerList().getBans().isBanned(nameAndId);
    }

    public static void banForOutOfLives(MinecraftServer server, NameAndId nameAndId) {
        PlayerList pl = server.getPlayerList();
        UserBanList bans = pl.getBans();
        String reason = LifeConfig.get().banMessage;
        if (!bans.isBanned(nameAndId)) {
            UserBanListEntry entry = new UserBanListEntry(
                nameAndId,
                null,
                "lifesmp",
                null,
                reason
            );
            bans.add(entry);
        }
        ServerPlayer online = pl.getPlayer(nameAndId.id());
        if (online != null) {
            online.connection.disconnect(Component.literal(reason));
        }
    }

    public static void unban(MinecraftServer server, NameAndId nameAndId) {
        UserBanList bans = server.getPlayerList().getBans();
        UserBanListEntry entry = bans.get(nameAndId);
        if (entry != null) bans.remove(nameAndId);
    }

    /**
     * Called when a player's lives actually change: updates the tab-list name
     * for everyone AND pushes the lives HUD / action-bar fallback to that player.
     */
    public static void refreshTabName(MinecraftServer server, ServerPlayer player) {
        broadcastTabPacket(server, player);
        LivesNet.notifyLivesChanged(server, player);
    }

    /**
     * Refreshes every player's tab-list name only — no HUD push. Used on join,
     * so a player connecting doesn't flash an action bar on everyone else.
     */
    public static void refreshAllTabs(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            broadcastTabPacket(server, p);
        }
    }

    /**
     * Grants the player a few seconds of damage immunity, applied on join and
     * respawn. Implemented as Resistance V (amplifier 4 = 100% reduction) plus
     * Fire Resistance for {@code spawn-immunity-seconds} seconds. Fire damage
     * is on its own pre-Resistance code path in some sources, so we add it
     * explicitly to cover spawning into lava / fire blocks. No effect when
     * the config is 0.
     */
    /**
     * Per-player spawn-immunity expiry (system-millis). Implemented as a
     * timestamp map + an ALLOW_DAMAGE hook in {@code JoinHandler} so the
     * immunity covers everything (fire/lava/drown included) and never appears
     * in the effects list — there's no MobEffect involved at all.
     */
    private static final Map<UUID, Long> SPAWN_IMMUNE_UNTIL = new ConcurrentHashMap<>();

    public static void applySpawnImmunity(ServerPlayer player) {
        int secs = LifeConfig.get().spawnImmunitySeconds;
        if (secs <= 0) return;
        SPAWN_IMMUNE_UNTIL.put(player.getUUID(), System.currentTimeMillis() + secs * 1000L);
    }

    /** True while {@code id} is within their spawn-immunity window. */
    public static boolean isSpawnImmune(UUID id) {
        Long until = SPAWN_IMMUNE_UNTIL.get(id);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        SPAWN_IMMUNE_UNTIL.remove(id);
        return false;
    }

    /** Forgets any pending immunity for a player — e.g. on disconnect. */
    public static void clearSpawnImmunity(UUID id) {
        SPAWN_IMMUNE_UNTIL.remove(id);
    }

    private static void broadcastTabPacket(MinecraftServer server, ServerPlayer player) {
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
            List.of(player)
        );
        server.getPlayerList().broadcastAll(packet);
    }
}
