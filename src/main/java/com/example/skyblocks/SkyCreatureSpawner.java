package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where sky creatures come from. By themselves (checked every 10 seconds round each player in the Overworld):
 * jellyfish high up on clear nights, a whale over the sea at dusk (rarely), moths round sky lanterns at night. Made by
 * players: a slimeball dropped into a sky liquid brings up a jellyfish of that sky, a Heart of the Sea a whale; glowstone
 * dust on a sky lantern draws a moth of its sky.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
public final class SkyCreatureSpawner {
    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 200 != 37) {
            return;
        }
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        long day = level.getDayTime() % 24000L;
        boolean night = day > 13000L && day < 23000L;
        boolean dusk = day > 11800L && day < 13800L;
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos at = player.blockPosition();
            // jellyfish: clear nights, out under the sky, high up
            if (night && !level.isRaining() && level.canSeeSky(at) && random.nextInt(4) == 0
                    && count(level, ModEntities.SKY_JELLYFISH.get(), player.position(), 96) < 6) {
                double a = random.nextDouble() * Math.PI * 2, d = 24 + random.nextDouble() * 20;
                double y = Math.max(player.getY() + 22, 150) + random.nextDouble() * 10;
                Vec3 c = new Vec3(player.getX() + Math.cos(a) * d, Math.min(y, level.getMaxBuildHeight() - 4), player.getZ() + Math.sin(a) * d);
                SkyblockType sky = randomSky(random);
                for (int i = 0, n = 2 + random.nextInt(3); i < n; i++) {
                    spawn(level, ModEntities.SKY_JELLYFISH.get(), c.add(random.nextGaussian() * 3, random.nextGaussian(), random.nextGaussian() * 3), sky, true);
                }
            }
            // a whale: over the sea at dusk, rarely, one at a time
            if (dusk && level.getBiome(at).is(BiomeTags.IS_OCEAN) && random.nextInt(30) == 0
                    && count(level, ModEntities.SKY_WHALE.get(), player.position(), 192) == 0) {
                double a = random.nextDouble() * Math.PI * 2, d = 64 + random.nextDouble() * 32;
                Vec3 c = new Vec3(player.getX() + Math.cos(a) * d, level.getSeaLevel() + 40 + random.nextInt(20), player.getZ() + Math.sin(a) * d);
                spawn(level, ModEntities.SKY_WHALE.get(), c, randomSky(random), true);
            }
            // moths: round sky lanterns, at night
            if (night) {
                for (SkyLanternBlockEntity lantern : lanternsNear(level, player)) {
                    Vec3 c = Vec3.atCenterOf(lantern.getBlockPos());
                    if (random.nextInt(3) == 0 && count(level, ModEntities.SKY_MOTH.get(), c, 8) < 3) {
                        SkyblockType sky = skyNamed(lantern.sky());
                        spawn(level, ModEntities.SKY_MOTH.get(), c.add(random.nextGaussian(), 1.0, random.nextGaussian()), sky, true);
                    }
                }
            }
        }
    }

    /** Sky lanterns in the chunks round a player (up to 6 of them). */
    private static List<SkyLanternBlockEntity> lanternsNear(ServerLevel level, Player player) {
        List<SkyLanternBlockEntity> out = new ArrayList<>();
        ChunkPos center = player.chunkPosition();
        for (int dx = -2; dx <= 2 && out.size() < 6; dx++) {
            for (int dz = -2; dz <= 2 && out.size() < 6; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SkyLanternBlockEntity lantern && out.size() < 6) {
                        out.add(lantern);
                    }
                }
            }
        }
        return out;
    }

    private static int count(ServerLevel level, EntityType<?> type, Vec3 at, double radius) {
        return level.getEntities(type, new net.minecraft.world.phys.AABB(at, at).inflate(radius), e -> true).size();
    }

    public static SkyblockType randomSky(RandomSource random) {
        SkyblockType[] skies = SkyblockType.values();
        return skies[random.nextInt(skies.length)];
    }

    @Nullable
    public static SkyblockType skyNamed(String name) {
        for (SkyblockType sky : SkyblockType.values()) {
            if (sky.name().equals(name)) {
                return sky;
            }
        }
        return null;
    }

    /** A sky creature at pos (with that sky, or a random one), wild or kept. */
    @Nullable
    public static <T extends SkyCreature> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos, @Nullable SkyblockType sky, boolean wild) {
        if (!level.isLoaded(BlockPos.containing(pos))) {
            return null;
        }
        T creature = type.create(level);
        if (creature == null) {
            return null;
        }
        creature.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
        creature.setSky(sky);
        creature.setHome(pos);
        creature.setWild(wild);
        creature.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), wild ? MobSpawnType.NATURAL : MobSpawnType.MOB_SUMMONED, null);
        if (!wild) {
            creature.setPersistenceRequired();
        }
        level.addFreshEntity(creature);
        return creature;
    }

    // ---- made by players ----

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean slime = stack.is(Items.SLIME_BALL), heart = stack.is(Items.HEART_OF_THE_SEA);
        if (!slime && !heart) {
            return;
        }
        Player player = event.getEntity();
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        FluidState fluid = player.level().getFluidState(block.getBlockPos());
        ModFluids.SkyLiquid liquid = fluid.isSource() ? ModFluids.of(fluid.getFluidType()) : null;
        if (liquid == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(block.getBlockPos());
        if (slime) {
            spawn(level, ModEntities.SKY_JELLYFISH.get(), at.add(0, 1.2, 0), liquid.sky, false);
            level.playSound(null, block.getBlockPos(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 1.4F);
        } else {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, block.getBlockPos().getX(), block.getBlockPos().getZ());
            spawn(level, ModEntities.SKY_WHALE.get(), new Vec3(at.x, Math.max(at.y + 8, top + 6), at.z), liquid.sky, false);
            level.playSound(null, block.getBlockPos(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        }
        stack.consume(1, player);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GLOWSTONE_DUST) || !(event.getLevel().getBlockEntity(event.getPos()) instanceof SkyLanternBlockEntity lantern)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getLevel() instanceof ServerLevel level) {
            spawn(level, ModEntities.SKY_MOTH.get(), Vec3.atCenterOf(event.getPos()).add(0, 1.0, 0), skyNamed(lantern.sky()), false);
            level.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
            stack.consume(1, event.getEntity());
        }
    }

    private SkyCreatureSpawner() {}
}
