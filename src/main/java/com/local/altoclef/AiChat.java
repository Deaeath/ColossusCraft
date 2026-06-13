package com.local.altoclef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class AiChat {
    // Key is loaded at runtime from %APPDATA%/.minecraft/colossuscraft-ai.key (plain text, one line)
    private static String API_KEY = null;
    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private AiChat() {}

    private static String loadKey() {
        if (API_KEY != null) return API_KEY;
        try {
            // Look for key file next to the game dir: .minecraft/colossuscraft-ai.key
            java.nio.file.Path keyFile = Minecraft.getInstance().gameDirectory.toPath().resolve("colossuscraft-ai.key");
            if (java.nio.file.Files.exists(keyFile)) {
                API_KEY = java.nio.file.Files.readString(keyFile).trim().replace("﻿", "");
            }
        } catch (Exception ignored) {}
        return API_KEY;
    }

    public static int query(String userMessage) {
        if (loadKey() == null || API_KEY.isBlank()) {
            say("AI: no API key found. Create colossuscraft-ai.key in your game folder.");
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        say("...");
        // Screenshot must happen on the game/render thread
        mc.execute(() -> {
            NativeImage img = null;
            Path tmp = null;
            try {
                img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                tmp = Files.createTempFile("cc_ai_", ".png");
                img.writeToFile(tmp.toFile());
                byte[] pngBytes = Files.readAllBytes(tmp);
                String b64 = Base64.getEncoder().encodeToString(pngBytes);
                final Path tmpFinal = tmp;
                new Thread(() -> {
                    try {
                        call(userMessage, b64);
                    } finally {
                        try { Files.deleteIfExists(tmpFinal); } catch (Exception ignored) {}
                    }
                }, "cc-ai").start();
            } catch (Exception e) {
                say("Screenshot failed: " + e.getMessage());
                if (tmp != null) { try { Files.deleteIfExists(tmp); } catch (Exception ignored) {} }
            } finally {
                if (img != null) img.close();
            }
        });
        return 1;
    }

    private static void call(String userMessage, String b64) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", "gpt-4o");
            ArrayNode msgs = body.putArray("messages");

            ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content",
                "You are ColossusCraft AI, an assistant embedded inside the ColossusCraft mod for Minecraft 1.21.1 (NeoForge, All the Mods 10). " +
                "You can see the player's screen in real time via a screenshot attached to every message. " +
                "Be friendly, concise, and helpful. Plain text only — no markdown. " +
                "\n\nAbout ColossusCraft: it is a client-side automation mod controlled entirely via chat commands starting with /cc (alias /colossuscraft). " +
                "It automates pathfinding, combat, mining, item gathering, food, elytra travel, and ATM Star quest progression. " +
                "\n\nKey commands:" +
                "\n/cc on|off|stop|status — start, stop, or check the bot" +
                "\n/cc get <item> [count] — gather any item by name or registry ID (e.g. /cc get elytra, /cc get allthemodium:allthemodium_ingot 64)" +
                "\n/cc mine <block> [count] — mine a block type; tab-complete shows all mod blocks (e.g. /cc mine allthemodium:allthemodium_ore 64)" +
                "\n/cc mine <count> <block1> <block2> ... — mine multiple block types at once" +
                "\n/cc goto <x> <y> <z> | <player> | entity <type> | item <type> — pathfind to a location, player, entity, or dropped item" +
                "\n/cc follow <player> — follow a player continuously" +
                "\n/cc kill <entity> — hunt and kill an entity type" +
                "\n/cc food <units> — gather food" +
                "\n/cc come — pathfind to the nearest other player" +
                "\n/cc escape — emergency teleport home immediately" +
                "\n/cc equip [tier] — auto-equip best available armor/weapons (tiers: netherite, diamond, iron, gold, leather)" +
                "\n/cc locate <structure> — find and path to a structure" +
                "\n/cc sneak on|off|status — toggle sneak; auto-enabled in the deep dark biome" +
                "\n/cc warden fight — trigger a shrieker, dig a trap pit during the warden's 30-second emerge animation, retreat 21+ blocks, bow it down" +
                "\n/cc warden fight gather — same but collects bow, arrows, hoe, iron blocks, pumpkins first" +
                "\n/cc warden golems [count] — spawn iron golem squad near the warden as a distraction" +
                "\n/cc warden stop — cancel warden task" +
                "\n/cc bow on|off|status — tick-driven bow/crossbow aimbot with ballistic aim and target prediction" +
                "\n/cc home on|off|threshold <hearts>|status — configure emergency /home (fires automatically at low health, lava, void, drowning)" +
                "\n/cc ai <message> — that's this command; sends a screenshot + message to GPT-4o" +
                "\n/cc gamma [value] — set fullbright" +
                "\n/cc deposit|inventory|findchest — storage helpers" +
                "\n/cc nav <cmd> — raw Baritone pathfinder command" +
                "\n/cc exec <cmd> — raw internal AltoClef command" +
                "\n\nDeep dark / ancient city: bot auto-sneaks, freezes if warden anger >= 70, suppresses sprinting. " +
                "Warden trap strategy: find shrieker → optionally build iron golems → trigger → sprint to spawning warden → mine 2x2 pit around it while it emerges → retreat → bow down. " +
                "Emergency /home fires as a safety net throughout at 3 hearts. " +
                "\n\nIf asked what ColossusCraft can do, explain the above. If asked how to use a command, explain it clearly. " +
                "If the screenshot shows the game, describe what's relevant to the question.");

            ObjectNode user = msgs.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");

            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", userMessage);

            ObjectNode imgPart = content.addObject();
            imgPart.put("type", "image_url");
            imgPart.putObject("image_url").put("url", "data:image/png;base64," + b64);

            body.put("max_tokens", 500);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(resp.body());

            if (root.has("error")) {
                String err = root.path("error").path("message").asText("unknown error");
                Minecraft.getInstance().execute(() -> say("AI: error - " + err));
                return;
            }

            String reply = root.path("choices").path(0).path("message").path("content").asText("(no response)");
            Minecraft.getInstance().execute(() -> say("[AI] " + reply));
        } catch (Exception e) {
            Minecraft.getInstance().execute(() -> say("AI: request failed - " + e.getMessage()));
        }
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
