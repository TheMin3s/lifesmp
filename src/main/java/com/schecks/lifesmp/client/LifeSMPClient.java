package com.schecks.lifesmp.client;

import com.schecks.lifesmp.FileTransferPayload;
import com.schecks.lifesmp.LivesNet;
import com.schecks.lifesmp.LivesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-side half of LifeSMP: receives lives updates from the server and
 * draws a small heart row on the HUD, just above the armor bar.
 *
 * This class (and everything in the client package) is only loaded on a
 * physical client — the dedicated server never touches it.
 */
public class LifeSMPClient implements ClientModInitializer {
    private static final Identifier HUD_ID = Identifier.parse("lifesmp:lives_hud");

    @Override
    public void onInitializeClient() {
        // Receive lives updates pushed by the server.
        ClientPlayNetworking.registerGlobalReceiver(LivesPayload.TYPE, (payload, context) ->
            ClientLivesState.update(payload.lives(), payload.maxLives()));

        // Receive offered-file transfers; hop to the main thread to show the
        // confirmation screen.
        ClientPlayNetworking.registerGlobalReceiver(FileTransferPayload.TYPE, (payload, context) ->
            context.client().execute(() -> FileDownloadHandler.handle(payload)));

        // Draw the heart row immediately after (i.e. just above) the armor bar.
        HudElement element = (graphics, deltaTracker) -> renderLivesBar(graphics);
        HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, HUD_ID, element);
    }

    private static void renderLivesBar(GuiGraphicsExtractor g) {
        if (!ClientLivesState.hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;

        Component bar = LivesNet.heartBar(ClientLivesState.lives(), ClientLivesState.maxLives());
        Font font = mc.font;
        int x = g.guiWidth() / 2 - 91;     // left edge of the hotbar
        int y = g.guiHeight() - 59;        // one row above the armor bar
        g.text(font, bar, x, y, 0xFFFFFFFF, true);   // true = drop shadow
    }
}
