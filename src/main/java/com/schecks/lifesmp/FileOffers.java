package com.schecks.lifesmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of files the server offers to players, by name -> path (relative to
 * the server root). Players pull them with /lives get; the bytes travel over
 * a FileTransferPayload and the client mod saves them, with confirmation, into
 * a sandboxed downloads folder.
 *
 * Persisted to config/lifesmp/offers.json. Server/common-safe — no client deps.
 */
public final class FileOffers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> offers = new LinkedHashMap<>(); // name -> relative path

    private static volatile Path serverRoot;
    private static volatile Path offersFile;

    private FileOffers() {}

    /** Registers the large S2C payload. Call once from the main entrypoint. */
    public static void registerPayload() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(
            FileTransferPayload.TYPE, FileTransferPayload.CODEC,
            FileTransferPayload.MAX_BYTES + 4096);
    }

    /** Loads the offer list. Call at server start. */
    public static synchronized void init(MinecraftServer server) {
        serverRoot = server.getServerDirectory().toAbsolutePath().normalize();
        offersFile = serverRoot.resolve("config").resolve("lifesmp").resolve("offers.json");
        offers.clear();
        if (Files.exists(offersFile)) {
            try {
                JsonObject obj = JsonParser.parseString(
                    Files.readString(offersFile, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : obj.keySet()) {
                    offers.put(key, obj.get(key).getAsString());
                }
            } catch (Exception e) {
                LifeLog.warn("[lifesmp] offers.json unreadable ({}), starting empty", e.getMessage());
            }
        }
    }

    private static synchronized void save() {
        if (offersFile == null) return;
        try {
            Files.createDirectories(offersFile.getParent());
            JsonObject obj = new JsonObject();
            offers.forEach(obj::addProperty);
            Files.writeString(offersFile, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LifeLog.warn("[lifesmp] failed to write offers.json: {}", e.getMessage());
        }
    }

    /**
     * Adds an offer for the file at {@code relativePath} (under the server root).
     * The offer name is the file's base name. Returns "ok:&lt;name&gt;" or
     * "error:&lt;reason&gt;".
     */
    public static synchronized String addOffer(String relativePath) {
        if (serverRoot == null) return "error:server not ready";
        Path target = serverRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!target.startsWith(serverRoot)) return "error:path escapes the server directory";
        if (!Files.isRegularFile(target)) return "error:not a file: " + relativePath;
        long size;
        try { size = Files.size(target); } catch (IOException e) { return "error:" + e.getMessage(); }
        if (size > FileTransferPayload.MAX_BYTES) {
            return "error:file is " + size + " bytes; the transfer cap is "
                + FileTransferPayload.MAX_BYTES + " (8 MB)";
        }
        String name = target.getFileName().toString();
        if (offers.containsKey(name)) {
            return "error:a file named '" + name + "' is already offered";
        }
        offers.put(name, serverRoot.relativize(target).toString());
        save();
        return "ok:" + name;
    }

    public static synchronized boolean removeOffer(String name) {
        boolean removed = offers.remove(name) != null;
        if (removed) save();
        return removed;
    }

    /** Snapshot of all offers (name -> relative path). */
    public static synchronized Map<String, String> all() {
        return new LinkedHashMap<>(offers);
    }

    /** Resolves an offer name to its on-disk file, or null if invalid/missing. */
    public static synchronized Path resolve(String name) {
        String rel = offers.get(name);
        if (rel == null || serverRoot == null) return null;
        Path p = serverRoot.resolve(rel).toAbsolutePath().normalize();
        if (!p.startsWith(serverRoot) || !Files.isRegularFile(p)) return null;
        return p;
    }

    /**
     * Sends an offered file to a player's client. Returns "ok:&lt;bytes&gt;" or
     * "error:&lt;reason&gt;". Vanilla clients (no mod channel) get an error.
     */
    public static String sendTo(ServerPlayer player, String name) {
        Path file = resolve(name);
        if (file == null) return "error:no such offered file: " + name;
        if (!ServerPlayNetworking.canSend(player, FileTransferPayload.TYPE)) {
            return "error:you need the LifeSMP client mod installed to receive files";
        }
        byte[] data;
        try {
            long size = Files.size(file);
            if (size > FileTransferPayload.MAX_BYTES) return "error:file too large to send";
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return "error:read failed: " + e.getMessage();
        }
        ServerPlayNetworking.send(player, new FileTransferPayload(name, data));
        return "ok:" + data.length;
    }
}
