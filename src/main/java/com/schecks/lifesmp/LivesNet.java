package com.schecks.lifesmp;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side lives-display delivery, plus the shared heart-row builder used
 * by both the client HUD and the action-bar fallback.
 *
 * This class is server/common-safe — it references no client-only classes,
 * so it loads fine on a dedicated server.
 */
public final class LivesNet {
    private static final int HEART_ROW_CAP = 20;   // beyond this, show compact text
    private static final int FILLED_COLOR  = 0xFF4C4C;
    private static final int LOST_COLOR    = 0x55383A;

    private LivesNet() {}

    /** Registers the S2C payload type. Call once from the main entrypoint. */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(LivesPayload.TYPE, LivesPayload.CODEC);
    }

    /**
     * Pushes a player's current lives to their own client.
     *  - Modded client (channel registered) -> LivesPayload packet -> HUD bar.
     *  - Vanilla client -> action-bar flash with the same heart row.
     */
    public static void notifyLivesChanged(MinecraftServer server, ServerPlayer player) {
        int lives = LivesData.get(server).getLives(player.getUUID());
        int max = LifeConfig.get().maxLives;
        if (ServerPlayNetworking.canSend(player, LivesPayload.TYPE)) {
            ServerPlayNetworking.send(player, new LivesPayload(lives, max));
        } else {
            player.sendSystemMessage(heartBar(lives, max), true);   // true = action bar
        }
    }

    /**
     * Builds the heart row shown by the client HUD and the fallback action bar.
     * Filled red hearts for current lives, dim hearts for lost ones up to max.
     * Falls back to compact "N / M" text when max is very large.
     */
    public static Component heartBar(int lives, int max) {
        int safeMax = Math.max(max, Math.max(lives, 1));
        if (safeMax > HEART_ROW_CAP) {
            return Component.literal("❤ " + lives + " / " + safeMax)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(FILLED_COLOR)));
        }
        MutableComponent row = Component.empty();
        for (int i = 0; i < safeMax; i++) {
            int color = i < lives ? FILLED_COLOR : LOST_COLOR;
            row.append(Component.literal("❤")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return row;
    }
}
