package com.example.skyblocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A chalk rune's magic circle. Every second: is a ring of chalk (marks or runes) closed round it - 7 x 7 (a big circle),
 * or 5 x 5? Then the circle's lit (ACTIVE), and its rune works on what's inside it (the ring's reach, three blocks up):
 * feather - slow falling; heart - regeneration; ward - monsters pushed out of it; swift - speed; growth - plants grown
 * (as with bone meal, now and then); moon - night vision.
 */
public class ChalkRuneBlockEntity extends BlockEntity {
    public static final String[] RUNES = {"", "feather", "heart", "ward", "swift", "growth", "moon"};
    /** (client and server) How far its ring reaches (2, 3), or 0: not closed. */
    private int ring;

    public ChalkRuneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHALK_RUNE.get(), pos, state);
    }

    private long ringAt = Long.MIN_VALUE / 2;

    /** Its ring's reach while it's lit (2 or 3), 0 if not (on a client: looked at again every second). */
    public int ring() {
        if (!getBlockState().getValue(ChalkRuneBlock.ACTIVE)) {
            return 0;
        }
        if (level != null && level.isClientSide && level.getGameTime() - ringAt >= 20) {
            ringAt = level.getGameTime();
            ring = closed(level, worldPosition);
        }
        return ring;
    }

    /** The ring closed round a rune: its reach (3, then 2), or 0. */
    static int closed(Level level, BlockPos pos) {
        if (level == null) {
            return 0;
        }
        for (int r = 3; r >= 2; r--) {
            boolean all = true;
            for (int dx = -r; dx <= r && all; dx++) {
                for (int dz = -r; dz <= r && all; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockState s = level.getBlockState(pos.offset(dx, 0, dz));
                    all = s.getBlock() instanceof ChalkMarkBlock || s.getBlock() instanceof ChalkRuneBlock;
                }
            }
            if (all) {
                return r;
            }
        }
        return 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChalkRuneBlockEntity be) {
        long time = level.getGameTime() + pos.hashCode();
        if (time % 20 == 0) {
            int r = closed(level, pos);
            boolean active = r > 0;
            if (active != state.getValue(ChalkRuneBlock.ACTIVE)) {
                level.setBlock(pos, state.setValue(ChalkRuneBlock.ACTIVE, active), 3);
                level.playSound(null, pos, active ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F,
                        active ? 1.3F : 0.8F);
            }
            be.ring = r;
        }
        if (be.ring <= 0 || !state.getValue(ChalkRuneBlock.ACTIVE)) {
            return;
        }
        int rune = state.getValue(ChalkRuneBlock.RUNE);
        double reach = be.ring + 0.5;
        AABB area = new AABB(pos.getX() + 0.5 - reach, pos.getY(), pos.getZ() + 0.5 - reach, pos.getX() + 0.5 + reach, pos.getY() + 3, pos.getZ() + 0.5 + reach);
        if (rune == 3 && time % 4 == 0) {
            // ward: monsters inside pushed out, away from its middle
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e instanceof Enemy)) {
                Vec3 away = new Vec3(e.getX() - (pos.getX() + 0.5), 0, e.getZ() - (pos.getZ() + 0.5));
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.setDeltaMovement(away.x * 0.6, 0.25, away.z * 0.6);
                e.hurtMarked = true;
            }
            return;
        }
        if (time % 20 != 0) {
            return;
        }
        if (rune == 5 && level instanceof ServerLevel sl) {
            // growth: a plant or two in it grown, as bone meal would
            for (int i = 0; i < 2; i++) {
                BlockPos p = pos.offset(level.random.nextInt(2 * be.ring + 1) - be.ring, level.random.nextInt(3) - 1, level.random.nextInt(2 * be.ring + 1) - be.ring);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, p, s) && b.isBonemealSuccess(level, level.random, p, s)) {
                    b.performBonemeal(sl, level.random, p, s);
                    level.levelEvent(1505, p, 0); // (its green sparkles)
                }
            }
            return;
        }
        List<LivingEntity> in = level.getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Enemy) || rune == 1 || rune == 6);
        for (LivingEntity e : in) {
            switch (rune) {
                case 1 -> e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, true));
                case 2 -> {
                    if (!e.hasEffect(MobEffects.REGENERATION) || e.getEffect(MobEffects.REGENERATION).getDuration() < 30) {
                        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 70, 0, true, true));
                    }
                }
                case 4 -> e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, true));
                case 6 -> e.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, true));
                default -> {}
            }
        }
    }
}
