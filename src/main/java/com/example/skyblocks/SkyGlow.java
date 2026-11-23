package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Sky glow (ModComponents.SKY_GLOW: a sky's name), given by crafting with a sky block:
 * arrows (up to 8 alike) and tridents leave a trail of the sky behind them in flight; elytra, behind you as you glide
 * (client/GlowTrails). An amethyst shard becomes a Sky Crystal, the sky armour trim's material - trimmed with it,
 * the armour's trim shows its sky (client/ArmorPaintRender, mixin/SmithingTrimSkyMixin).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
public final class SkyGlow {
    private static final String TAG = "skyblocks_glow";
    public static final ResourceLocation TRIM_MATERIAL = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sky");

    private SkyGlow() {}

    @Nullable
    public static String of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModComponents.SKY_GLOW.get());
    }

    @Nullable
    public static SkyblockType sky(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return SkyblockType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** What can take a sky's glow. */
    static boolean takes(ItemStack stack) {
        return stack.getItem() instanceof ArrowItem || stack.is(Items.TRIDENT) || stack.is(Items.ELYTRA) || stack.is(Items.AMETHYST_SHARD);
    }

    // ---- arrows and tridents in flight: their trail's sky, to the players who see them ----

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof AbstractArrow arrow) {
            String sky = of(arrow.getPickupItemStackOrigin());
            if (sky != null) {
                arrow.getPersistentData().putString(TAG, sky);
            }
        }
    }

    @SubscribeEvent
    public static void seen(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof AbstractArrow arrow && arrow.getPersistentData().contains(TAG) && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Trail(arrow.getId(), arrow.getPersistentData().getString(TAG)));
        }
    }

    /** An arrow or trident (by its id) trailing a sky. */
    public record Trail(int id, String sky) implements CustomPacketPayload {
        public static final Type<Trail> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "glow_trail"));
        public static final StreamCodec<ByteBuf, Trail> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Trail::id, ByteBufCodecs.STRING_UTF8, Trail::sky, Trail::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Trail p, IPayloadContext context) {
            context.enqueueWork(() -> com.example.skyblocks.client.GlowTrails.track(p.id, p.sky));
        }
    }

    // ---- the crafting: a sky block with what takes its glow ----

    public static final class InfuseRecipe extends CustomRecipe {
        public InfuseRecipe(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return !assemble(input, null).isEmpty();
        }

        @Override
        public ItemStack assemble(CraftingInput input, @Nullable HolderLookup.Provider registries) {
            ItemStack first = ItemStack.EMPTY;
            int count = 0;
            SkyblockType sky = null;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (takes(stack) && (first.isEmpty() || ItemStack.isSameItemSameComponents(first, stack))) {
                    first = stack;
                    count++;
                } else if (sky == null && (sky = SkyLiquidBucketRecipe.skyOf(stack)) != null) {
                    // the sky block
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (first.isEmpty() || sky == null || count > 1 && !(first.getItem() instanceof ArrowItem)) {
                return ItemStack.EMPTY;
            }
            ItemStack out = first.is(Items.AMETHYST_SHARD) ? new ItemStack(ModItems.SKY_CRYSTAL.get()) : first.copyWithCount(count);
            out.set(ModComponents.SKY_GLOW.get(), sky.name());
            return out;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModSkyShapes.GLOW_RECIPE.get();
        }
    }
}
