package com.schecks.lifesmp.client;

import com.schecks.lifesmp.DirListingPayload;
import com.schecks.lifesmp.DirRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modded-client file browser for /lives op dir. Each DirListingPayload from
 * the server opens a fresh browser for that directory; navigating sends a
 * DirRequestPayload and the reply re-opens the screen. Files can be pulled
 * down through the existing /lives op get command.
 */
public final class DirBrowserScreen extends Screen {
    private static final int ENTRY_HEIGHT = 14;

    private final String path;                            // relative to root, "" = root
    private final List<DirListingPayload.Entry> entries;
    private DirList list;
    private Button openButton;
    private Button downloadButton;
    private Button upButton;

    public DirBrowserScreen(String path, List<DirListingPayload.Entry> entries) {
        super(Component.literal("Server files — /" + path));
        this.path = path;
        this.entries = entries;
    }

    @Override
    protected void init() {
        int listTop = 28;
        int listHeight = Math.max(ENTRY_HEIGHT, this.height - listTop - 36);

        list = new DirList(this.minecraft, this.width, listHeight, listTop, ENTRY_HEIGHT);
        for (DirListingPayload.Entry e : entries) {
            list.add(new DirEntry(e));
        }
        addRenderableWidget(list);

        int by = this.height - 28;
        upButton = Button.builder(Component.literal("Up"), b -> navigateUp())
            .bounds(8, by, 60, 20).build();
        openButton = Button.builder(Component.literal("Open"), b -> openSelected())
            .bounds(72, by, 80, 20).build();
        downloadButton = Button.builder(Component.literal("Download"), b -> downloadSelected())
            .bounds(156, by, 100, 20).build();
        addRenderableWidget(upButton);
        addRenderableWidget(openButton);
        addRenderableWidget(downloadButton);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(this.width - 88, by, 80, 20).build());

        upButton.active = !path.isEmpty();
    }

    private DirListingPayload.Entry selected() {
        DirEntry e = list == null ? null : list.getSelected();
        return e == null ? null : e.data;
    }

    private void request(String newPath) {
        ClientPlayNetworking.send(new DirRequestPayload(newPath));
    }

    private void navigateUp() {
        if (path.isEmpty()) return;
        int slash = path.lastIndexOf('/');
        request(slash >= 0 ? path.substring(0, slash) : "");
    }

    private void openSelected() {
        DirListingPayload.Entry e = selected();
        if (e == null || !e.directory()) return;
        request(path.isEmpty() ? e.name() : path + "/" + e.name());
    }

    private void downloadSelected() {
        DirListingPayload.Entry e = selected();
        if (e == null || e.directory()) return;
        String full = path.isEmpty() ? e.name() : path + "/" + e.name();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand("lives op get " + full);
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        DirListingPayload.Entry sel = selected();
        openButton.active = sel != null && sel.directory();
        downloadButton.active = sel != null && !sel.directory();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (entries.isEmpty()) {
            g.centeredText(this.font, Component.literal("(empty directory)"),
                this.width / 2, this.height / 2, 0xFF888888);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String humanBytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.1f MB", n / (1024.0 * 1024.0));
    }

    // ----- list widget -----

    private static final class DirList extends ObjectSelectionList<DirEntry> {
        DirList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        /** Public alias for the protected addEntry, callable from the screen. */
        void add(DirEntry entry) {
            addEntry(entry);
        }
    }

    private static final class DirEntry extends ObjectSelectionList.Entry<DirEntry> {
        private final DirListingPayload.Entry data;

        DirEntry(DirListingPayload.Entry data) {
            this.data = data;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            String label = data.directory() ? data.name() + "/" : data.name();
            int color = data.directory() ? 0xFF55FFFF : 0xFFFFFFFF;
            g.text(mc.font, Component.literal(label),
                getContentX() + 2, getContentY() + 2, color, false);
            if (!data.directory()) {
                String size = humanBytes(data.size());
                int w = mc.font.width(size);
                g.text(mc.font, Component.literal(size),
                    getContentRight() - w - 2, getContentY() + 2, 0xFF777777, false);
            }
        }

        @Override
        public Component getNarration() {
            return Component.literal(data.name());
        }
    }
}
