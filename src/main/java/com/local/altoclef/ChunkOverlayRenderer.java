package com.local.altoclef;

import adris.altoclef.trackers.ChunkScanCache;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.awt.Color;

/**
 * Draws per-chunk-column scan overlays.
 *
 * HAS_ORE → green AABB outline + faint green fill
 * CLEAN   → red AABB outline + faint red fill
 * UNKNOWN → not drawn
 *
 * Toggle with /cc scan overlay [on|off].
 * The surfaceY is the Y where the flat cap of each chunk's AABB is drawn.
 */
public final class ChunkOverlayRenderer {

    public static boolean enabled = false;
    private static ChunkScanCache cache;
    private static int surfaceY = 316;

    private ChunkOverlayRenderer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ChunkOverlayRenderer::onRenderLevel);
    }

    public static void setCache(ChunkScanCache c, int y) {
        cache = c;
        surfaceY = y;
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || cache == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        int playerCx = (int) Math.floor(cam.x) >> 4;
        int playerCz = (int) Math.floor(cam.z) >> 4;
        int viewChunks = Math.min(mc.options.renderDistance().get(), 16);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // ── Outlined bounding boxes via Baritone's IRenderer (batched by color) ──
        BufferBuilder oreLines = IRenderer.startLines(new Color(0, 255, 50, 200), 1.0f, 1.5f, false);
        BufferBuilder cleanLines = IRenderer.startLines(new Color(255, 40, 40, 160), 1.0f, 1.5f, false);
        boolean hasOreLines = false, hasCleanLines = false;

        for (int dcx = -viewChunks; dcx <= viewChunks; dcx++) {
            for (int dcz = -viewChunks; dcz <= viewChunks; dcz++) {
                int cx = playerCx + dcx;
                int cz = playerCz + dcz;
                ChunkScanCache.State state = cache.get(cx, cz);
                if (state == ChunkScanCache.State.UNKNOWN) continue;

                AABB box = new AABB(cx * 16, surfaceY, cz * 16,
                                    cx * 16 + 16, surfaceY + 0.4, cz * 16 + 16);
                if (state == ChunkScanCache.State.HAS_ORE) {
                    IRenderer.emitAABB(oreLines, poseStack, box);
                    hasOreLines = true;
                } else {
                    IRenderer.emitAABB(cleanLines, poseStack, box);
                    hasCleanLines = true;
                }
            }
        }

        if (hasOreLines)  IRenderer.endLines(oreLines,  false);
        if (hasCleanLines) IRenderer.endLines(cleanLines, false);

        // ── Translucent fills via direct BufferUploader ──
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder fills = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasFills = false;

        for (int dcx = -viewChunks; dcx <= viewChunks; dcx++) {
            for (int dcz = -viewChunks; dcz <= viewChunks; dcz++) {
                int cx = playerCx + dcx;
                int cz = playerCz + dcz;
                ChunkScanCache.State state = cache.get(cx, cz);
                if (state == ChunkScanCache.State.UNKNOWN) continue;

                int r, g, b, a;
                if (state == ChunkScanCache.State.HAS_ORE) {
                    r = 0; g = 220; b = 50; a = 40;
                } else {
                    r = 220; g = 30; b = 30; a = 25;
                }

                // Camera-relative coordinates (poseStack already has -cam translate, but
                // addVertex(float,float,float) ignores the stack — supply raw cam-relative coords).
                float wx0 = (float)(cx * 16 - cam.x);
                float wx1 = (float)(cx * 16 + 16 - cam.x);
                float wy  = (float)(surfaceY + 0.05 - cam.y);
                float wz0 = (float)(cz * 16 - cam.z);
                float wz1 = (float)(cz * 16 + 16 - cam.z);

                fills.addVertex(wx0, wy, wz0).setColor(r, g, b, a);
                fills.addVertex(wx0, wy, wz1).setColor(r, g, b, a);
                fills.addVertex(wx1, wy, wz1).setColor(r, g, b, a);
                fills.addVertex(wx1, wy, wz0).setColor(r, g, b, a);
                hasFills = true;
            }
        }

        if (hasFills) {
            try (MeshData mesh = fills.buildOrThrow()) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}
