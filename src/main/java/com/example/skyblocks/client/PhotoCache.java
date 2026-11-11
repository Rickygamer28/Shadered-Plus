package com.example.skyblocks.client;

import com.example.skyblocks.PhotoPayloads;
import com.example.skyblocks.PhotoStore;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Photos' pictures on this client: asked of the server the first time one's seen, then kept as textures. */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PhotoCache {
    private PhotoCache() {}

    private static final Map<Integer, ResourceLocation> LOADED = new HashMap<>();
    private static final Set<Integer> ASKED = new HashSet<>();

    /** Photo number id's texture, or null while it's still coming. */
    @Nullable
    public static ResourceLocation get(int id) {
        ResourceLocation loc = LOADED.get(id);
        if (loc == null && ASKED.add(id) && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new PhotoPayloads.Want(id));
        }
        return loc;
    }

    static void received(int id, byte[] data) {
        PhotoCodec.Picture pic = data.length == 0 ? null : PhotoCodec.decode(data);
        if (pic == null || LOADED.containsKey(id)) {
            return;
        }
        NativeImage img = new NativeImage(pic.w(), pic.h(), false);
        for (int y = 0; y < pic.h(); y++) {
            for (int x = 0; x < pic.w(); x++) {
                int c = pic.rgb()[y * pic.w() + x];
                img.setPixelRGBA(x, y, 0xFF000000 | (c & 255) << 16 | (c >> 8 & 255) << 8 | (c >> 16 & 255)); // (ABGR)
            }
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "photo/" + id);
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        LOADED.put(id, loc);
    }

    /** Leaving a world: its photos are its own. */
    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        for (ResourceLocation loc : LOADED.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        LOADED.clear();
        ASKED.clear();
    }
}
