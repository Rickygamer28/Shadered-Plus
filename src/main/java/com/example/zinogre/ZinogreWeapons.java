package com.example.zinogre;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The Zinogre weapons (the mod's own designs), made in a smithing table like the armor:
 * Thunderfang Longsword (netherite sword + Stormhide template + Thunder Horn) and Thunderfang Bow (bow + Stormhide
 * template + Zinogre Tail), Thunderfang Crossbow (crossbow + Stormhide template + Zinogre Claw); Duskfang ones from
 * the Thunderfang ones + Duskhide template + Stygian Horn / Tail / Claw.
 *
 * The longswords hit a little harder than netherite but swing slower, and now and then lightning arcs from the one
 * you hit to others close by. The bows' arrows hit harder and shock what they hit. The Duskfang ones arc and shock
 * more, and wither.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ZinogreWeapons {
    /** netherite: 2031 uses, +4 damage, enchantability 15 */
    static final Tier THUNDER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9.0F, 4.0F, 18,
            () -> Ingredient.of(ZinogreItems.SHELL.get()));
    static final Tier DUSK = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2800, 9.5F, 5.0F, 20,
            () -> Ingredient.of(ZinogreItems.STYGIAN_SHELL.get()));

    public static final DeferredItem<Item> THUNDERFANG_LONGSWORD = ZinogreItems.ITEMS.register("thunderfang_longsword",
            () -> new Longsword(THUNDER, false));
    public static final DeferredItem<Item> DUSKFANG_LONGSWORD = ZinogreItems.ITEMS.register("duskfang_longsword",
            () -> new Longsword(DUSK, true));
    public static final DeferredItem<Item> THUNDERFANG_BOW = ZinogreItems.ITEMS.register("thunderfang_bow",
            () -> new ZinogreBow(false, new Item.Properties().durability(800).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> DUSKFANG_BOW = ZinogreItems.ITEMS.register("duskfang_bow",
            () -> new ZinogreBow(true, new Item.Properties().durability(1000).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<Item> THUNDERFANG_CROSSBOW = ZinogreItems.ITEMS.register("thunderfang_crossbow",
            () -> new ZinogreCrossbow(false, new Item.Properties().durability(900).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> DUSKFANG_CROSSBOW = ZinogreItems.ITEMS.register("duskfang_crossbow",
            () -> new ZinogreCrossbow(true, new Item.Properties().durability(1100).rarity(Rarity.EPIC).fireResistant()));

    static List<DeferredItem<Item>> all() {
        return List.of(THUNDERFANG_LONGSWORD, THUNDERFANG_BOW, THUNDERFANG_CROSSBOW, DUSKFANG_LONGSWORD, DUSKFANG_BOW, DUSKFANG_CROSSBOW);
    }

    static void init() {}

    // ---- the longsword: Thunderfang 9 damage, Duskfang 10, both 1.4 swings a second (netherite sword: 8 at 1.6)
    public static final class Longsword extends SwordItem {
        final boolean dusk;

        Longsword(Tier tier, boolean dusk) {
            super(tier, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordItem.createAttributes(tier, 4, -2.6F)));
            this.dusk = dusk;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(dusk ? "item.zinogre.duskfang_longsword.ability" : "item.zinogre.thunderfang_longsword.ability")
                    .withStyle(dusk ? ChatFormatting.RED : ChatFormatting.AQUA));
        }
    }

    // ---- the bow: arrows hit harder (x1.3, Duskfang x1.45) and shock
    public static final class ZinogreBow extends BowItem {
        final boolean dusk;

        ZinogreBow(boolean dusk, Properties properties) {
            super(properties);
            this.dusk = dusk;
        }

        @Override
        protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
            Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, isCrit);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(arrow.getBaseDamage() * (dusk ? 1.45 : 1.3));
                arrow.getPersistentData().putInt(SHOCK_TAG, dusk ? 2 : 1);
            }
            return projectile;
        }

        @Override
        public int getEnchantmentValue() {
            return dusk ? 20 : 18;
        }

        @Override
        public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
            return repair.is(dusk ? ZinogreItems.STYGIAN_SHELL.get() : ZinogreItems.SHELL.get());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(dusk ? "item.zinogre.duskfang_bow.ability" : "item.zinogre.thunderfang_bow.ability")
                    .withStyle(dusk ? ChatFormatting.RED : ChatFormatting.AQUA));
        }
    }

    // ---- the crossbow: bolts hit harder (x1.3, Duskfang x1.45) and shock, like the bow's arrows
    public static final class ZinogreCrossbow extends net.minecraft.world.item.CrossbowItem {
        final boolean dusk;

        ZinogreCrossbow(boolean dusk, Properties properties) {
            super(properties);
            this.dusk = dusk;
        }

        @Override
        protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
            Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, isCrit);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(arrow.getBaseDamage() * (dusk ? 1.45 : 1.3));
                arrow.getPersistentData().putInt(SHOCK_TAG, dusk ? 2 : 1);
            }
            return projectile;
        }

        @Override
        public int getEnchantmentValue() {
            return dusk ? 20 : 18;
        }

        @Override
        public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
            return repair.is(dusk ? ZinogreItems.STYGIAN_SHELL.get() : ZinogreItems.SHELL.get());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable(dusk ? "item.zinogre.duskfang_bow.ability" : "item.zinogre.thunderfang_bow.ability")
                    .withStyle(dusk ? ChatFormatting.RED : ChatFormatting.AQUA));
        }
    }

    private static final String SHOCK_TAG = "zinogre_shock";
    /** Set while lightning arcs, so the arcs don't set off more arcs. */
    private static boolean arcing;

    /** Shocking arrows: extra damage as they hit (Duskfang: more, and withers). */
    @SubscribeEvent
    public static void onArrowHit(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        int shock = arrow.getPersistentData().getInt(SHOCK_TAG);
        if (shock == 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        event.setAmount(event.getAmount() + (shock == 2 ? 4.0F : 3.0F));
        sparks(level, target.position().add(0, target.getBbHeight() * 0.5, 0), shock == 2, 16);
        level.playSound(null, target.blockPosition(), ZinogreSounds.ZAP.get(), target.getSoundSource(), 0.7F, shock == 2 ? 0.9F : 1.3F);
        if (shock == 2) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), arrow.getOwner() instanceof LivingEntity l ? l : null);
        }
    }

    /** Longsword hits: now and then lightning arcs to others close by (not the wielder's pets, not players). */
    @SubscribeEvent
    public static void onSwordHit(LivingDamageEvent.Post event) {
        if (arcing || !(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player
                || !(player.getMainHandItem().getItem() instanceof Longsword sword) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.getRandom().nextFloat() >= (sword.dusk ? 0.35F : 0.25F)) {
            return;
        }
        LivingEntity hit = event.getEntity();
        int jumps = sword.dusk ? 3 : 2;
        float damage = sword.dusk ? 5.0F : 4.0F;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(5.0),
                e -> e != hit && e != player && e.isAlive() && !(e instanceof Player)
                        && !(e instanceof OwnableEntity pet && pet.getOwner() == player));
        near.sort((x, y) -> Double.compare(x.distanceToSqr(hit), y.distanceToSqr(hit)));
        arcing = true;
        try {
            Vec3 from = hit.position().add(0, hit.getBbHeight() * 0.5, 0);
            for (int i = 0; i < Math.min(jumps, near.size()); i++) {
                LivingEntity e = near.get(i);
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
                for (int k = 0; k <= 8; k++) { // a line of sparks along the arc
                    Vec3 p = from.lerp(to, k / 8.0);
                    level.sendParticles(sword.dusk ? DustParticleOptions.REDSTONE : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.0);
                }
                if (e.hurt(level.damageSources().indirectMagic(player, player), damage) && sword.dusk) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), player);
                }
                from = to;
            }
            level.playSound(null, hit.blockPosition(), ZinogreSounds.ZAP.get(), player.getSoundSource(), 1.0F, sword.dusk ? 0.85F : 1.15F);
            sparks(level, hit.position().add(0, hit.getBbHeight() * 0.5, 0), sword.dusk, 12);
        } finally {
            arcing = false;
        }
    }

    private static void sparks(ServerLevel level, Vec3 at, boolean dusk, int count) {
        ParticleOptions particle = dusk ? DustParticleOptions.REDSTONE : ParticleTypes.ELECTRIC_SPARK;
        level.sendParticles(particle, at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.3);
    }

    private ZinogreWeapons() {}
}
