package com.local.altoclef;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class InventoryView {
    private static final int SLOT = 18;
    private static final int GRID = 9;
    private static final int PANEL_WIDTH = 202;
    private static final int PANEL_HEIGHT = 100;
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.colossuscraft.invview",
            InputConstants.KEY_I,
            "key.categories.colossuscraft"
    );

    private static boolean initialized;
    private static boolean visible;

    private InventoryView() {
    }

    public static void init(IEventBus modBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        modBus.addListener(InventoryView::registerKeys);
        NeoForge.EVENT_BUS.addListener(InventoryView::clientTick);
        NeoForge.EVENT_BUS.addListener(InventoryView::render);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("invview")
                .executes(ctx -> toggle())
                .then(Commands.literal("toggle").executes(ctx -> toggle()))
                .then(Commands.literal("on").executes(ctx -> setVisible(true)))
                .then(Commands.literal("off").executes(ctx -> setVisible(false)))
                .then(Commands.argument("visible", BoolArgumentType.bool()).executes(ctx -> setVisible(BoolArgumentType.getBool(ctx, "visible"))));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) {
            setVisible(!visible);
        }
    }

    private static int toggle() {
        return setVisible(!visible);
    }

    private static int setVisible(boolean value) {
        visible = value;
        say("Inventory view: " + (visible ? "ON" : "OFF"));
        return 1;
    }

    private static String botStatus() {
        try {
            adris.altoclef.AltoClefPort port = adris.altoclef.platform.NeoForgeAltoClefMod.port();
            if (!port.running()) return "idle (@stop / off)";
            adris.altoclef.tasksystem.TaskChain chain = port.core().getTaskRunner().getCurrentTaskChain();
            if (chain == null) return "idle";
            String s = chain.getName();
            if (chain instanceof adris.altoclef.chains.SingleTaskChain stc && stc.getCurrentTask() != null) {
                s += ": " + stc.getCurrentTask();
            }
            return s.length() > 44 ? s.substring(0, 44) : s;
        } catch (Exception e) {
            return "?";
        }
    }

    private static void render(RenderGuiEvent.Post event) {
        if (!visible) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        Font font = mc.font;
        // Just a compact "what is the bot doing" line, top-left, clear of the minimap.
        // No fake inventory — open your real inventory/menus normally while it plays.
        String label = "Bot: " + botStatus();
        int w = font.width(label) + 8;
        int x = 4;
        int y = 4;
        gui.fill(x, y, x + w, y + 12, 0xB0101010);
        gui.drawString(font, label, x + 4, y + 2, 0xFF7CFC7C, false);
    }

    private static void drawSlot(GuiGraphics gui, Font font, int x, int y, ItemStack stack, boolean selected) {
        int bg = selected ? 0xCCF2E36B : 0xAA2B2B2B;
        int edge = selected ? 0xFFF2E36B : 0x66454545;
        gui.fill(x, y, x + 18, y + 18, bg);
        gui.renderOutline(x, y, 18, 18, edge);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        gui.renderItem(stack, x + 1, y + 1);
        gui.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
