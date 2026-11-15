package com.example.skyblocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The moon, caught in a jar (MoonJarBlock): whether it's out of the sky - kept with the world (by the overworld), and
 * told to every player, whose sky then draws no moon (mixin/MoonSkyMixin). There's one moon: caught, it can't be
 * caught again until it's let out - by opening the jar, or the jar being destroyed (burnt, despawned).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class MoonJar {
    private MoonJar() {}

    /** Client: the moon's in a jar (as the server last said). */
    public static boolean clientCaught;

    public static final class State extends SavedData {
        boolean caught;

        static State get(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "skyblocks_moon_jar");
        }

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State s = new State();
            s.caught = tag.getBoolean("caught");
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("caught", caught);
            return tag;
        }
    }

    public static boolean caught(MinecraftServer server) {
        return State.get(server).caught;
    }

    public static void set(MinecraftServer server, boolean caught) {
        State s = State.get(server);
        if (s.caught != caught) {
            s.caught = caught;
            s.setDirty();
            PacketDistributor.sendToAllPlayers(new Payload(caught));
        }
    }

    public record Payload(boolean caught) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "moon_jar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeBoolean(p.caught),
                buf -> new Payload(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Payload payload, IPayloadContext context) {
            context.enqueueWork(() -> clientCaught = payload.caught);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Payload(caught(sp.server)));
        }
    }

    /** A jar with the moon in it destroyed as an item (in lava, fire, despawned): the moon goes back up. */
    @SubscribeEvent
    public static void itemGone(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !MoonJarBlock.full(stack)) {
            return;
        }
        Entity.RemovalReason why = item.getRemovalReason();
        boolean despawned = why == Entity.RemovalReason.DISCARDED && item.getAge() >= item.lifespan - 1;
        if (why == Entity.RemovalReason.KILLED || despawned) {
            set(level.getServer(), false);
        }
    }
}
