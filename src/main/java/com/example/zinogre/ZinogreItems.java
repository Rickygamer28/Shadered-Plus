package com.example.zinogre;

import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Zinogre materials (dropped when its parts break and when it dies) and the Stormhide armor made from them.
 *
 * Stormhide (an original design: gunmetal plates over indigo cloth, fur trim, electric-blue lines, worn in 3D) is the
 * step after netherite: netherite armor upgraded in a smithing table (see UPGRADE_TEMPLATE), tougher still and also
 * fireproof. Wearing the whole set: whatever hits you is sometimes shocked back, lightning doesn't hurt you
 * but charges you up (Speed II and Strength I for 30 s), and you're quicker out in a thunderstorm.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ZinogreItems {
    public static final DeferredRegister.Items ITEMS = ZinogreMod.ITEMS;
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ZinogreMod.MOD_ID);

    // ---- materials
    /** From a broken horn. */
    public static final DeferredItem<Item> THUNDER_HORN = ITEMS.registerSimpleItem("thunder_horn", new Item.Properties().rarity(Rarity.UNCOMMON));
    /** From broken front legs. */
    public static final DeferredItem<Item> CLAW = ITEMS.registerSimpleItem("zinogre_claw", new Item.Properties());
    /** From broken shoulders, and when it dies. */
    public static final DeferredItem<Item> SHELL = ITEMS.registerSimpleItem("zinogre_shell", new Item.Properties());
    /** From a cut tail. */
    public static final DeferredItem<Item> TAIL = ITEMS.registerSimpleItem("zinogre_tail", new Item.Properties().rarity(Rarity.UNCOMMON));
    /** When it dies. */
    public static final DeferredItem<Item> FUR = ITEMS.registerSimpleItem("electro_fur", new Item.Properties());

    // ---- armor
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STORMHIDE = ARMOR_MATERIALS.register("stormhide", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 4);       // netherite: 3 8 6 3, toughness 3, knockback resistance 0.1
        defense.put(ArmorItem.Type.CHESTPLATE, 9);
        defense.put(ArmorItem.Type.LEGGINGS, 7);
        defense.put(ArmorItem.Type.BOOTS, 4);
        defense.put(ArmorItem.Type.BODY, 13);
        return new ArmorMaterial(defense, 18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(SHELL.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "stormhide"))), 3.5F, 0.12F);
    });
    public static final DeferredItem<ArmorItem> HELMET = armor("stormhide_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> CHESTPLATE = armor("stormhide_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> LEGGINGS = armor("stormhide_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> BOOTS = armor("stormhide_boots", ArmorItem.Type.BOOTS);

    private static DeferredItem<ArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new StormhideArmor(type, new Item.Properties().rarity(Rarity.EPIC).fireResistant().durability(type.getDurability(42))));
    }

    /**
     * Netherite armor becomes Stormhide in a smithing table with this template and a material (helmet: Thunder Horn,
     * chestplate: Zinogre Tail, leggings: Zinogre Shell, boots: Zinogre Claw). Crafted from Zinogre materials.
     */
    public static final DeferredItem<Item> UPGRADE_TEMPLATE = ITEMS.register("stormhide_upgrade_smithing_template", () -> new net.minecraft.world.item.SmithingTemplateItem(
            Component.translatable("item.zinogre.stormhide_upgrade.applies_to").withStyle(ChatFormatting.BLUE),
            Component.translatable("item.zinogre.stormhide_upgrade.ingredients").withStyle(ChatFormatting.BLUE),
            Component.translatable("item.zinogre.stormhide_upgrade.title").withStyle(ChatFormatting.GRAY),
            Component.translatable("item.zinogre.stormhide_upgrade.base"),
            Component.translatable("item.zinogre.stormhide_upgrade.additions"),
            List.of(ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"), ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                    ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"), ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
            List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"))));

    /** All the Zinogre materials and the armor, for the creative tab. */
    static List<DeferredItem<? extends Item>> all() {
        return List.of(THUNDER_HORN, CLAW, SHELL, TAIL, FUR, UPGRADE_TEMPLATE, HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    /** Called from the mod's constructor so the registers exist before registration starts. */
    static void init() {}

    /** Stormhide armor with the set bonus in its tooltip. */
    private static final class StormhideArmor extends ArmorItem {
        StormhideArmor(Type type, Properties properties) {
            super(STORMHIDE, type, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.zinogre.stormhide.set").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.zinogre.stormhide.set.shock").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.zinogre.stormhide.set.lightning").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.zinogre.stormhide.set.storm").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- the set bonus

    /** Whether it's wearing all four Stormhide pieces. */
    public static boolean fullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get()) && entity.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE.get())
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get()) && entity.getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get());
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide || !fullSet(wearer)) {
            return;
        }
        ServerLevel level = (ServerLevel) wearer.level();
        // lightning: no harm, it charges you up
        if (event.getSource().is(DamageTypeTags.IS_LIGHTNING)) {
            event.setCanceled(true);
            if (!wearer.hasEffect(MobEffects.MOVEMENT_SPEED) || wearer.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() < 1) {
                level.playSound(null, wearer.blockPosition(), ZinogreSounds.CHARGE_UP.get(), wearer.getSoundSource(), 1.0F, 1.2F);
            }
            wearer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
            wearer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
            return;
        }
        // something hit you up close: one time in four it gets a shock back
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker != wearer && attacker.isAlive()
                && wearer.getRandom().nextFloat() < 0.25F) {
            if (attacker.hurt(level.damageSources().thorns(wearer), 3.0F)) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                        20, attacker.getBbWidth() * 0.4, attacker.getBbHeight() * 0.3, attacker.getBbWidth() * 0.4, 0.4);
                level.playSound(null, attacker.blockPosition(), ZinogreSounds.ZAP.get(), wearer.getSoundSource(), 0.8F, 1.3F);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0) {
            return;
        }
        // out in a thunderstorm: quicker on your feet
        if (player.level().isThundering() && player.level().canSeeSky(player.blockPosition()) && fullSet(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false, true));
        }
    }

    private ZinogreItems() {}
}
