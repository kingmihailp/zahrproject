package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Client-side handler for the "Это точно не вирус?" voting event.
 *
 * On activation:
 *   1. Downloads the block/item atlas texture from the GPU (mip level 0).
 *   2. Saves each sprite's original pixel region.
 *   3. Shuffles pixel regions among sprites of equal size and re-uploads.
 *
 * On deactivation / disconnect: restores original pixel data.
 *
 * Sprites are grouped by their pixel dimensions before shuffling so that
 * each upload region always receives exactly the right number of bytes.
 * Most block/item sprites are 16 × 16, so nearly all of them get shuffled.
 */
@OnlyIn(Dist.CLIENT)
public class RandomTextureHandler {

    private static boolean active      = false;
    private static boolean initialized = false;

    private static int atlasGlId = -1;
    private static int atlasW    = 0;
    private static int atlasH    = 0;

    /** Pixel bounds of each collected sprite in the atlas: [x, y, w, h]. */
    private static final List<int[]>  spriteRegions    = new ArrayList<>();
    /** Original RGBA byte data for each sprite (parallel to spriteRegions). */
    private static final List<byte[]> originalSprites  = new ArrayList<>();

    // ── Packet handler entry point ────────────────────────────────────────────

    /** Called on the client/render thread by {@link com.zahrproject.votingmod.network.RandomTexturePacket}. */
    public static void setActive(boolean value) {
        if (value) {
            if (!initialized) tryInitialize();
            if (initialized) applyRandomization();
        } else {
            if (initialized && active) {
                restore();
                // Reset so the next activation picks up a fresh atlas
                // (handles resource-pack changes between events)
                initialized = false;
                spriteRegions.clear();
                originalSprites.clear();
            }
        }
        active = value;
    }

    // ── Disconnect: never leave the atlas in a shuffled state ─────────────────

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        boolean wasActive = active;
        active = false;
        if (wasActive && initialized) {
            // LoggingOut can fire on the Netty thread; GL calls require the render thread.
            // recordRenderCall() queues the work to run on the very next render frame,
            // before the atlas could be invalidated, so the restore is always safe.
            Runnable doRestore = () -> {
                try { restore(); } finally {
                    initialized = false;
                    spriteRegions.clear();
                    originalSprites.clear();
                }
            };
            if (RenderSystem.isOnRenderThread()) doRestore.run();
            else RenderSystem.recordRenderCall(doRestore::run);
        } else {
            initialized = false;
            spriteRegions.clear();
            originalSprites.clear();
        }
    }

    // ── Initialization: download the atlas from GPU once per activation ───────

    private static void tryInitialize() {
        try {
            Minecraft mc    = Minecraft.getInstance();
            TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            atlasGlId = atlas.getId();

            Map<ResourceLocation, TextureAtlasSprite> spriteMap = findSpriteMap(atlas);
            if (spriteMap.isEmpty()) return;

            RenderSystem.bindTexture(atlasGlId);
            atlasW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            atlasH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            // Guard against unexpectedly large atlases to avoid huge allocations
            if (atlasW <= 0 || atlasH <= 0 || atlasW > 8192 || atlasH > 8192) return;

            // Download the entire atlas at mip level 0 (RGBA, unsigned byte)
            ByteBuffer atlasBuf = ByteBuffer.allocateDirect(atlasW * atlasH * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, atlasBuf);
            atlasBuf.rewind();

            for (TextureAtlasSprite spr : spriteMap.values()) {
                // Derive pixel-exact bounds from the UV floats
                int x = Math.round(spr.getU0() * atlasW);
                int y = Math.round(spr.getV0() * atlasH);
                int w = Math.max(1, Math.round((spr.getU1() - spr.getU0()) * atlasW));
                int h = Math.max(1, Math.round((spr.getV1() - spr.getV0()) * atlasH));
                if (x < 0 || y < 0 || x + w > atlasW || y + h > atlasH) continue;

                byte[] pixels = new byte[w * h * 4];
                for (int row = 0; row < h; row++) {
                    atlasBuf.position(((y + row) * atlasW + x) * 4);
                    atlasBuf.get(pixels, row * w * 4, w * 4);
                }
                spriteRegions.add(new int[]{x, y, w, h});
                originalSprites.add(pixels);
            }

            initialized = !spriteRegions.isEmpty();
        } catch (Exception e) {
            spriteRegions.clear();
            originalSprites.clear();
        }
    }

    // ── Apply: shuffle pixels within size groups ──────────────────────────────

    private static void applyRandomization() {
        int n = spriteRegions.size();
        if (n == 0) return;

        // Group sprite indices by pixel dimensions (w << 16 | h as key)
        Map<Long, List<Integer>> bySize = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int[] r   = spriteRegions.get(i);
            long key  = ((long) r[2] << 32) | (r[3] & 0xFFFFFFFFL);
            bySize.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        // Build shuffled assignment: shuffled[i] = which original sprite's pixels to put at position i
        byte[][] shuffled = originalSprites.toArray(new byte[0][]);
        Random rng = new Random();
        for (List<Integer> group : bySize.values()) {
            if (group.size() < 2) continue;
            List<byte[]> groupPixels = new ArrayList<>();
            for (int idx : group) groupPixels.add(originalSprites.get(idx));
            Collections.shuffle(groupPixels, rng);
            for (int i = 0; i < group.size(); i++) shuffled[group.get(i)] = groupPixels.get(i);
        }

        uploadSprites(shuffled);
    }

    // ── Restore: put original pixels back ────────────────────────────────────

    private static void restore() {
        uploadSprites(originalSprites.toArray(new byte[0][]));
    }

    // ── GL helpers ────────────────────────────────────────────────────────────

    private static void uploadSprites(byte[][] pixels) {
        RenderSystem.bindTexture(atlasGlId);
        for (int i = 0; i < spriteRegions.size(); i++) {
            int[]      r   = spriteRegions.get(i);
            ByteBuffer buf = ByteBuffer.allocateDirect(pixels[i].length);
            buf.put(pixels[i]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    r[0], r[1], r[2], r[3],
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
    }

    /**
     * Finds the {@code Map<ResourceLocation, TextureAtlasSprite>} field inside
     * {@link TextureAtlas} by inspecting field types rather than relying on a
     * specific obfuscated name.
     */
    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, TextureAtlasSprite> findSpriteMap(TextureAtlas atlas) {
        for (Field f : TextureAtlas.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            try {
                Object val = f.get(atlas);
                if (!(val instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Map.Entry<?, ?> e = m.entrySet().iterator().next();
                if (e.getKey() instanceof ResourceLocation && e.getValue() instanceof TextureAtlasSprite)
                    return (Map<ResourceLocation, TextureAtlasSprite>) m;
            } catch (Exception ignored) {}
        }
        return Map.of();
    }
}
