package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The infusion altar's editor -> server: the skies painted on the item on the altar, and its own texture if it was
 * redrawn (none: back to the item's own). An amethyst shard a change, but in creative.
 */
public record ShaderPaintPayload(BlockPos pos, ModComponents.ShaderPaint paint, Optional<ModComponents.ItemTexture> texture) implements CustomPacketPayload {
    public static final Type<ShaderPaintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "shader_paint"));

    public static final StreamCodec<ByteBuf, ShaderPaintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShaderPaintPayload::pos,
            ModComponents.ShaderPaint.STREAM_CODEC, ShaderPaintPayload::paint,
            ByteBufCodecs.optional(ModComponents.ItemTexture.STREAM_CODEC), ShaderPaintPayload::texture,
            ShaderPaintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean fits(int w, int h, int length, int per) {
        return w >= 1 && h >= 1 && w <= 64 && h <= 64 && length == w * h * per;
    }

    public static void handle(ShaderPaintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ModComponents.ShaderPaint paint = payload.paint();
            ModComponents.ItemTexture texture = payload.texture().orElse(null);
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos()) || player.distanceToSqr(payload.pos().getCenter()) > 8 * 8
                    || !(level.getBlockEntity(payload.pos()) instanceof InfusionAltarBlockEntity altar) || altar.item().isEmpty()
                    || !fits(paint.w(), paint.h(), paint.pixels().length, 2) || texture != null && !fits(texture.w(), texture.h(), texture.argb().length, 4)) {
                return;
            }
            ItemStack item = altar.item().copy();
            ModComponents.ShaderPaint hadPaint = item.get(ModComponents.SHADER_PAINT.get());
            ModComponents.ItemTexture hadTexture = item.get(ModComponents.ITEM_TEXTURE.get());
            ModComponents.ShaderPaint newPaint = paint.empty() ? null : paint;
            if (java.util.Objects.equals(newPaint, hadPaint) && java.util.Objects.equals(texture, hadTexture)) {
                return;
            }
            boolean undoing = newPaint == null && texture == null;
            if (!undoing && !player.getAbilities().instabuild) {
                int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.AMETHYST_SHARD));
                if (slot < 0) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.infusion_altar.shard"), true);
                    return;
                }
                player.getInventory().removeItem(slot, 1);
            }
            if (newPaint == null) {
                item.remove(ModComponents.SHADER_PAINT.get());
            } else {
                item.set(ModComponents.SHADER_PAINT.get(), newPaint);
            }
            if (texture == null) {
                item.remove(ModComponents.ITEM_TEXTURE.get());
            } else {
                item.set(ModComponents.ITEM_TEXTURE.get(), texture);
            }
            altar.setItem(item);
            level.playSound(null, payload.pos(), undoing ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, payload.pos().getX() + 0.5, payload.pos().getY() + 1.3,
                    payload.pos().getZ() + 0.5, 20, 0.25, 0.25, 0.25, 0.02);
        });
    }
}
