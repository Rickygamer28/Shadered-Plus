package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The arena core's memory: whether its Alpha has come out already (it does once), and whether it has lit the arena.
 * The first time it ticks it fills the cavern with hidden light (so the arena is bright and nothing spawns in it,
 * also in arenas generated before this was added) and clears out the monsters that spawned there in the dark.
 */
public class ZinogreArenaCoreBlockEntity extends BlockEntity {
    /** How far out from the middle a player brings the boss out (the whole cavern, ledge included). */
    private static final double TRIGGER_RADIUS = 33.0;
    /** How far from the middle the boss's pen is. */
    private static final int PEN_DISTANCE = 28;
    /** Bumped when the lighting gets better, so arenas lit by an older version are lit again. */
    private static final int LIGHTING_VERSION = 2;

    private boolean bossOut;
    private int litVersion;

    public ZinogreArenaCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ZinogreMod.ARENA_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (litVersion < LIGHTING_VERSION) {
            light(server, pos, state);
        }
        if (bossOut || level.getGameTime() % 20 != Math.floorMod(pos.asLong(), 20)) {
            return;
        }
        // someone (not a spectator) is in the arena: in the pit, on the ledge or coming in from a tunnel
        AABB pit = new AABB(pos).inflate(TRIGGER_RADIUS, 0, TRIGGER_RADIUS).expandTowards(0, 24, 0).expandTowards(0, -2, 0);
        boolean someone = !level.getEntitiesOfClass(Player.class, pit, p -> !p.isSpectator() && p.isAlive()
                && p.position().subtract(pos.getCenter()).horizontalDistanceSqr() < TRIGGER_RADIUS * TRIGGER_RADIUS).isEmpty();
        if (!someone) {
            return;
        }
        bossOut = true;
        setChanged();
        boolean stygian = state.getBlock() instanceof ZinogreArenaCoreBlock core && core.stygian;
        Direction toPen = state.getValue(ZinogreArenaCoreBlock.FACING);
        BlockPos at = pos.relative(toPen, PEN_DISTANCE).above(2);
        ZinogreEntity boss = (stygian ? ZinogreMod.ALPHA_STYGIAN_ZINOGRE : ZinogreMod.ALPHA_ZINOGRE).get().create(server);
        if (boss == null) {
            return;
        }
        float yaw = toPen.getOpposite().toYRot(); // facing out of the pen, into the pit
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
        boss.setPersistenceRequired();
        server.addFreshEntity(boss);
        server.playSound(null, at, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.5F);
        server.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3.0F, 0.6F);
        Component message = Component.translatable(stygian ? "message.zinogre.arena_stygian" : "message.zinogre.arena");
        for (Player p : level.getEntitiesOfClass(Player.class, pit.inflate(16))) {
            p.displayClientMessage(message, true);
        }
    }

    /** Lights the arena evenly (see ArenaLighting) once its chunks are all loaded, and out with the monsters in it. */
    private void light(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != Math.floorMod(pos.asLong(), 20) || !ArenaLighting.loaded(level, pos)) {
            return;
        }
        litVersion = LIGHTING_VERSION;
        setChanged();
        boolean stygian = state.getBlock() instanceof ZinogreArenaCoreBlock core && core.stygian;
        int[] result = ArenaLighting.light(level, pos, stygian ? 10 : 12);
        int r = ArenaLighting.RADIUS;
        AABB cavern = new AABB(pos).inflate(r, 0, r).expandTowards(0, ArenaLighting.ABOVE, 0).expandTowards(0, -ArenaLighting.BELOW, 0);
        for (Monster monster : level.getEntitiesOfClass(Monster.class, cavern, m -> !m.isPersistenceRequired() && !m.hasCustomName())) {
            monster.discard();
        }
        ZinogreMod.LOGGER.info("Lit the arena at {}: {} lights added, darkest surface now {}", pos, result[0], result[1]);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("BossOut", bossOut);
        tag.putInt("LitVersion", litVersion);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bossOut = tag.getBoolean("BossOut");
        litVersion = tag.getInt("LitVersion");
    }
}
