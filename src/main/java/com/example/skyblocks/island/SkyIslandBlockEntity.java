package com.example.skyblocks.island;

import com.example.skyblocks.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/** A placed Sky Island: holds the scanned island (from the item) and how wide to show it. */
public class SkyIslandBlockEntity extends BlockEntity {
    /** The miniature is always shown 1 block wide. */
    public static final int DISPLAY_WIDTH = 1;

    @Nullable
    private IslandData island;

    /** Client only: the decoded island, its baked model and mob figures (client/island/IslandView). */
    @Nullable
    public Object clientView;

    public SkyIslandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_ISLAND.get(), pos, state);
    }

    @Nullable
    public IslandData island() {
        return island;
    }

    public int displayWidth() {
        return DISPLAY_WIDTH;
    }

    /** Island Painting: how the island is turned, in steps of 45 degrees (0-7). */
    private int rotation;

    public int rotation() {
        return rotation;
    }

    void rotate() {
        rotation = (rotation + 1) % 8;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Island Painting: the Shadered sky block used as the room's background (empty = painted sky). */
    private ItemStack background = ItemStack.EMPTY;

    public ItemStack background() {
        return background;
    }

    void setBackground(ItemStack background) {
        this.background = background.copy();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The name the Sky Island item was given in an anvil (shown on a plaque), or null. */
    @Nullable
    private Component name;

    @Nullable
    public Component name() {
        return name;
    }

    /** Island Painting: puts an island in, with its item's name (or takes it out with null). */
    void setIsland(@Nullable IslandData island, @Nullable Component name) {
        this.island = island;
        this.name = island != null ? name : null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }


    // ---- mobs living on a placed Sky Island (pedestals; Island Paintings keep showing the scanned mobs) ----

    /**
     * Block size of the miniature: the same for every Sky Island (a 25-wide island fills the pedestal), so islands on
     * pedestals next to each other line up and their mobs can walk from one onto the other.
     */
    public static final float SCALE = DISPLAY_WIDTH / (float) IslandScan.MAX_SIZE;

    private final List<IslandMob> mobs = new ArrayList<>();
    /** Whether the mobs were taken from the island yet (done by the server the first time it ticks). */
    private boolean mobsReady;
    @Nullable
    private IslandGrid grid;
    @Nullable
    private IslandData gridFor;

    /** The mobs on this Sky Island now (in its island's block coordinates). */
    public List<IslandMob> mobs() {
        return mobs;
    }

    public boolean isPedestal() {
        return getBlockState().getBlock() instanceof SkyIslandBlock;
    }

    @Nullable
    public IslandGrid grid() {
        if (island == null) {
            return null;
        }
        if (gridFor != island) {
            gridFor = island;
            grid = IslandGrid.decode(island);
        }
        return grid;
    }

    /** Server: takes the island's scanned mobs as this Sky Island's living mobs (once, when first placed). */
    private void ensureMobs(Level level) {
        if (mobsReady || island == null || !isPedestal()) {
            return;
        }
        mobsReady = true;
        mobs.clear();
        ListTag entries = island.view().getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            CompoundTag saved = entry.getCompound("entity");
            boolean walker = false, slime = false, isMob = false;
            float body = 0;
            try {
                Entity entity = EntityType.create(saved.copy(), level).orElse(null);
                if (entity != null) {
                    // pets that were sitting (told to stay) stay where they are; so do flying and swimming mobs
                    boolean sitting = entity instanceof TamableAnimal pet && (pet.isInSittingPose() || pet.isOrderedToSit());
                    walker = entity instanceof Mob && !(entity instanceof FlyingMob) && entity.onGround() && !sitting;
                    slime = entity instanceof Slime;
                    isMob = entity instanceof Mob;
                    body = entity.getYRot();
                }
            } catch (RuntimeException ignored) {
                // an entity that can't be recreated just stays where it is
            }
            IslandMob mob = new IslandMob(UUID.randomUUID(), saved, walker, slime, entry.getDouble("x"), entry.getDouble("y"),
                    entry.getDouble("z"), body, level.random.nextLong());
            mob.isMob = isMob;
            mobs.add(mob);
        }
        setChanged();
        sendMobs(level);
    }

    /** Where a point of this island is in the world, on x / z. */
    private double worldX(double x) {
        return worldPosition.getX() + 0.5 + (x - grid.contentCenterX()) * SCALE;
    }

    private double worldZ(double z) {
        return worldPosition.getZ() + 0.5 + (z - grid.contentCenterZ()) * SCALE;
    }

    /** The Sky Island whose pedestal is under the world point (x, z) at this one's height, or null. */
    @Nullable
    private SkyIslandBlockEntity islandAt(Level level, double wx, double wz) {
        int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
        if (bx == worldPosition.getX() && bz == worldPosition.getZ()) {
            return this;
        }
        BlockPos at = new BlockPos(bx, worldPosition.getY(), bz);
        if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof SkyIslandBlockEntity other)
                || !other.isPedestal() || other.grid() == null) {
            return null;
        }
        return other;
    }

    /** The offset (island blocks) from this island's coordinates to another's: add it to go there. */
    private double[] offsetTo(SkyIslandBlockEntity other) {
        return new double[] {
                (worldPosition.getX() - other.worldPosition.getX()) / SCALE - grid.contentCenterX() + other.grid.contentCenterX(),
                other.grid.lowestLayer() - grid.lowestLayer(),
                (worldPosition.getZ() - other.worldPosition.getZ()) / SCALE - grid.contentCenterZ() + other.grid.contentCenterZ()};
    }

    /** This island and the Sky Islands next to it, as seen from this island's coordinates. */
    private IslandMob.Space space(Level level) {
        return new IslandMob.Space() {
            @Override
            public double floor(double x, double y, double z) {
                SkyIslandBlockEntity at = islandAt(level, worldX(x), worldZ(z));
                if (at == null) {
                    return Double.NaN;
                }
                if (at == SkyIslandBlockEntity.this) {
                    return grid.floorBelow(x, y, z);
                }
                double[] d = offsetTo(at);
                double floor = at.grid.floorBelow(x + d[0], y + d[1], z + d[2]);
                return Double.isNaN(floor) ? Double.NaN : floor - d[1];
            }

            @Override
            public boolean onIsland(double x, double z) {
                SkyIslandBlockEntity at = islandAt(level, worldX(x), worldZ(z));
                if (at == null) {
                    return false;
                }
                double lx = x, lz = z;
                if (at != SkyIslandBlockEntity.this) {
                    double[] d = offsetTo(at);
                    lx += d[0];
                    lz += d[2];
                }
                return lx >= 0 && lz >= 0 && lx <= at.grid.size && lz <= at.grid.size;
            }
        };
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SkyIslandBlockEntity be) {
        be.tickMobs(level);
    }

    /**
     * Both sides: moves the mobs one tick. The server also hands mobs that walked onto a neighbouring Sky Island over
     * to it, and tells clients whenever something was decided (they run the same steps in between).
     */
    private void tickMobs(Level level) {
        if (island == null || !isPedestal()) {
            return;
        }
        if (!level.isClientSide) {
            ensureMobs(level);
        }
        if (mobs.isEmpty() || grid() == null) {
            return;
        }
        IslandMob.Space space = space(level);
        boolean changed = false;
        for (IslandMob mob : mobs) {
            changed |= mob.tick(space);
        }
        if (level.isClientSide) {
            return; // only the server moves mobs between islands (clients get told)
        }
        for (Iterator<IslandMob> it = mobs.iterator(); it.hasNext(); ) {
            IslandMob mob = it.next();
            SkyIslandBlockEntity other = islandAt(level, worldX(mob.x), worldZ(mob.z));
            if (other == null || other == this) {
                continue;
            }
            other.ensureMobs(level);
            double[] d = offsetTo(other);
            mob.shift(d[0], d[1], d[2]);
            mob.entitySent = false; // new to the clients' copy of that island
            it.remove();
            other.mobs.add(mob);
            other.setChanged();
            other.sendMobs(level);
            changed = true;
        }
        if (changed) {
            setChanged();
            sendMobs(level);
        }
    }

    private void sendMobs(Level level) {
        if (level instanceof ServerLevel server) {
            com.example.skyblocks.SkyBlocksMod.NETWORK.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> server.getChunkAt(worldPosition)),
                    new IslandMobsPayload(worldPosition, mobsTag(true)));
        }
    }

    /** The mobs as NBT; for an update, saved entities only of mobs the clients don't know yet (keeps it small). */
    private CompoundTag mobsTag(boolean update) {
        ListTag list = new ListTag();
        for (IslandMob mob : mobs) {
            list.add(mob.save(!update || !mob.entitySent));
            if (update) {
                mob.entitySent = true;
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("mobs", list);
        return tag;
    }

    /** Client: the server's mobs (mobs already known keep their last position, for smooth movement). */
    public void applyMobs(CompoundTag tag) {
        Map<UUID, IslandMob> known = new HashMap<>();
        for (IslandMob mob : mobs) {
            known.put(mob.id, mob);
        }
        mobs.clear();
        ListTag list = tag.getList("mobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            IslandMob mine = known.get(entry.getUUID("id"));
            IslandMob server = IslandMob.load(entry, mine != null ? mine.entity : null);
            if (server.entity.isEmpty()) {
                continue; // a mob this client was never told about (shouldn't happen)
            }
            if (mine != null && Math.abs(mine.x - server.x) + Math.abs(mine.z - server.z) < 2.0) {
                mine.follow(server);
                mobs.add(mine);
            } else {
                mobs.add(server);
            }
        }
        mobsReady = true;
    }

    /** The island with the mobs that are on it now (for the item it drops: mobs that walked over go with it). */
    public IslandData islandWithMobs() {
        if (island == null || !mobsReady || !isPedestal()) {
            return island;
        }
        CompoundTag tag = island.tag();
        ListTag entities = new ListTag();
        int count = 0;
        for (IslandMob mob : mobs) {
            CompoundTag entry = new CompoundTag();
            entry.put("entity", mob.entity.copy());
            entry.putDouble("x", mob.x);
            entry.putDouble("y", mob.y);
            entry.putDouble("z", mob.z);
            entities.add(entry);
            if (mob.isMob) {
                count++;
            }
        }
        tag.put("entities", entities);
        tag.putInt("mobs", count);
        return new IslandData(tag);
    }

    /** Pedestal: the name of the item it was placed from (SkyIslandBlock.setPlacedBy). */
    void setName(@Nullable Component name) {
        this.name = name;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rotation = Math.floorMod(tag.getInt("rotation"), 8);
        background = tag.contains("background")
                ? ItemStack.of(tag.getCompound("background")) : ItemStack.EMPTY;
        name = tag.contains("CustomName") ? parseName(tag.getString("CustomName")) : null;
        IslandData loaded = tag.contains("island") ? new IslandData(tag.getCompound("island")) : null;
        if (loaded == null || !loaded.equals(island)) {
            island = loaded; // keep the same instance when nothing changed (the client renderer compares it)
        }
        if (tag.contains("live_mobs")) {
            mobsReady = true;
            CompoundTag mobTag = new CompoundTag();
            mobTag.put("mobs", tag.getList("live_mobs", Tag.TAG_COMPOUND));
            if (level != null && level.isClientSide) {
                applyMobs(mobTag);
            } else {
                mobs.clear();
                ListTag list = mobTag.getList("mobs", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    mobs.add(IslandMob.load(list.getCompound(i), null));
                }
            }
        } else {
            mobsReady = false; // (again) taken from the island on the next server tick
            mobs.clear();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (island != null) {
            tag.put("island", island.tag());
        }
        // Always write something: clients ignore an empty update, so without this, taking the island out of a
        // painting would never reach them and it would still be shown.
        tag.putBoolean("has_island", island != null);
        tag.putInt("rotation", rotation);
        if (mobsReady && isPedestalSafe()) {
            tag.put("live_mobs", mobsTag(false).getList("mobs", Tag.TAG_COMPOUND));
        }
        if (name != null) {
            tag.putString("CustomName", Component.Serializer.toJson(name));
        }
        if (!background.isEmpty()) {
            tag.put("background", background.save(new CompoundTag()));
        }
    }

    @Nullable
    private static Component parseName(String json) {
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What the renderer draws: the island above the pedestal, or a (joined) painting and the room behind it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof IslandPaintingBlock && level != null) {
            // a joined painting spans width x height blocks; the room goes into the wall behind it
            int w = IslandPaintingBlock.width(level, pos, state);
            int h = IslandPaintingBlock.height(level, pos, state);
            BlockPos far = pos.relative(state.getValue(IslandPaintingBlock.FACING).getClockWise(), w - 1).above(h - 1);
            return new net.minecraft.world.phys.AABB(pos).minmax(new net.minecraft.world.phys.AABB(far)).inflate(Math.max(w, h) + 1.0);
        }
        double half = displayWidth() / 2.0 + 0.5;
        double top = 0.35 + 1.0 + (island != null && island.size() > 0 ? displayWidth() * (double) island.height() / island.size() : 1.0);
        return new net.minecraft.world.phys.AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + top, pos.getZ() + 0.5 + half);
    }

    /** isPedestal() when the block state may not be set up yet (saving early). */
    private boolean isPedestalSafe() {
        try {
            return isPedestal();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Client: free the baked model when the block goes away or its chunk unloads. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide && clientView instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
            clientView = null;
        }
    }
}
