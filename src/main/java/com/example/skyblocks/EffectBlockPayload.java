package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Effect block menu -> server: the settings, for the clicked block's whole group. */
public record EffectBlockPayload(BlockPos pos, int look, java.util.List<Integer> colours, int amounts, String block) implements CustomPacketPayload {
    // (look: the effect, the colour count << 8, pixelated << 12, blur << 13; amounts: speed, size << 10, brightness << 20)
    public static EffectBlockPayload of(BlockPos pos, EffectBlockEntity.Settings s) {
        java.util.List<Integer> c = new java.util.ArrayList<>();
        for (int v : s.colours()) {
            c.add(v);
        }
        return new EffectBlockPayload(pos, s.effect() | (s.count() << 8) | (s.pixel() ? 1 << 12 : 0) | (s.blur() << 13), c,
                s.speed() | (s.size() << 10) | (s.brightness() << 20), s.block());
    }

    public EffectBlockEntity.Settings settings() {
        int[] c = new int[EffectBlockEntity.MAX_COLOURS + 1];
        for (int i = 0; i < c.length && i < colours.size(); i++) {
            c[i] = colours.get(i);
        }
        return new EffectBlockEntity.Settings(look & 0xFF, (look >> 8) & 0xF, c, amounts & 0x3FF, (amounts >> 10) & 0x3FF,
                (amounts >> 20) & 0x3FF, (look & (1 << 12)) != 0, (look >> 13) & 0x7F,
                block.length() > 200 ? "" : block);
    }

    public static final Type<EffectBlockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "effect_block"));

    public static final StreamCodec<ByteBuf, EffectBlockPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EffectBlockPayload::pos,
            ByteBufCodecs.VAR_INT, EffectBlockPayload::look,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(EffectBlockEntity.MAX_COLOURS + 1)), EffectBlockPayload::colours,
            ByteBufCodecs.VAR_INT, EffectBlockPayload::amounts,
            ByteBufCodecs.stringUtf8(256), EffectBlockPayload::block,
            EffectBlockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EffectBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 10 * 10
                    && level.getBlockEntity(payload.pos()) instanceof EffectBlockEntity) {
                EffectBlockEntity.Settings s = payload.settings();
                for (BlockPos p : EffectBlock.group(level, payload.pos())) {
                    if (level.getBlockEntity(p) instanceof EffectBlockEntity be) {
                        be.setSettings(s);
                    }
                }
            }
        });
    }
}
