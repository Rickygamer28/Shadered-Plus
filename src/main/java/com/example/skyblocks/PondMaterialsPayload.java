package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla koi pond menu -> server: the blocks its rim and floor are made of ("" for its own), for the whole pond (every
 * vanilla koi pond joined to it).
 */
public record PondMaterialsPayload(BlockPos pos, String rim, String floor) implements CustomPacketPayload {
    public static final Type<PondMaterialsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "pond_materials"));

    public static final StreamCodec<ByteBuf, PondMaterialsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PondMaterialsPayload::pos,
            ByteBufCodecs.stringUtf8(256), PondMaterialsPayload::rim,
            ByteBufCodecs.stringUtf8(256), PondMaterialsPayload::floor,
            PondMaterialsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    private static BlockState block(String id) {
        ResourceLocation name = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (name == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(name).map(Block::defaultBlockState).orElse(null);
    }

    public static void handle(PondMaterialsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            BlockPos start = payload.pos();
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(start) || player.distanceToSqr(start.getCenter()) > 8 * 8) {
                return;
            }
            Block pond = level.getBlockState(start).getBlock();
            if (pond != ModBlocks.VANILLA_KOI_POND.get()) {
                return;
            }
            BlockState rim = block(payload.rim()), floor = block(payload.floor());
            // the whole pond: every vanilla koi pond joined to this one
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            todo.add(start);
            seen.add(start);
            while (!todo.isEmpty() && seen.size() <= 4096) {
                BlockPos p = todo.poll();
                if (level.getBlockEntity(p) instanceof KoiPondBlockEntity be) {
                    be.setMaterials(rim, floor);
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (level.isLoaded(n) && level.getBlockState(n).is(pond) && seen.add(n)) {
                        todo.add(n);
                    }
                }
            }
        });
    }
}
