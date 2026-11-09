package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The claw machine's messages: a go played to its end (and what it won), and where the claw is as it's played. */
public final class ClawPayloads {
    private ClawPayloads() {}

    /** Client -> server: a go is over; {@code prize}: the plushie won (ModBlocks.PLUSHIE_NAMES), -1 none. */
    public record Play(BlockPos pos, int prize) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "claw_play"));
        public static final StreamCodec<ByteBuf, Play> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Play::pos, ByteBufCodecs.VAR_INT, Play::prize, Play::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Play p, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(p.pos())
                        || player.distanceToSqr(p.pos().getCenter()) > 10 * 10
                        || !(level.getBlockEntity(p.pos()) instanceof ClawMachineBlockEntity be) || !be.useCredit(player)) {
                    return;
                }
                if (p.prize() >= 0 && p.prize() < ModItems.PLUSHIES.size()) {
                    ItemStack prize = new ItemStack(ModItems.PLUSHIES.get(p.prize()).get());
                    if (!player.getInventory().add(prize)) {
                        player.drop(prize, false);
                    }
                    level.playSound(null, p.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.pos().getX() + 0.5, p.pos().getY() + 1.0, p.pos().getZ() + 0.5,
                            12, 0.3, 0.3, 0.3, 0.0);
                }
            });
        }
    }

    /** Both ways: the claw now (each 0-1000), open or not, holding which plushie (-1 none). */
    public record State(BlockPos pos, int x, int z, int depth, int holding) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "claw_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, State::pos, ByteBufCodecs.VAR_INT, State::x, ByteBufCodecs.VAR_INT, State::z,
                ByteBufCodecs.VAR_INT, State::depth, ByteBufCodecs.VAR_INT, State::holding, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(State p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.flow().isServerbound()) {
                    Player player = context.player();
                    if (player.level() instanceof ServerLevel level && level.isLoaded(p.pos())
                            && player.distanceToSqr(p.pos().getCenter()) <= 10 * 10
                            && level.getBlockEntity(p.pos()) instanceof ClawMachineBlockEntity) {
                        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(p.pos()), p);
                    }
                } else {
                    com.example.skyblocks.client.ClientAccess.clawState(p);
                }
            });
        }
    }
}
