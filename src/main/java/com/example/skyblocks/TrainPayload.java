package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A train's screen (client/TrainScreen) and its seats (client/TrainClient): a seat taken in a car, its look, cars or
 * going round set, it sent to a station, it picked up; or a station renamed or a train called to it (client/StationScreen).
 */
public record TrainPayload(int train, int action, int value, BlockPos pos, String text) implements CustomPacketPayload {
    public static final int SIT = 0, STYLE = 1, CARS = 2, LOOP = 3, GO = 4, PICK_UP = 5, RENAME = 6, CALL = 7;
    public static final Type<TrainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "train_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrainPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.train);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
        buf.writeBlockPos(p.pos);
        ByteBufCodecs.STRING_UTF8.encode(buf, p.text);
    }, buf -> new TrainPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static TrainPayload of(TrainEntity t, int action, int value) {
        return new TrainPayload(t.getId(), action, value, BlockPos.ZERO, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrainPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (p.action == RENAME || p.action == CALL) {
                if (!level.isLoaded(p.pos) || player.distanceToSqr(p.pos.getCenter()) > 64.0
                        || !(level.getBlockEntity(p.pos) instanceof StationBlockEntity be)) {
                    return;
                }
                if (p.action == RENAME) {
                    be.setName(p.text.strip());
                } else {
                    StationBlock.call(level, p.pos, player);
                }
                return;
            }
            if (!(level.getEntity(p.train) instanceof TrainEntity t) || (player.getVehicle() != t && player.distanceToSqr(t) > 48.0 * 48.0)) {
                return;
            }
            switch (p.action) {
                case SIT -> t.sit(player, p.value);
                case STYLE -> t.setStyle(p.value);
                case CARS -> t.setCars(p.value);
                case LOOP -> t.setLoop(p.value != 0);
                case GO -> {
                    if (level.isLoaded(p.pos) && level.getBlockState(p.pos).getBlock() instanceof StationBlock) {
                        BlockPos rail = TrainLine.railBy(level, p.pos);
                        if (rail != null) {
                            t.goTo(rail);
                        }
                    }
                }
                case PICK_UP -> t.pickUp(player);
                default -> {}
            }
        });
    }
}
