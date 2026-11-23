package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Blocks given heat shimmer with the Heat Wand (client/HeatShimmer draws it over them, as over fire and lava): kept
 * per world, by chunk, and sent to the players who can see them. Breaking the block takes its shimmer away.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
public final class HeatMarks extends SavedData {
    private final Map<Long, Set<Long>> chunks = new HashMap<>();

    public static HeatMarks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HeatMarks::new, HeatMarks::load, null), "skyblocks_heat_marks");
    }

    /** Shimmer on (or off) over a block; whether it's on now. */
    boolean toggle(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> chunk = chunks.computeIfAbsent(key, k -> new HashSet<>());
        boolean on = !chunk.remove(pos.asLong());
        if (on) {
            chunk.add(pos.asLong());
        } else if (chunk.isEmpty()) {
            chunks.remove(key);
        }
        setDirty();
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new Mark(pos.asLong(), on));
        return on;
    }

    private static HeatMarks load(CompoundTag tag, HolderLookup.Provider registries) {
        HeatMarks marks = new HeatMarks();
        for (long p : tag.getLongArray("pos")) {
            BlockPos at = BlockPos.of(p);
            marks.chunks.computeIfAbsent(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4), k -> new HashSet<>()).add(p);
        }
        return marks;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("pos", chunks.values().stream().flatMap(Set::stream).mapToLong(Long::longValue).toArray());
        return tag;
    }

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        Set<Long> chunk = get(event.getLevel()).chunks.get(event.getPos().toLong());
        if (chunk != null && !chunk.isEmpty()) {
            PacketDistributor.sendToPlayer(event.getPlayer(), new Chunk(event.getPos().toLong(), chunk.stream().mapToLong(Long::longValue).toArray()));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            HeatMarks marks = get(level);
            Set<Long> chunk = marks.chunks.get(ChunkPos.asLong(event.getPos().getX() >> 4, event.getPos().getZ() >> 4));
            if (chunk != null && chunk.contains(event.getPos().asLong())) {
                marks.toggle(level, event.getPos());
            }
        }
    }

    /** One block's shimmer, on or off. */
    public record Mark(long pos, boolean on) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "heat_mark"));
        public static final StreamCodec<ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Mark::pos, ByteBufCodecs.BOOL, Mark::on, Mark::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Mark p, IPayloadContext context) {
            context.enqueueWork(() -> com.example.skyblocks.client.HeatShimmer.mark(p.pos, p.on));
        }
    }

    /** A chunk's shimmering blocks, as it's sent to a player. */
    public record Chunk(long chunk, long[] pos) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "heat_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.chunk);
                    ByteBufCodecs.VAR_INT.encode(buf, p.pos.length);
                    for (long l : p.pos) {
                        buf.writeLong(l);
                    }
                },
                buf -> {
                    long chunk = buf.readLong();
                    long[] pos = new long[Math.min(ByteBufCodecs.VAR_INT.decode(buf), 65536)];
                    for (int i = 0; i < pos.length; i++) {
                        pos[i] = buf.readLong();
                    }
                    return new Chunk(chunk, pos);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Chunk p, IPayloadContext context) {
            context.enqueueWork(() -> {
                for (long l : p.pos) {
                    com.example.skyblocks.client.HeatShimmer.mark(l, true);
                }
            });
        }
    }

    /** The Heat Wand: right-click a block to give it heat shimmer (or take it away). */
    public static class Wand extends Item {
        public Wand(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (context.getLevel() instanceof ServerLevel level) {
                boolean on = get(level).toggle(level, context.getClickedPos());
                level.playSound(null, context.getClickedPos(), on ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.3F);
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable(on ? "message.skyblocks.heat_wand.on" : "message.skyblocks.heat_wand.off"), true);
                }
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.skyblocks.heat_wand").withStyle(ChatFormatting.GRAY));
        }
    }
}
