package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A claw machine: who has paid for a go (and how many); on the client, where its claw is while someone plays. */
public class ClawMachineBlockEntity extends BlockEntity {
    private final Map<UUID, Integer> credits = new HashMap<>();
    /** Client only: the claw now (0-1 across, 0-1 front to back, 0-1 down), open, holding (a plushie, -1 none). */
    public float liveX, liveZ, liveDepth;
    public boolean liveOpen = true;
    public int liveHolding = -1;
    public long liveAt = Long.MIN_VALUE / 2;

    public ClawMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAW_MACHINE.get(), pos, state);
    }

    public void addCredit(Player player) {
        credits.merge(player.getUUID(), 1, Integer::sum);
        setChanged();
    }

    /** Uses up one paid go; false if there's none (creative players always play). */
    public boolean useCredit(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Integer n = credits.get(player.getUUID());
        if (n == null || n <= 0) {
            return false;
        }
        if (n == 1) {
            credits.remove(player.getUUID());
        } else {
            credits.put(player.getUUID(), n - 1);
        }
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag c = new CompoundTag();
        credits.forEach((id, n) -> c.putInt(id.toString(), n));
        tag.put("credits", c);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        credits.clear();
        CompoundTag c = tag.getCompound("credits");
        for (String k : c.getAllKeys()) {
            try {
                credits.put(UUID.fromString(k), c.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // (not a player's id)
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag(); // (nothing the clients need)
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return null;
    }
}
