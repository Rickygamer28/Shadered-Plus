package com.example.skyblocks.mixin;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.SkyGlow;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Trimmed with a Sky Crystal: the armour takes the crystal's sky (SkyGlow), for its trim to show. */
@Mixin(SmithingTrimRecipe.class)
public abstract class SmithingTrimSkyMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), require = 0)
    private void skyblocks$skyTrim(SmithingRecipeInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack out = cir.getReturnValue();
        String sky = SkyGlow.of(input.addition());
        ArmorTrim trim = out.isEmpty() ? null : out.get(DataComponents.TRIM);
        if (sky == null || trim == null) {
            return;
        }
        if (trim.material().unwrapKey().map(k -> k.location().equals(SkyGlow.TRIM_MATERIAL)).orElse(false)) {
            out.set(ModComponents.SKY_GLOW.get(), sky);
        }
    }
}
