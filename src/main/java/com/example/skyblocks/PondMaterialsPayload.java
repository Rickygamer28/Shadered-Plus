package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla koi pond menu -> server: the blocks its rim and floor are made of ("" for its own), for the whole pond (every
 * vanilla koi pond joined to it).
 */
public record PondMaterialsPayload(BlockPos pos, String rim, String floor) {



    @Nullable
    private static BlockState block(String id) {
        ResourceLocation name = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (name == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(name).map(Block::defaultBlockState).orElse(null);
    }


    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(rim, 256);
        buf.writeUtf(floor, 256);
    }

    public static PondMaterialsPayload decode(FriendlyByteBuf buf) {
        return new PondMaterialsPayload(buf.readBlockPos(), buf.readUtf(256), buf.readUtf(256));
    }

    public static void handle(PondMaterialsPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) {
                return;
            }
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
