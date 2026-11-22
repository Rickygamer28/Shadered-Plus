package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Sky Studio's skies: {@link #SLOTS} skies (SkyNames' studio_1 .. studio_8, real skies like any other: blocks,
 * liquids, shapes, paint, creatures), each drawn by one shader (shaders/include/studio_sky.glsl) from its {@link #SIZE}
 * settings, and named by whoever made it. Kept per server (in the Overworld's data), sent to every player.
 *
 * <p>The settings, in order (all 0 .. 1; colours r g b): sky colour top, middle, horizon, horizon height, glow, on;
 * nebula on, colour 1, colour 2, amount, drift speed, softness; stars on, density, twinkle, size; aurora on, colour 1,
 * colour 2, brightness, wave speed, height; sun/moon on, colour, size, glow, height; shooting stars on, how often, trail
 * length; grid on, colour, spacing, scroll speed.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
public final class StudioSkies extends SavedData {
    public static final int SLOTS = 8, SIZE = 52;

    /** Where each layer's "on" setting is. */
    public static final int GRADIENT_ON = 11, NEBULA_ON = 12, STARS_ON = 22, AURORA_ON = 26, MOON_ON = 36, SHOOTING_ON = 43, GRID_ON = 46;

    public final String[] names = new String[SLOTS];
    public final float[][] params = new float[SLOTS][];

    private StudioSkies() {
        for (int i = 0; i < SLOTS; i++) {
            names[i] = "";
            params[i] = preset(i);
        }
    }

    public static StudioSkies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(StudioSkies::new, StudioSkies::load, null),
                "skyblocks_studio_skies");
    }

    /** A slot's sky to begin with: one of four looks, each a little different. */
    public static float[] preset(int slot) {
        String[][] looks = {
                {"#120d2e", "#3b1f5c", "#f08a5d", "#7c4dff", "#ff6fae", "#5ef2c2", "#ff6fae", "#ff5fd2"},
                {"#02030a", "#0b1640", "#1b3a6b", "#3aa0ff", "#9b5cff", "#6ad8ff", "#9b5cff", "#49c6ff"},
                {"#0a0018", "#2a0b4d", "#ff3d81", "#ff2bd6", "#2bf0ff", "#c6ff3d", "#2bf0ff", "#2bf0ff"},
                {"#1d2b4f", "#5b6fa8", "#ffc59e", "#ffd6e8", "#a5d8ff", "#b8ffd9", "#ffd6e8", "#ffb3c7"}};
        int[][] layers = {{1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 0, 1, 1, 0}, {1, 0, 1, 1, 0, 1, 1}, {1, 1, 0, 0, 1, 0, 0}};
        String[] c = looks[slot % 4];
        int[] on = layers[(slot / 2 + slot) % 4];
        List<Float> p = new ArrayList<>();
        rgb(p, c[0]); rgb(p, c[1]); rgb(p, c[2]); add(p, 0.55F, 0.7F, on[0]);
        add(p, on[1]); rgb(p, c[3]); rgb(p, c[4]); add(p, 0.6F, 0.25F, 0.8F);
        add(p, on[2], 0.45F, 0.65F, 0.3F);
        add(p, on[3]); rgb(p, c[5]); rgb(p, c[6]); add(p, 0.75F, 0.4F, 0.35F);
        add(p, on[4]); rgb(p, "#f4efe1"); add(p, 0.4F, 0.3F, 0.35F + 0.1F * (slot % 3));
        add(p, on[5], 0.6F, 0.55F);
        add(p, on[6]); rgb(p, c[7]); add(p, 0.5F, 0.6F);
        float[] out = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            out[i] = p.get(i);
        }
        return out;
    }

    private static void rgb(List<Float> p, String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        p.add(((v >> 16) & 255) / 255.0F);
        p.add(((v >> 8) & 255) / 255.0F);
        p.add((v & 255) / 255.0F);
    }

    private static void add(List<Float> p, float... values) {
        for (float v : values) {
            p.add(v);
        }
    }

    private static StudioSkies load(CompoundTag tag, HolderLookup.Provider registries) {
        StudioSkies s = new StudioSkies();
        ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(SLOTS, list.size()); i++) {
            CompoundTag c = list.getCompound(i);
            s.names[i] = c.getString("name");
            int[] bits = c.getIntArray("params");
            if (bits.length == SIZE) {
                for (int k = 0; k < SIZE; k++) {
                    s.params[i][k] = clamp(Float.intBitsToFloat(bits[k]));
                }
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("name", names[i]);
            int[] bits = new int[SIZE];
            for (int k = 0; k < SIZE; k++) {
                bits[k] = Float.floatToIntBits(params[i][k]);
            }
            c.putIntArray("params", bits);
            list.add(c);
        }
        tag.put("slots", list);
        return tag;
    }

    private static float clamp(float v) {
        return Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
    }

    /** The sky type of a slot (SkyNames' studio_N). */
    public static com.noodlegamer76.shadered.client.util.SkyblockType type(int slot) {
        return SkyTypes.type(index(slot));
    }

    /** Our sky index (SkyNames) of a slot. */
    public static int index(int slot) {
        return java.util.Arrays.asList(SkyNames.NAMES).indexOf("studio_" + (slot + 1));
    }

    private Sync sync() {
        List<String> n = new ArrayList<>();
        List<float[]> p = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            n.add(names[i]);
            p.add(params[i].clone());
        }
        return new Sync(n, p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            PacketDistributor.sendToPlayer(player, get(player.getServer()).sync());
        }
    }

    /** Close enough to a Sky Studio to use it. */
    private static boolean atStudio(Player player, BlockPos pos) {
        return player.level().isLoaded(pos) && player.level().getBlockState(pos).getBlock() instanceof SkyStudioBlock
                && player.distanceToSqr(pos.getCenter()) <= 8 * 8;
    }

    // ---- what goes over the network ----

    /** Every slot's name and settings (to a player). */
    public record Sync(List<String> names, List<float[]> params) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "studio_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, s.names.size());
                    for (int i = 0; i < s.names.size(); i++) {
                        ByteBufCodecs.stringUtf8(64).encode(buf, s.names.get(i));
                        for (float v : s.params.get(i)) {
                            buf.writeFloat(v);
                        }
                    }
                },
                buf -> {
                    int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), SLOTS);
                    List<String> names = new ArrayList<>();
                    List<float[]> params = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        names.add(ByteBufCodecs.stringUtf8(64).decode(buf));
                        float[] p = new float[SIZE];
                        for (int k = 0; k < SIZE; k++) {
                            p[k] = clamp(buf.readFloat());
                        }
                        params.add(p);
                    }
                    return new Sync(names, params);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Sync s, IPayloadContext context) {
            context.enqueueWork(() -> StudioSkiesStore.set(s.names, s.params));
        }
    }

    /** A player saves a slot (name and settings) at a Sky Studio. */
    public record Save(BlockPos pos, int slot, String name, float[] params) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "studio_save"));
        public static final StreamCodec<ByteBuf, Save> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    BlockPos.STREAM_CODEC.encode(buf, s.pos);
                    ByteBufCodecs.VAR_INT.encode(buf, s.slot);
                    ByteBufCodecs.stringUtf8(64).encode(buf, s.name);
                    for (int k = 0; k < SIZE; k++) {
                        buf.writeFloat(k < s.params.length ? s.params[k] : 0.0F);
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int slot = ByteBufCodecs.VAR_INT.decode(buf);
                    String name = ByteBufCodecs.stringUtf8(64).decode(buf);
                    float[] p = new float[SIZE];
                    for (int k = 0; k < SIZE; k++) {
                        p[k] = clamp(buf.readFloat());
                    }
                    return new Save(pos, slot, name, p);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Save s, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (s.slot < 0 || s.slot >= SLOTS || !atStudio(player, s.pos) || player.getServer() == null) {
                    return;
                }
                StudioSkies skies = get(player.getServer());
                skies.names[s.slot] = s.name.strip().length() > 32 ? s.name.strip().substring(0, 32) : s.name.strip();
                skies.params[s.slot] = s.params.clone();
                skies.setDirty();
                PacketDistributor.sendToAllPlayers(skies.sync());
                player.level().playSound(null, s.pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            });
        }
    }

    /** A player takes sky blocks of a slot's sky at a Sky Studio: one for each glass block, up to 16 at a time. */
    public record Take(BlockPos pos, int slot) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "studio_take"));
        public static final StreamCodec<ByteBuf, Take> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Take::pos, ByteBufCodecs.VAR_INT, Take::slot, Take::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Take t, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                int index = t.slot >= 0 && t.slot < SLOTS ? index(t.slot) : -1;
                if (index < 0 || !atStudio(player, t.pos)) {
                    return;
                }
                int count = 0;
                boolean free = player.getAbilities().instabuild;
                if (free) {
                    count = 16;
                } else {
                    var inv = player.getInventory();
                    for (int i = 0; i < inv.getContainerSize() && count < 16; i++) {
                        ItemStack s = inv.getItem(i);
                        if (s.is(Items.GLASS)) {
                            int n = Math.min(16 - count, s.getCount());
                            s.shrink(n);
                            count += n;
                        }
                    }
                }
                if (count == 0) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.studio.glass"), true);
                    return;
                }
                ItemStack sky = com.noodlegamer76.shadered.item.SkyblockHolderBlockItem.create(SkyTypes.type(index),
                        com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass.NORMAL, ModItems.SKY_ITEMS.get(index).get());
                sky.setCount(count);
                if (!player.getInventory().add(sky)) {
                    player.drop(sky, false);
                }
                player.level().playSound(null, t.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            });
        }
    }

    // (a level is needed only to find the server's data: the Overworld's)
    static StudioSkies of(ServerLevel level) {
        return get(level.getServer());
    }
}
