package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The arena core's memory: whether its Alpha has come out already (it does once). */
public class ZinogreArenaCoreBlockEntity extends BlockEntity {
    /** The pit's radius (blocks), and how far from the middle the boss's pen is. */
    private static final double PIT_RADIUS = 25.0;
    private static final int PEN_DISTANCE = 28;

    private boolean bossOut;

    public ZinogreArenaCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ZinogreMod.ARENA_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    void serverTick(Level level, BlockPos pos, BlockState state) {
        if (bossOut || level.getGameTime() % 20 != pos.asLong() % 20 || !(level instanceof ServerLevel server)) {
            return;
        }
        // someone (not a spectator) is down in the pit
        AABB pit = new AABB(pos).inflate(PIT_RADIUS, 0, PIT_RADIUS).expandTowards(0, 9, 0);
        boolean someone = !level.getEntitiesOfClass(Player.class, pit, p -> !p.isSpectator() && p.isAlive()
                && p.position().subtract(pos.getCenter()).horizontalDistanceSqr() < PIT_RADIUS * PIT_RADIUS).isEmpty();
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("BossOut", bossOut);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bossOut = tag.getBoolean("BossOut");
    }
}
