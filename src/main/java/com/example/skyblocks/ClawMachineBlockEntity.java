package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A claw machine: the plushies in it (each where it lies on the floor - won ones gone, the machine restocked when it
 * runs low) and who has paid for a go; on the client, where its claw is while someone plays.
 */
public class ClawMachineBlockEntity extends BlockEntity {
    /** The inside, in pixels of the lower block (front facing north): across, front to back. */
    public static final float X0 = 2.5F, X1 = 13.5F, Z0 = 2.5F, Z1 = 13.5F, FLOOR = 12.0F, TOP = 27.6F;
    public static final float CHUTE_X0 = 10.0F, CHUTE_Z1 = 6.0F; // (the chute: the front corner on the viewer's left)
    /** How big a plushie is drawn in the case; how far above the floor one lying on others is; how tall one is. */
    public static final float PRIZE_SCALE = 0.36F, STACKED = 2.0F, PRIZE_H = 3.4F;
    private static final int STOCK = 14, LOW = 6;

    /** A plushie in the case: its id, which plushie, where (pixels), lying on top of others or not, turned how. */
    public record Prize(int id, int kind, float x, float z, boolean top, float turn) {}

    private final List<Prize> prizes = new ArrayList<>();
    private int nextId;
    private final Map<UUID, Integer> credits = new HashMap<>();
    /** Client only: someone's claw now (0-1 across from the viewer's left, 0-1 front to back, 0-1 down), open, holding (a prize id). */
    public float liveX, liveZ, liveDepth;
    public boolean liveOpen = true;
    public int liveHolding = -1;
    public long liveAt = Long.MIN_VALUE / 2;

    public ClawMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAW_MACHINE.get(), pos, state);
    }

    public List<Prize> prizes() {
        return prizes;
    }

    @Nullable
    public Prize prize(int id) {
        for (Prize p : prizes) {
            if (p.id() == id) return p;
        }
        return null;
    }

    /** Fills the case up again (new plushies scattered on the floor, some on top of others). */
    public void restock(RandomSource r) {
        int kinds = ModBlocks.PLUSHIE_NAMES.length;
        int tries = 0;
        while (prizes.size() < STOCK && tries++ < 400) {
            float x = X0 + 2.0F + r.nextFloat() * (X1 - X0 - 4.0F), z = Z0 + 2.0F + r.nextFloat() * (Z1 - Z0 - 4.0F);
            if (x > CHUTE_X0 - 2.0F && z < CHUTE_Z1 + 2.0F) continue; // (not on the chute)
            boolean top = false, clash = false;
            for (Prize p : prizes) {
                float d = (p.x() - x) * (p.x() - x) + (p.z() - z) * (p.z() - z);
                if (d < 10.0F) {
                    if (p.top()) clash = true;
                    else top = true;
                }
            }
            if (clash) continue;
            prizes.add(new Prize(nextId++, r.nextInt(kinds), x, z, top, r.nextFloat() * 360.0F));
        }
        changed();
    }

    /** A prize won: gone from the case (restocked if it's running low). Its kind, or -1 if it isn't there. */
    public int take(int id, RandomSource r) {
        Prize p = prize(id);
        if (p == null) return -1;
        prizes.remove(p);
        if (prizes.size() < LOW) {
            restock(r);
        } else {
            changed();
        }
        return p.kind();
    }

    /** How high the top of a prize is (pixels). */
    public static float topOf(Prize p) {
        return FLOOR + (p.top() ? STACKED : 0.0F) + PRIZE_H;
    }

    public void addCredit(Player player) {
        credits.merge(player.getUUID(), 1, Integer::sum);
        setChanged();
    }

    /** Uses up one paid go; false if there's none (creative players always play). */
    public boolean useCredit(Player player) {
        if (player.getAbilities().instabuild) return true;
        Integer n = credits.get(player.getUUID());
        if (n == null || n <= 0) return false;
        if (n == 1) credits.remove(player.getUUID());
        else credits.put(player.getUUID(), n - 1);
        setChanged();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && prizes.isEmpty()) {
            restock(level.random);
        }
    }

    private void savePrizes(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Prize p : prizes) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", p.id());
            t.putInt("kind", p.kind());
            t.putFloat("x", p.x());
            t.putFloat("z", p.z());
            t.putBoolean("top", p.top());
            t.putFloat("turn", p.turn());
            list.add(t);
        }
        tag.put("prizes", list);
        tag.putInt("next", nextId);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        savePrizes(tag);
        CompoundTag c = new CompoundTag();
        credits.forEach((id, n) -> c.putInt(id.toString(), n));
        tag.put("credits", c);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        prizes.clear();
        for (Tag e : tag.getList("prizes", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            prizes.add(new Prize(t.getInt("id"), t.getInt("kind"), t.getFloat("x"), t.getFloat("z"), t.getBoolean("top"), t.getFloat("turn")));
        }
        nextId = tag.getInt("next");
        if (tag.contains("credits")) {
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
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        savePrizes(tag); // (the prizes - not who has paid)
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
