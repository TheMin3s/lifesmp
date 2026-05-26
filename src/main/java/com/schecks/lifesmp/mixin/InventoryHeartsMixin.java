package com.schecks.lifesmp.mixin;

import com.schecks.lifesmp.LifeItems;
import com.schecks.lifesmp.client.ClientLivesState;
import com.schecks.lifesmp.client.LivesWithdrawDepositScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a clickable hearts box below the vanilla inventory GUI. Shows up to
 * ten hardcore-heart icons: full for lives you have, blank for lives you've
 * lost, with the rightmost full slot replaced by the Life Shard item icon
 * (the "next one to be lost"). Lives above ten get an extra heart with the
 * overflow count drawn on top. Clicking the box opens the withdraw/deposit
 * screen so you can shuffle lives ↔ shards without typing the commands.
 *
 * Client-only; registered under the "client" mixin array in lifesmp.mixins.json.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryHeartsMixin extends AbstractContainerScreen<InventoryMenu> {
    @Unique private static final Identifier HARDCORE_FULL =
        Identifier.parse("minecraft:hud/heart/hardcore_full");
    @Unique private static final Identifier HARDCORE_CONTAINER =
        Identifier.parse("minecraft:hud/heart/container_hardcore");

    private InventoryHeartsMixin(InventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lifesmp$initHeartsBox(CallbackInfo ci) {
        if (!ClientLivesState.hasData()) return;
        int x = this.leftPos + 23;
        int y = this.topPos + 172;
        Button button = Button.builder(Component.empty(),
                b -> Minecraft.getInstance().setScreen(new LivesWithdrawDepositScreen()))
            .bounds(x, y, 130, 20)
            .build();
        this.addRenderableWidget(button);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lifesmp$drawHearts(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ClientLivesState.hasData()) return;
        int lives = ClientLivesState.lives();
        int x = this.leftPos + 28;
        int y = this.topPos + 177;
        ItemStack shard = LifeItems.createLifeShard(1);

        for (int i = 0; i < 10; i++) {
            int hx = x + i * 9;
            if (i < lives - 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_FULL, hx, y, 9, 9);
            } else if (i == lives - 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_CONTAINER, hx, y, 9, 9);
                g.item(shard, hx - 4, y - 4);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_CONTAINER, hx, y, 9, 9);
            }
        }
        if (lives > 10) {
            int ox = x + 10 * 9 + 4;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_FULL, ox, y, 9, 9);
            String count = String.valueOf(lives - 10);
            Minecraft mc = Minecraft.getInstance();
            int w = mc.font.width(count);
            g.text(mc.font, Component.literal(count),
                ox + 5 - w / 2, y - 2, 0xFFFFFFFF, true);
        }
    }
}
