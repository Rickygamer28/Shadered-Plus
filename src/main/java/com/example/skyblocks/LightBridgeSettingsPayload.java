package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: a light bridge's settings from its screen, for both its emitters. Colour: a dye (0-15), 16 rainbow. */
public record LightBridgeSettingsPayload(BlockPos pos, int mode, boolean reverse, int width, int pattern, int opacity, boolean rails, int colour)
        implements CustomPacketPayload {
    public static final Type<LightBridgeSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "light_bridge_settings"));
    public static final StreamCodec<ByteBuf, LightBridgeSettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf b = new FriendlyByteBuf(buf);
                b.writeBlockPos(p.pos());
                b.writeVarInt(p.mode());
                b.writeBoolean(p.reverse());
                b.writeVarInt(p.width());
                b.writeVarInt(p.pattern());
                b.writeVarInt(p.opacity());
                b.writeBoolean(p.rails());
                b.writeVarInt(p.colour());
            },
            buf -> {
                FriendlyByteBuf b = new FriendlyByteBuf(buf);
                return new LightBridgeSettingsPayload(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                        b.readBoolean(), b.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LightBridgeSettingsPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(p.pos()) || player.distanceToSqr(p.pos().getCenter()) > 12 * 12
                    || !(level.getBlockEntity(p.pos()) instanceof LightBridgeEmitterBlockEntity be)) {
                return;
            }
            int idx = Math.max(0, Math.min(16, p.colour()));
            int rgb = idx < 16 ? DyeColor.byId(idx).getTextureDiffuseColor() & 0xFFFFFF : -1;
            LightBridgeEmitterBlockEntity partner = be.partner();
            for (LightBridgeEmitterBlockEntity e : partner != null && partner != be ? java.util.List.of(be, partner) : java.util.List.of(be)) {
                e.setSettings(p.mode(), p.reverse(), p.width(), p.pattern(), p.opacity(), p.rails());
                if (e.colour() != rgb) {
                    e.setColour(rgb);
                }
                if (e.getBlockState().getValue(LightBridgeEmitterBlock.COLOR) != idx) {
                    level.setBlock(e.getBlockPos(), e.getBlockState().setValue(LightBridgeEmitterBlock.COLOR, idx), Block.UPDATE_ALL);
                }
            }
        });
    }
}
