package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Sky Brush: hold use and sweep it over blocks to paint their faces with its sky (SkyPaint); sneak to wipe paint
 * off. Dipped in a sky by crafting it with a sky block.
 */
public class SkyBrushItem extends Item {
    public SkyBrushItem(Properties properties) {
        super(properties);
    }

    /** The sky it's loaded with, or null. */
    @Nullable
    public static SkyblockType sky(ItemStack stack) {
        String name = stack.get(ModComponents.SKY_BRUSH.get());
        if (name == null) {
            return null;
        }
        for (SkyblockType sky : SkyblockType.values()) {
            if (sky.name().equals(name)) {
                return sky;
            }
        }
        return null;
    }

    public static ItemStack with(ItemStack brush, @Nullable SkyblockType sky) {
        ItemStack out = brush.copyWithCount(1);
        if (sky == null) {
            out.remove(ModComponents.SKY_BRUSH.get());
        } else {
            out.set(ModComponents.SKY_BRUSH.get(), sky.name());
        }
        return out;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS; // (use(), below: hold it down)
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (sky(stack) == null && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.skyblocks.sky_brush.dry"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || remaining % 2 != 0) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || server.getBlockState(block.getBlockPos()).isAir() || !player.mayBuild()) {
            return;
        }
        SkyblockType sky = player.isShiftKeyDown() ? null : sky(stack);
        if (sky == null && !player.isShiftKeyDown()) {
            return;
        }
        if (SkyPaint.get(server).paint(server, block.getBlockPos(), block.getDirection(), sky) && remaining % 6 == 0) {
            server.playSound(null, block.getBlockPos(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.5F, 1.2F);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        SkyblockType sky = sky(stack);
        return sky == null ? super.getName(stack)
                : Component.translatable("item.skyblocks.sky_brush.with", Component.translatable(sky.getTranslationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (sky(stack) == null) {
            tooltip.add(Component.translatable("tooltip.skyblocks.sky_brush.dip").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.skyblocks.sky_brush.use").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skyblocks.sky_brush.wipe").withStyle(ChatFormatting.GRAY));
    }

    /** A Sky Brush and a sky block: the brush, dipped in that sky. */
    public static final class DipRecipe extends CustomRecipe {
        public DipRecipe(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return !assemble(input, null).isEmpty();
        }

        @Override
        public ItemStack assemble(CraftingInput input, @Nullable HolderLookup.Provider registries) {
            ItemStack brush = ItemStack.EMPTY;
            SkyblockType sky = null;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.getItem() instanceof SkyBrushItem && brush.isEmpty()) {
                    brush = stack;
                } else if (sky == null && (sky = SkyLiquidBucketRecipe.skyOf(stack)) != null) {
                    // the sky block
                } else {
                    return ItemStack.EMPTY;
                }
            }
            return brush.isEmpty() || sky == null ? ItemStack.EMPTY : with(brush, sky);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModSkyShapes.DIP_RECIPE.get();
        }
    }
}
