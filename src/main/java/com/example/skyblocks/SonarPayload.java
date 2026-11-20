package com.example.skyblocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A sonar's list of what it finds, set (from client/SonarScreen) on the one in that hand. */
public record SonarPayload(boolean mainHand, CompoundTag entries) implements CustomPacketPayload {
    public static final Type<SonarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sonar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SonarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SonarPayload::mainHand, ByteBufCodecs.COMPOUND_TAG, SonarPayload::entries, SonarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SonarPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getItemInHand(payload.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (stack.getItem() instanceof SonarItem) {
                // (only what a list can hold, cleaned up)
                ItemStack probe = new ItemStack(stack.getItem());
                probe.set(DataComponents.CUSTOM_DATA, CustomData.of(payload.entries));
                CompoundTag clean = SonarItem.write(SonarItem.entries(probe));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put("entries", clean.get("entries")));
            }
        });
    }
}
