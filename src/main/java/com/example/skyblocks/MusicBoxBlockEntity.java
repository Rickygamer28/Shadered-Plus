package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A music box: when its tune started (game time, kept and sent to every player, so each computer plays it from the
 * same moment). On the client it plays the notes, puffs out a music note for each, and keeps itself in a list of the
 * music boxes playing near you, for the fish and fireflies to move in time with ({@link #beat}).
 */
public class MusicBoxBlockEntity extends BlockEntity {
    private long start;
    /** (client) how far open its lid is, eased each frame by client/MusicBoxRenderer. */
    public float open;

    /** (client) the music boxes playing now. */
    private static final Map<BlockPos, MusicBoxBlockEntity> PLAYING = new HashMap<>();

    public MusicBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSIC_BOX.get(), pos, state);
    }

    public long start() {
        return start;
    }

    void start(long gameTime) {
        start = gameTime;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public boolean playing() {
        return getBlockState().getBlock() instanceof MusicBoxBlock && getBlockState().getValue(MusicBoxBlock.PLAYING);
    }

    public int song() {
        return getBlockState().getBlock() instanceof MusicBoxBlock ? getBlockState().getValue(MusicBoxBlock.SONG) : 0;
    }

    /** How far into its tune it is (ticks, round and round), or -1 before it starts. */
    public int tick(long gameTime) {
        long e = gameTime - start;
        return e < 0 ? -1 : (int) (e % MusicBoxSongs.length(song()));
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, MusicBoxBlockEntity be) {
        if (!state.getValue(MusicBoxBlock.PLAYING)) {
            PLAYING.remove(pos);
            return;
        }
        PLAYING.put(pos.immutable(), be);
        int song = state.getValue(MusicBoxBlock.SONG);
        int tick = be.tick(level.getGameTime());
        if (tick < 0) {
            return;
        }
        int note = MusicBoxSongs.noteAt(song, tick);
        if (note < 0) {
            return;
        }
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.playLocalSound(x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS, 0.55F, pitch, false);
        if (note % 12 == 0 || note % 12 == 7) { // (a softer chime under some notes: a fuller sound, like a real comb)
            level.playLocalSound(x, y, z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.RECORDS, 0.12F, pitch, false);
        }
        level.addParticle(ParticleTypes.NOTE, x + (level.random.nextDouble() - 0.5) * 0.3, y + 0.35, z + (level.random.nextDouble() - 0.5) * 0.3,
                note / 24.0, 0.0, 0.0);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            PLAYING.remove(worldPosition);
        }
    }

    /**
     * (client) The music near this spot: {how strongly to move in time (0 none: no music box playing within the
     * radius .. 1 right on a note, fading till the next), which note it is (counting up: for taking turns), how near
     * the music box is (0 none .. 1 close: how much to follow it at all)}.
     */
    public static float[] beat(Level level, double x, double y, double z, double radius, float partialTick) {
        float best = 0.0F, which = 0.0F, nearest = 0.0F;
        var it = PLAYING.entrySet().iterator();
        while (it.hasNext()) {
            MusicBoxBlockEntity be = it.next().getValue();
            if (be.isRemoved() || be.level != level || !be.playing()) {
                it.remove();
                continue;
            }
            BlockPos p = be.worldPosition;
            double d2 = p.distToCenterSqr(x, y, z);
            if (d2 > radius * radius) {
                continue;
            }
            int tick = be.tick(level.getGameTime());
            if (tick < 0) {
                continue;
            }
            int[] last = MusicBoxSongs.lastNote(be.song(), tick);
            float since = tick - last[0] + partialTick;
            float near = Math.min(1.0F, (float) (1.0 - Math.sqrt(d2) / radius) * 3.0F);
            float k = (float) Math.exp(-since * 0.22) * near;
            nearest = Math.max(nearest, near);
            if (k > best) {
                best = k;
                which = last[1] + (float) ((level.getGameTime() - be.start) / MusicBoxSongs.length(be.song())) * 1000.0F;
            }
        }
        return new float[] {best, which, nearest};
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("start", start);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        start = tag.getLong("start");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
