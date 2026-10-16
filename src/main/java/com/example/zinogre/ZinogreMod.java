package com.example.zinogre;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Zinogre (personal build): one mob, drawn with Shadered's model renderer from assets/zinogre/models/complex/zinogre.glb.
 * Spawned with its spawn egg only (no natural spawning). Tamable with meat. Its head drops
 * (as a placeable trophy) when a charged creeper kills it. The Alpha bosses (three times as big) drop an egg that
 * hatches a Zinogre when lightning strikes it.
 */
@Mod(ZinogreMod.MOD_ID)
public class ZinogreMod {
    public static final String MOD_ID = "zinogre";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /** About 2.6 blocks wide and 2.4 tall (the body; the tail reaches further back). */
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> ZINOGRE = ENTITIES.register("zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("zinogre"));

    /** The Stygian Zinogre: the same mob (same class) with its own type, textures and tougher attributes. */
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> STYGIAN_ZINOGRE = ENTITIES.register("stygian_zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("stygian_zinogre"));

    /** The bosses: Alpha Zinogre and Alpha Stygian Zinogre, three times as big (same class: see ZinogreEntity#isAlpha). */
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> ALPHA_ZINOGRE = ENTITIES.register("alpha_zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F * ZinogreEntity.ALPHA_SIZE, 2.4F * ZinogreEntity.ALPHA_SIZE)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("alpha_zinogre"));
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> ALPHA_STYGIAN_ZINOGRE = ENTITIES.register("alpha_stygian_zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F * ZinogreEntity.ALPHA_SIZE, 2.4F * ZinogreEntity.ALPHA_SIZE)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("alpha_stygian_zinogre"));

    public static final DeferredItem<DeferredSpawnEggItem> SPAWN_EGG = ITEMS.register("zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ZINOGRE, 0x2E3B4E, 0x8FE3FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STYGIAN_SPAWN_EGG = ITEMS.register("stygian_zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(STYGIAN_ZINOGRE, 0x16161C, 0xD6283A, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> ALPHA_SPAWN_EGG = ITEMS.register("alpha_zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ALPHA_ZINOGRE, 0x1B2533, 0xFFE45C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ALPHA_STYGIAN_SPAWN_EGG = ITEMS.register("alpha_stygian_zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ALPHA_STYGIAN_ZINOGRE, 0x0B0B0F, 0xFF5A1F, new Item.Properties()));

    /** The eggs the Alphas drop: placed on the ground, they hatch into a (tamed) Zinogre when lightning strikes them. */
    public static final DeferredHolder<Block, ZinogreEggBlock> EGG = BLOCKS.register("zinogre_egg", () -> new ZinogreEggBlock(false, eggProperties()));
    public static final DeferredHolder<Block, ZinogreEggBlock> STYGIAN_EGG = BLOCKS.register("stygian_zinogre_egg", () -> new ZinogreEggBlock(true, eggProperties()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> EGG_ITEM = ITEMS.registerSimpleBlockItem("zinogre_egg", EGG, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STYGIAN_EGG_ITEM = ITEMS.registerSimpleBlockItem("stygian_zinogre_egg", STYGIAN_EGG, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZinogreEggBlockEntity>> EGG_BLOCK_ENTITY = BLOCK_ENTITIES.register("zinogre_egg",
            () -> BlockEntityType.Builder.of(ZinogreEggBlockEntity::new, EGG.get(), STYGIAN_EGG.get()).build(null));

    private static BlockBehaviour.Properties eggProperties() {
        return BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    /** The trophy heads (dropped like mob heads: when a charged creeper's explosion kills it). */
    public static final DeferredHolder<Block, ZinogreHeadBlock> HEAD = BLOCKS.register("zinogre_head", () -> new ZinogreHeadBlock(false, headProperties()));
    public static final DeferredHolder<Block, ZinogreHeadBlock> STYGIAN_HEAD = BLOCKS.register("stygian_zinogre_head", () -> new ZinogreHeadBlock(true, headProperties()));
    public static final DeferredHolder<Block, ZinogreWallHeadBlock> WALL_HEAD = BLOCKS.register("zinogre_wall_head", () -> new ZinogreWallHeadBlock(false, headProperties()));
    public static final DeferredHolder<Block, ZinogreWallHeadBlock> STYGIAN_WALL_HEAD = BLOCKS.register("stygian_zinogre_wall_head", () -> new ZinogreWallHeadBlock(true, headProperties()));
    /** One item per look: placed on the floor (on a stand) or on a wall (on a plaque), like mob heads; wearable. */
    public static final DeferredItem<StandingAndWallBlockItem> HEAD_ITEM = ITEMS.register("zinogre_head",
            () -> new StandingAndWallBlockItem(HEAD.get(), WALL_HEAD.get(), new Item.Properties().rarity(Rarity.EPIC), Direction.DOWN));
    public static final DeferredItem<StandingAndWallBlockItem> STYGIAN_HEAD_ITEM = ITEMS.register("stygian_zinogre_head",
            () -> new StandingAndWallBlockItem(STYGIAN_HEAD.get(), STYGIAN_WALL_HEAD.get(), new Item.Properties().rarity(Rarity.EPIC), Direction.DOWN));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZinogreHeadBlockEntity>> HEAD_BLOCK_ENTITY = BLOCK_ENTITIES.register("zinogre_head",
            () -> BlockEntityType.Builder.of(ZinogreHeadBlockEntity::new, HEAD.get(), STYGIAN_HEAD.get(), WALL_HEAD.get(), STYGIAN_WALL_HEAD.get()).build(null));

    /** The mod's own creative tab: everything Zinogre in one place (they are also in the vanilla tabs). */
    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> TAB = TABS.register("zinogre",
            () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.zinogre"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(HEAD_ITEM.get()))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((parameters, output) -> {
                        output.accept(SPAWN_EGG.get());
                        output.accept(STYGIAN_SPAWN_EGG.get());
                        output.accept(ALPHA_SPAWN_EGG.get());
                        output.accept(ALPHA_STYGIAN_SPAWN_EGG.get());
                        output.accept(EGG_ITEM.get());
                        output.accept(STYGIAN_EGG_ITEM.get());
                        output.accept(HEAD_ITEM.get());
                        output.accept(STYGIAN_HEAD_ITEM.get());
                    })
                    .build());

    private static BlockBehaviour.Properties headProperties() {
        return BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.BONE_BLOCK).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    public ZinogreMod(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(ZinogreMod::attributes);
        modEventBus.addListener(ZinogreMod::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ZINOGRE.get(), ZinogreEntity.createAttributes(false).build());
        event.put(STYGIAN_ZINOGRE.get(), ZinogreEntity.createAttributes(true).build());
        event.put(ALPHA_ZINOGRE.get(), ZinogreEntity.createAlphaAttributes(false).build());
        event.put(ALPHA_STYGIAN_ZINOGRE.get(), ZinogreEntity.createAlphaAttributes(true).build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPAWN_EGG.get());
            event.accept(STYGIAN_SPAWN_EGG.get());
            event.accept(ALPHA_SPAWN_EGG.get());
            event.accept(ALPHA_STYGIAN_SPAWN_EGG.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(HEAD_ITEM.get());
            event.accept(STYGIAN_HEAD_ITEM.get());
            event.accept(EGG_ITEM.get());
            event.accept(STYGIAN_EGG_ITEM.get());
        }
    }
}
