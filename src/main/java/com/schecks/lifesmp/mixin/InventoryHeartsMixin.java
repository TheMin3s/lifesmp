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
 * Overlays a clickable hearts box onto the player display box (the 3D player
 * preview) of the vanilla inventory GUI. Shows up to ten hardcore-heart icons,
 * laid out as two rows of five to fit the narrow preview area: full for lives
 * you have, blank for lives you've lost, with the rightmost full slot replaced
 * by the Life Shard item icon (the "next one to be lost"). Lives above ten get
 * an extra heart with the overflow count drawn on top. Clicking the box opens
 * the withdraw/deposit screen so you can shuffle lives ↔ shards without typing
 * the commands.
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

    // Top-left of the heart grid, overlaid on the lower part of the player
    // display box. Two rows of five 9px hearts span 45px wide / 18px tall,
    // which fits the ~49px-wide vanilla player preview.
    @Unique private int lifesmp$heartGridX() { return this.leftPos + 27; }
    @Unique private int lifesmp$heartGridY() { return this.topPos + 56; }

    @Inject(method = "init", at = @At("TAIL"))
    private void lifesmp$initHeartsBox(CallbackInfo ci) {
        if (!ClientLivesState.hasData()) return;
        // Button sits behind the heart grid so the whole box stays clickable.
        int x = lifesmp$heartGridX() - 2;
        int y = lifesmp$heartGridY() - 2;
        Button button = Button.builder(Component.empty(),
                b -> Minecraft.getInstance().setScreen(new LivesWithdrawDepositScreen()))
            .bounds(x, y, 49, 22)
            .build();
        this.addRenderableWidget(button);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lifesmp$drawHearts(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ClientLivesState.hasData()) return;
        int lives = ClientLivesState.lives();
        int x = lifesmp$heartGridX();
        int y = lifesmp$heartGridY();
        ItemStack shard = LifeItems.createLifeShard(1);

        // Ten hearts in two rows of five.
        for (int i = 0; i < 10; i++) {
            int hx = x + (i % 5) * 9;
            int hy = y + (i / 5) * 9;
            if (i < lives - 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_FULL, hx, hy, 9, 9);
            } else if (i == lives - 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_CONTAINER, hx, hy, 9, 9);
                g.item(shard, hx - 4, hy - 4);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_CONTAINER, hx, hy, 9, 9);
            }
        }
        if (lives > 10) {
            // Overflow heart + count sits just below the two rows.
            int ox = x + 2 * 9;
            int oy = y + 18;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_FULL, ox, oy, 9, 9);
            String count = String.valueOf(lives - 10);
            Minecraft mc = Minecraft.getInstance();
            int w = mc.font.width(count);
            g.text(mc.font, Component.literal(count),
                ox + 5 - w / 2, oy - 2, 0xFFFFFFFF, true);
        }
    }
}
