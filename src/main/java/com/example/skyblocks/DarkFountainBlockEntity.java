package com.example.skyblocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A dark fountain, cut open with the rift knife: the ground tears open, then darkness pours up out of it in a towering
 * column (client/DarkFountainRenderer) - anything that steps into it is carried up, and floats down after. Cut it
 * again with the knife and it sinks back into the ground and the tear closes.
 */
public class DarkFountainBlockEntity extends BlockEntity {
    /** How long the tear takes to open, the column to rise (after it), and all of it to close (ticks). */
    public static final int TEAR = 30, RISE = 50, CLOSE = 60;
    /** How tall the column stands (blocks), and how wide it is (radius, blocks). */
    public static final float HEIGHT = 48.0F, RADIUS = 1.1F;

    /** When it was cut open, and when it began to close (-1: it hasn't); game time. */
    private long opened = -1, closing = -1;
    /** The sky pouring up out of it (Shadered's sky type and pass, by name): the knife's other hand's sky block, or Space. */
    private String sky = "SPACE", pass = "NORMAL";

    public DarkFountainBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DARK_FOUNTAIN.get(), pos, state);
    }

    public String sky() {
        return sky;
    }

    public String pass() {
        return pass;
    }

    public void setSky(String sky, String pass) {
        this.sky = sky;
        this.pass = pass;
        changed();
    }

    public long opened() {
        return opened;
    }

    public long closing() {
        return closing;
    }

    /** How open it is now (0 .. 1: the tear, then the column), by game time. */
    public float tear(float time) {
        float open = clamp((time - opened) / TEAR);
        return closing < 0 ? open : Math.min(open, 1.0F - clamp((time - closing - CLOSE * 0.5F) / (CLOSE * 0.5F)));
    }

    /** How high the column stands now (0 .. 1 of HEIGHT). */
    public float column(float time) {
        float t = clamp((time - opened - TEAR) / RISE);
        float up = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        return closing < 0 ? up : Math.min(up, 1.0F - clamp((time - closing) / (CLOSE * 0.6F)));
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    public void open() {
        if (level == null) {
            return;
        }
        opened = level.getGameTime();
        closing = -1;
        changed();
    }

    public void close() {
        if (level == null || closing >= 0) {
            return;
        }
        closing = level.getGameTime();
        changed();
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DarkFountainBlockEntity be) {
        long now = level.getGameTime();
        if (be.opened < 0) {
            be.open();
            return;
        }
        if (be.closing >= 0 && now - be.closing > CLOSE) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        long age = now - be.opened;
        if (age == TEAR) {
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.2F, 0.6F);
        }
        if (age > TEAR && now % 70 == Math.floorMod(pos.hashCode(), 70)) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.7F, 0.45F);
        }
        float height = be.column(now) * HEIGHT;
        if (height < 1.0F) {
            return;
        }
        // anything in the column is carried up it, and floats down after
        AABB box = new AABB(pos.getX() + 0.5 - RADIUS, pos.getY(), pos.getZ() + 0.5 - RADIUS, pos.getX() + 0.5 + RADIUS, pos.getY() + height,
                pos.getZ() + 0.5 + RADIUS);
        List<Entity> in = level.getEntitiesOfClass(Entity.class, box, e -> !e.isSpectator() && !(e instanceof SkyTrailEntity));
        for (Entity e : in) {
            double dx = e.getX() - (pos.getX() + 0.5), dz = e.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz > RADIUS * RADIUS) {
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            double top = pos.getY() + height - e.getY();
            double up = top > 3 ? 0.55 : top > 0 ? 0.15 : v.y;
            // (pulled in towards the middle a little, turning as it goes)
            e.setDeltaMovement(v.x * 0.8 - dx * 0.04 - dz * 0.03, Math.max(v.y, up), v.z * 0.8 - dz * 0.04 + dx * 0.03);
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, false, true));
            }
        }
        if (level instanceof ServerLevel server && now % 4 == 0) {
            server.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 3, 0.6, 0.05, 0.6, 0.08);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("opened", opened);
        tag.putLong("closing", closing);
        tag.putString("sky", sky);
        tag.putString("pass", pass);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        opened = tag.contains("opened") ? tag.getLong("opened") : -1;
        closing = tag.contains("closing") ? tag.getLong("closing") : -1;
        if (tag.contains("sky")) {
            sky = tag.getString("sky");
            pass = tag.getString("pass");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
