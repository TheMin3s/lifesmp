package com.schecks.lifesmp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Opened from the inventory's hearts box. Lets the player turn a life into a
 * Life Shard (withdraw) or a Life Shard back into a life (deposit) without
 * leaving the inventory screen first. Both buttons route to the existing
 * /life withdraw and /life deposit commands.
 */
public final class LivesWithdrawDepositScreen extends Screen {
    public LivesWithdrawDepositScreen() {
        super(Component.literal("Lives"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(Button.builder(Component.literal("Withdraw a life → Life Shard"), b -> run("life withdraw 1"))
            .bounds(cx - 110, cy - 10, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Deposit a Life Shard → life"), b -> run("life deposit"))
            .bounds(cx - 110, cy + 14, 220, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(cx - 75, cy + 48, 150, 20).build());
    }

    private void run(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.centeredText(this.font, this.title, cx, cy - 48, 0xFFFF4C4C);
        int lives = ClientLivesState.lives();
        int max = ClientLivesState.maxLives();
        g.centeredText(this.font,
            Component.literal("You have " + lives + " / " + max + (lives == 1 ? " life" : " lives")),
            cx, cy - 30, 0xFFCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
