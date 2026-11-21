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
    public static final int SIT = 0, STYLE = 1, CARS = 2, LOOP = 3, GO = 4, PICK_UP = 5, RENAME = 6, CALL = 7, THROTTLE = 8;
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
            if (!(level.getEntity(p.train) instanceof TrainEntity t) || (player.getVehicle() != t && player.distanceToSqr(t) > 96.0 * 96.0)) {
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
                case THROTTLE -> {
                    // (value: unit * 64 + 32 + notch; + 0x1000: brake) - only from that cab's driver's seat
                    int v = p.value & 0xFFF, unit = v / 64, notch = v % 64 - 32;
                    Integer key = t.seatOf(player);
                    if (player.getVehicle() == t && key != null && key / 16 == unit && TrainEntity.driverSeat(t.kind(unit), key % 16)) {
                        t.throttle(unit, notch, (p.value & 0x1000) != 0);
                    }
                }
                default -> {}
            }
        });
    }
}
