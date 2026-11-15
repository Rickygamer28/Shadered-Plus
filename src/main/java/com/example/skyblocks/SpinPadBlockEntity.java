package com.example.skyblocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * A spin pad: a turntable for an OIIA cat. A cat on it spins - faster and faster the longer it's on (pet it for a
 * boost), a vortex of sky whirling up round it (client/SpinPadRenderer) - and the spinning makes Forge Energy (FE), more
 * the faster it goes (with the square of its speed), passed on to whatever's next to it that takes energy (cables,
 * machines, batteries), or kept. A redstone signal stops it (and lets the cat go). The vortex's sky: right-click the pad
 * with a sky block.
 */
public class SpinPadBlockEntity extends BlockEntity {
    /** FE made each tick at full speed; how much it keeps; how much it passes on each tick, at most. */
    public static final int MAX_FE = 320, CAPACITY = 250_000, PUSH = 4096;
    /** How quickly it speeds up with a cat on (to full: 60 s), slows down without, and what a pet adds. */
    private static final float SPEED_UP = 1.0F / 1200.0F, SLOW_DOWN = 1.0F / 160.0F, PET = 0.06F;

    /** Its energy: nothing put in from outside (it makes its own), taken out by anything. */
    public final Buffer energy = new Buffer();
    private float speed;
    private boolean occupied;
    private String sky = "SKYBLOCKS_SYNTHWAVE", pass = "NORMAL";
    private float sentSpeed = -1;
    private int lastFe;

    /** Client: how far the OIIA spin's got (seconds of its animation, which plays faster as it speeds up), and the plate's turn. */
    public double clientSpin, clientSpinO, clientTurn, clientTurnO;
    public float clientSpeed;

    public SpinPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIN_PAD.get(), pos, state);
    }

    public static final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 0, PUSH);
        }

        void make(int fe) {
            this.energy = Math.min(this.capacity, this.energy + Math.max(0, fe));
        }

        void set(int fe) {
            this.energy = Math.max(0, Math.min(this.capacity, fe));
        }
    }

    /** The spin pad at pos, if there's one. */
    @Nullable
    public static SpinPadBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpinPadBlockEntity be ? be : null;
    }

    public float speed() {
        return speed;
    }

    public boolean occupied() {
        return occupied;
    }

    public int lastFe() {
        return lastFe;
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

    /** The cat on it petted: a little push. */
    public void boost() {
        speed = Math.min(1.0F, speed + PET);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sentSpeed = speed;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Where a cat on the pad stands (its middle, on the plate). */
    public AABB top() {
        return new AABB(worldPosition.getX() + 0.15, worldPosition.getY(), worldPosition.getZ() + 0.15, worldPosition.getX() + 0.85,
                worldPosition.getY() + 1.2, worldPosition.getZ() + 0.85);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpinPadBlockEntity be) {
        boolean stopped = level.hasNeighborSignal(pos);
        List<OiiaCatEntity> cats = stopped ? List.of() : level.getEntitiesOfClass(OiiaCatEntity.class, be.top(), c -> c.isAlive() && !c.isBaby());
        OiiaCatEntity cat = cats.isEmpty() ? null : cats.get(0);
        be.occupied = cat != null;
        if (cat != null) {
            cat.holdOnPad(pos);
            be.speed = Math.min(1.0F, be.speed + SPEED_UP * (cat.isTame() ? 1.0F : 0.5F));
        } else {
            be.speed = Math.max(0.0F, be.speed - SLOW_DOWN);
        }
        // its energy: made with the square of its speed; passed on to its neighbours
        be.lastFe = Math.round(MAX_FE * be.speed * be.speed);
        be.energy.make(be.lastFe);
        for (Direction d : Direction.values()) {
            if (be.energy.getEnergyStored() <= 0) {
                break;
            }
            IEnergyStorage next = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(d), d.getOpposite());
            if (next != null && next.canReceive()) {
                int given = next.receiveEnergy(Math.min(PUSH, be.energy.getEnergyStored()), false);
                be.energy.extractEnergy(given, false);
            }
        }
        if (be.lastFe > 0 || cat != null) {
            be.setChanged();
        }
        // (the client told of its speed when it's changed enough to see)
        if (Math.abs(be.speed - be.sentSpeed) > 0.02F || (be.speed == 0) != (be.sentSpeed == 0)) {
            be.changed();
        }
        if (be.speed > 0.35F && level.getGameTime() % Math.max(6, Math.round(40 - be.speed * 30)) == 0) {
            level.playSound(null, pos, SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 0.15F + be.speed * 0.2F, 0.6F + be.speed * 1.2F);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SpinPadBlockEntity be) {
        be.clientSpeed += (be.speed - be.clientSpeed) * 0.15F;
        be.clientSpinO = be.clientSpin;
        be.clientTurnO = be.clientTurn;
        float s = be.clientSpeed;
        // (the OIIA spin plays at its own pace at first, up to 6x as fast at full speed)
        be.clientSpin += 0.05 * (s > 0.02F ? 0.8 + s * 5.2 : 0.0);
        be.clientTurn += s * 40.0; // degrees a tick: the plate
    }

    /** Client: the OIIA spin's time on this pad now (seconds of its animation). */
    public float spinSeconds(float partialTick) {
        return (float) (clientSpinO + (clientSpin - clientSpinO) * partialTick);
    }

    public float turn(float partialTick) {
        return (float) (clientTurnO + (clientTurn - clientTurnO) * partialTick);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("speed", speed);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putString("sky", sky);
        tag.putString("pass", pass);
        tag.putBoolean("occupied", occupied);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speed = tag.getFloat("speed");
        energy.set(tag.getInt("energy"));
        if (tag.contains("sky")) {
            sky = tag.getString("sky");
            pass = tag.getString("pass");
        }
        occupied = tag.getBoolean("occupied");
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
