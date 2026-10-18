package com.example.skyblocks.island;

import com.example.skyblocks.SkyBlocksMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the mobs on a placed Sky Island now (sent when one picks a new spot or walks to another island). */
public record IslandMobsPayload(BlockPos pos, CompoundTag mobs) implements CustomPacketPayload {
    public static final Type<IslandMobsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "island_mobs"));

    public static final StreamCodec<ByteBuf, IslandMobsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, IslandMobsPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, IslandMobsPayload::mobs,
            IslandMobsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IslandMobsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.isLoaded(payload.pos()) && level.getBlockEntity(payload.pos()) instanceof SkyIslandBlockEntity island) {
                island.applyMobs(payload.mobs());
            }
        });
    }
}
