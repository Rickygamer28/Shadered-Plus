package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A phase wall's look (a block's name; empty: its own panel) and how it opens (PhaseBlockEntity.MODES): set on all of it. */
public record PhasePayload(BlockPos pos, String look, int mode) implements CustomPacketPayload {
    public static final Type<PhasePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "phase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhasePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PhasePayload::pos, ByteBufCodecs.STRING_UTF8, PhasePayload::look, ByteBufCodecs.VAR_INT, PhasePayload::mode, PhasePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The block a name gives to look like (air: none, or no such block - or one it can't look like: one with a block entity of its own). */
    public static BlockState look(String name) {
        ResourceLocation rl = name.isBlank() ? null : ResourceLocation.tryParse(name.contains(":") ? name.trim() : "minecraft:" + name.trim());
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block instanceof EntityBlock || block instanceof PhaseBlock ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
    }

    public static void handle(PhasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (context.player().distanceToSqr(payload.pos.getCenter()) > 16 * 16 || payload.look.length() > 96) {
                return;
            }
            BlockState look = look(payload.look);
            // the whole wall: the phase blocks touching it, and theirs
            java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<BlockPos> next = new java.util.ArrayDeque<>();
            next.add(payload.pos);
            seen.add(payload.pos);
            while (!next.isEmpty() && seen.size() <= 512) {
                BlockPos p = next.poll();
                if (level.getBlockEntity(p) instanceof PhaseBlockEntity be) {
                    be.set(look, payload.mode);
                    for (Direction d : Direction.values()) {
                        BlockPos q = p.relative(d);
                        if (seen.add(q) && level.getBlockState(q).getBlock() instanceof PhaseBlock) {
                            next.add(q);
                        }
                    }
                }
            }
        });
    }
}
