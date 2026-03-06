package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
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
 *   1. Reads each sprite's original pixel data from {@link SpriteContents#originalImage}
 *      (the NativeImage kept in RAM by Minecraft), NOT from the GPU.
 *   2. Saves the data alongside the sprite's atlas position.
 *   3. Shuffles pixel regions among sprites of equal size and uploads via glTexSubImage2D.
 *
 * Why NativeImage instead of glGetTexImage:
 *   glGetTexImage downloads whatever is currently on the GPU.  If the deferred
 *   atlas-restore from a previous disconnect races with a new activation (both
 *   land in the same Minecraft.execute() queue), glGetTexImage may capture the
 *   SHUFFLED atlas and save it as "original".  Each rejoin then accumulates more
 *   corruption, producing the characteristic black-with-coloured-lines artefact.
 *   NativeImage.originalImage always holds the true source pixels regardless of
 *   GPU state, making every activation start from a clean baseline.
 *
 * On deactivation / disconnect: restores original pixel data via glTexSubImage2D.
 */
@OnlyIn(Dist.CLIENT)
public class RandomTextureHandler {

    private static boolean active      = false;
    private static boolean initialized = false;

    private static int atlasGlId = -1;
    private static int atlasW    = 0;
    private static int atlasH    = 0;

    /** Atlas position of each collected sprite: [x, y, w, h]. */
    private static final List<int[]>  spriteRegions   = new ArrayList<>();
    /** Original RGBA bytes per sprite, read from NativeImage (parallel to spriteRegions). */
    private static final List<byte[]> originalSprites = new ArrayList<>();

    // ── Packet handler entry point ────────────────────────────────────────────

    public static void setActive(boolean value) {
        if (value) {
            if (!initialized) tryInitialize();
            if (initialized) applyRandomization();
        } else {
            if (initialized && active) {
                restore();
                initialized = false;
                spriteRegions.clear();
                originalSprites.clear();
            }
        }
        active = value;
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        boolean wasActive = active;
        active = false;
        if (wasActive && initialized) {
            // LoggingOut can fire from the integrated-server thread.
            // Capture everything before clearing, then schedule the GL restore
            // via Minecraft.execute() — a thread-safe queue on the render/main thread.
            final int          capturedId      = atlasGlId;
            final List<int[]>  capturedRegions = new ArrayList<>(spriteRegions);
            final List<byte[]> capturedPixels  = new ArrayList<>(originalSprites);
            initialized = false;
            spriteRegions.clear();
            originalSprites.clear();

            Minecraft.getInstance().execute(() -> {
                try {
                    int maxLen = 0;
                    for (byte[] p : capturedPixels) maxLen = Math.max(maxLen, p.length);
                    if (maxLen == 0) return;

                    ByteBuffer buf = ByteBuffer.allocateDirect(maxLen);
                    RenderSystem.bindTexture(capturedId);
                    for (int i = 0; i < capturedRegions.size(); i++) {
                        int[] r = capturedRegions.get(i);
                        buf.clear();
                        buf.put(capturedPixels.get(i)).flip();
                        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                                r[0], r[1], r[2], r[3],
                                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                    }
                } catch (Exception ignored) {}
            });
        } else {
            initialized = false;
            spriteRegions.clear();
            originalSprites.clear();
        }
    }

    // ── Initialization: read sprite pixels from NativeImage (RAM) ─────────────

    private static void tryInitialize() {
        try {
            Minecraft mc    = Minecraft.getInstance();
            TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            atlasGlId = atlas.getId();

            Map<ResourceLocation, TextureAtlasSprite> spriteMap = findSpriteMap(atlas);
            if (spriteMap.isEmpty()) return;

            // Atlas dimensions are needed for UV → pixel conversion.
            // glGetTexLevelParameteri is a tiny, harmless GL query (no data download).
            RenderSystem.bindTexture(atlasGlId);
            atlasW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            atlasH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (atlasW <= 0 || atlasH <= 0 || atlasW > 8192 || atlasH > 8192) return;

            for (TextureAtlasSprite spr : spriteMap.values()) {
                int x = Math.round(spr.getU0() * atlasW);
                int y = Math.round(spr.getV0() * atlasH);
                int w = Math.max(1, Math.round((spr.getU1() - spr.getU0()) * atlasW));
                int h = Math.max(1, Math.round((spr.getV1() - spr.getV0()) * atlasH));
                if (x < 0 || y < 0 || x + w > atlasW || y + h > atlasH) continue;

                // Read from NativeImage in RAM — always original, never shuffled.
                byte[] pixels = readFromNativeImage(spr, w, h);
                if (pixels == null) continue;

                spriteRegions.add(new int[]{x, y, w, h});
                originalSprites.add(pixels);
            }

            initialized = !spriteRegions.isEmpty();
        } catch (Exception e) {
            spriteRegions.clear();
            originalSprites.clear();
        }
    }

    /**
     * Reads a sprite's pixels from its {@link SpriteContents}.originalImage (NativeImage).
     * Returns RGBA bytes suitable for {@code glTexSubImage2D(GL_RGBA, GL_UNSIGNED_BYTE)}.
     * Returns {@code null} if the image is unavailable or the format is unsupported.
     */
    private static byte[] readFromNativeImage(TextureAtlasSprite sprite, int w, int h) {
        try {
            // SpriteContents is accessible via the public contents() method in 1.20.1,
            // but we use type-based reflection to stay obfuscation-safe.
            SpriteContents contents = findFieldOfType(sprite, SpriteContents.class);
            if (contents == null) return null;

            NativeImage image = findFieldOfType(contents, NativeImage.class);
            if (image == null) return null;

            int imgW = image.getWidth();
            int imgH = image.getHeight();
            // For animated sprites, originalImage height = frameHeight × frameCount.
            // We only need the first frame (rows 0..h-1), which is what the atlas shows.
            int useW = Math.min(w, imgW);
            int useH = Math.min(h, imgH);

            byte[] pixels = new byte[w * h * 4]; // zero-initialised (transparent black for gaps)
            for (int py = 0; py < useH; py++) {
                for (int px = 0; px < useW; px++) {
                    // getPixelRGBA returns R in the lowest byte on x86 (little-endian memGetInt).
                    // Decomposing into bytes matches GL_RGBA / GL_UNSIGNED_BYTE memory order.
                    int rgba = image.getPixelRGBA(px, py);
                    int off  = (py * w + px) * 4;
                    pixels[off]     = (byte) rgba;
                    pixels[off + 1] = (byte)(rgba >> 8);
                    pixels[off + 2] = (byte)(rgba >> 16);
                    pixels[off + 3] = (byte)(rgba >> 24);
                }
            }
            return pixels;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Apply: shuffle pixels within same-size groups ─────────────────────────

    private static void applyRandomization() {
        int n = spriteRegions.size();
        if (n == 0) return;

        Map<Long, List<Integer>> bySize = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int[] r  = spriteRegions.get(i);
            long key = ((long) r[2] << 32) | (r[3] & 0xFFFFFFFFL);
            bySize.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

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

    // ── Restore ───────────────────────────────────────────────────────────────

    private static void restore() {
        uploadSprites(originalSprites.toArray(new byte[0][]));
    }

    // ── GL upload ─────────────────────────────────────────────────────────────

    private static void uploadSprites(byte[][] pixels) {
        int maxLen = 0;
        for (byte[] p : pixels) maxLen = Math.max(maxLen, p.length);
        if (maxLen == 0) return;

        ByteBuffer buf = ByteBuffer.allocateDirect(maxLen);
        RenderSystem.bindTexture(atlasGlId);
        for (int i = 0; i < spriteRegions.size(); i++) {
            int[] r = spriteRegions.get(i);
            buf.clear();
            buf.put(pixels[i]).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    r[0], r[1], r[2], r[3],
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
    }

    // ── Reflection helpers (obfuscation-safe — find by type, not name) ────────

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

    /** Finds the first declared field (walking the class hierarchy) whose type is assignable to {@code type}. */
    @SuppressWarnings("unchecked")
    private static <T> T findFieldOfType(Object obj, Class<T> type) {
        for (Class<?> cls = obj.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (!type.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (type.isInstance(val)) return type.cast(val);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
