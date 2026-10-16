package com.example.zinogre;

import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Zinogre and Stygian Zinogre materials (dropped when their parts break and when they die) and the two armor sets made
 * from them (the mod's own 3D designs, coloured like each Zinogre, see client/ZinogreArmorModels):
 *
 * Stormhide (Zinogre): netherite armor upgraded in a smithing table with the Stormhide template and a Zinogre material.
 * Duskhide (Stygian Zinogre): Stormhide upgraded again with the Duskhide template and a Stygian material.
 *
 * Wearing a whole set: whatever hits you up close is sometimes shocked back (Duskhide: more often, harder, and it
 * withers), lightning doesn't hurt you but charges you up, and you're quicker out in a thunderstorm.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ZinogreItems {
    public static final DeferredRegister.Items ITEMS = ZinogreMod.ITEMS;
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ZinogreMod.MOD_ID);

    // ---- Zinogre materials
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

    // ---- Stygian Zinogre materials (the same parts)
    public static final DeferredItem<Item> STYGIAN_HORN = ITEMS.registerSimpleItem("stygian_horn", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> STYGIAN_CLAW = ITEMS.registerSimpleItem("stygian_claw", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> STYGIAN_SHELL = ITEMS.registerSimpleItem("stygian_shell", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> STYGIAN_TAIL = ITEMS.registerSimpleItem("stygian_tail", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> STYGIAN_FUR = ITEMS.registerSimpleItem("stygian_fur", new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---- armor
    /** netherite: 3 8 6 3, toughness 3, knockback resistance 0.1 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STORMHIDE = material("stormhide", 4, 9, 7, 4, 18, 3.5F, 0.12F, SHELL);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DUSKHIDE = material("duskhide", 5, 10, 8, 5, 20, 4.5F, 0.15F, STYGIAN_SHELL);

    public static final DeferredItem<ArmorItem> HELMET = armor("stormhide_helmet", STORMHIDE, ArmorItem.Type.HELMET, 42, false);
    public static final DeferredItem<ArmorItem> CHESTPLATE = armor("stormhide_chestplate", STORMHIDE, ArmorItem.Type.CHESTPLATE, 42, false);
    public static final DeferredItem<ArmorItem> LEGGINGS = armor("stormhide_leggings", STORMHIDE, ArmorItem.Type.LEGGINGS, 42, false);
    public static final DeferredItem<ArmorItem> BOOTS = armor("stormhide_boots", STORMHIDE, ArmorItem.Type.BOOTS, 42, false);
    public static final DeferredItem<ArmorItem> DUSK_HELMET = armor("duskhide_helmet", DUSKHIDE, ArmorItem.Type.HELMET, 50, true);
    public static final DeferredItem<ArmorItem> DUSK_CHESTPLATE = armor("duskhide_chestplate", DUSKHIDE, ArmorItem.Type.CHESTPLATE, 50, true);
    public static final DeferredItem<ArmorItem> DUSK_LEGGINGS = armor("duskhide_leggings", DUSKHIDE, ArmorItem.Type.LEGGINGS, 50, true);
    public static final DeferredItem<ArmorItem> DUSK_BOOTS = armor("duskhide_boots", DUSKHIDE, ArmorItem.Type.BOOTS, 50, true);

    /**
     * Smithing templates: netherite armor + the Stormhide template + a Zinogre material (helmet: Thunder Horn,
     * chestplate: Zinogre Tail, leggings: Zinogre Shell, boots: Zinogre Claw) = Stormhide; Stormhide + the Duskhide
     * template + the Stygian material for that piece = Duskhide.
     */
    public static final DeferredItem<Item> UPGRADE_TEMPLATE = template("stormhide_upgrade_smithing_template", "stormhide_upgrade");
    public static final DeferredItem<Item> DUSK_UPGRADE_TEMPLATE = template("duskhide_upgrade_smithing_template", "duskhide_upgrade");

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name, int helmet, int chest, int legs, int boots,
            int enchantability, float toughness, float knockback, DeferredItem<Item> repair) {
        return ARMOR_MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.BODY, chest + 4);
            return new ArmorMaterial(defense, enchantability, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(repair.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, name))), toughness, knockback);
        });
    }

    private static DeferredItem<ArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, int durability, boolean dusk) {
        return ITEMS.register(name, () -> new ZinogreArmor(material, type, dusk,
                new Item.Properties().rarity(Rarity.EPIC).fireResistant().durability(type.getDurability(durability))));
    }

    private static DeferredItem<Item> template(String name, String key) {
        return ITEMS.register(name, () -> new SmithingTemplateItem(
                Component.translatable("item.zinogre." + key + ".applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable("item.zinogre." + key + ".ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable("item.zinogre." + key + ".title").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.zinogre." + key + ".base"),
                Component.translatable("item.zinogre." + key + ".additions"),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"), ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                        ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"), ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots"),
                        ResourceLocation.withDefaultNamespace("item/empty_slot_sword")),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"))));
    }

    /** All the materials, templates and armor, for the creative tab. */
    static List<DeferredItem<? extends Item>> all() {
        return List.of(THUNDER_HORN, CLAW, SHELL, TAIL, FUR, STYGIAN_HORN, STYGIAN_CLAW, STYGIAN_SHELL, STYGIAN_TAIL, STYGIAN_FUR,
                UPGRADE_TEMPLATE, DUSK_UPGRADE_TEMPLATE, HELMET, CHESTPLATE, LEGGINGS, BOOTS, DUSK_HELMET, DUSK_CHESTPLATE, DUSK_LEGGINGS, DUSK_BOOTS);
    }

    /** Called from the mod's constructor so the registers exist before registration starts. */
    static void init() {}

    /** A piece of either set, with its set bonus in the tooltip. */
    public static final class ZinogreArmor extends ArmorItem {
        public final boolean dusk;

        ZinogreArmor(Holder<ArmorMaterial> material, Type type, boolean dusk, Properties properties) {
            super(material, type, properties);
            this.dusk = dusk;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            String set = dusk ? "duskhide" : "stormhide";
            tooltip.add(Component.translatable("item.zinogre.armor.set").withStyle(dusk ? ChatFormatting.RED : ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.zinogre." + set + ".set.shock").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.zinogre." + set + ".set.lightning").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.zinogre.armor.set.storm").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- the set bonus

    /** Which whole set it's wearing: 1 Stormhide, 2 Duskhide, 0 neither (pieces of both don't count). */
    public static int fullSet(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD), chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS), feet = entity.getItemBySlot(EquipmentSlot.FEET);
        if (head.is(HELMET.get()) && chest.is(CHESTPLATE.get()) && legs.is(LEGGINGS.get()) && feet.is(BOOTS.get())) {
            return 1;
        }
        if (head.is(DUSK_HELMET.get()) && chest.is(DUSK_CHESTPLATE.get()) && legs.is(DUSK_LEGGINGS.get()) && feet.is(DUSK_BOOTS.get())) {
            return 2;
        }
        return 0;
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity wearer = event.getEntity();
        int set = wearer.level().isClientSide ? 0 : fullSet(wearer);
        if (set == 0) {
            return;
        }
        ServerLevel level = (ServerLevel) wearer.level();
        // lightning: no harm, it charges you up (Duskhide: stronger)
        if (event.getSource().is(DamageTypeTags.IS_LIGHTNING)) {
            event.setCanceled(true);
            MobEffectInstance speed = wearer.getEffect(MobEffects.MOVEMENT_SPEED);
            if (speed == null || speed.getAmplifier() < 1) {
                level.playSound(null, wearer.blockPosition(), ZinogreSounds.CHARGE_UP.get(), wearer.getSoundSource(), 1.0F, set == 2 ? 0.9F : 1.2F);
            }
            wearer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
            wearer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, set == 2 ? 1 : 0));
            return;
        }
        // something hit you up close: now and then it gets a shock back
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker != wearer && attacker.isAlive()
                && wearer.getRandom().nextFloat() < (set == 2 ? 0.35F : 0.25F)) {
            if (attacker.hurt(level.damageSources().thorns(wearer), set == 2 ? 4.0F : 3.0F)) {
                level.sendParticles(set == 2 ? net.minecraft.core.particles.DustParticleOptions.REDSTONE : ParticleTypes.ELECTRIC_SPARK,
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                        20, attacker.getBbWidth() * 0.4, attacker.getBbHeight() * 0.3, attacker.getBbWidth() * 0.4, 0.4);
                level.playSound(null, attacker.blockPosition(), ZinogreSounds.ZAP.get(), wearer.getSoundSource(), 0.8F, set == 2 ? 0.9F : 1.3F);
                if (set == 2) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), wearer);
                }
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
        if (player.level().isThundering() && player.level().canSeeSky(player.blockPosition()) && fullSet(player) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false, true));
        }
    }

    /** The material a broken part drops (null: none): Zinogre or Stygian, by the part. */
    @Nullable
    static Item partMaterial(boolean stygian, int group) {
        return switch (group) {
            case ZinogreEntity.HORN_L, ZinogreEntity.HORN_R -> (stygian ? STYGIAN_HORN : THUNDER_HORN).get();
            case ZinogreEntity.ARMS -> (stygian ? STYGIAN_CLAW : CLAW).get();
            case ZinogreEntity.SHOULDERS -> (stygian ? STYGIAN_SHELL : SHELL).get();
            case ZinogreEntity.TAIL -> (stygian ? STYGIAN_TAIL : TAIL).get();
            default -> null;
        };
    }

    private ZinogreItems() {}
}
