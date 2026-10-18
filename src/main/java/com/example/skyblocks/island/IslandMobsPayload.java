package com.example.skyblocks.island;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the mobs on a placed Sky Island now (sent when one picks a new spot or walks to another island). */
public record IslandMobsPayload(BlockPos pos, CompoundTag mobs) {

    public static void encode(IslandMobsPayload payload, FriendlyByteBuf buf) {
        buf.writeBlockPos(payload.pos);
        buf.writeNbt(payload.mobs);
    }

    public static IslandMobsPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag mobs = buf.readNbt();
        return new IslandMobsPayload(pos, mobs == null ? new CompoundTag() : mobs);
    }

    /** Runs on the client thread (registered with consumerMainThread). */
    public static void handle(IslandMobsPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(payload));
    }

    private static final class Client {
        static void apply(IslandMobsPayload payload) {
            Level level = Minecraft.getInstance().level;
            if (level != null && level.isLoaded(payload.pos())
                    && level.getBlockEntity(payload.pos()) instanceof SkyIslandBlockEntity island) {
                island.applyMobs(payload.mobs());
            }
        }
    }
}
