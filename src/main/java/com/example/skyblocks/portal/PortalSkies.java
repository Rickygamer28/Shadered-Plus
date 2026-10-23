package com.example.skyblocks.portal;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Portals showing a Shadered sky instead of their own look (right-click a nether portal, end portal or end gateway with
 * a Shadered sky block; with an empty hand to undo). Kept per dimension next to the world: the portals stay vanilla
 * portals (they work and look as usual without the mod). Clients are told which portals have which sky
 * (PortalSkiesPayload) and draw them (client/PortalSkyRenderer).
 */
public final class PortalSkies extends SavedData {
    private static final String NAME = "skyblocks_portal_skies";
    /** Most portal blocks changed by one click (the biggest nether portal is 21 x 21 inside). */
    private static final int MAX_BLOCKS = 2048;

    private final Map<Long, SkyblockType> skies = new HashMap<>();

    public static PortalSkies get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PortalSkies::new, PortalSkies::load, null), NAME);
    }

    public static PortalSkies load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalSkies data = new PortalSkies();
        long[] positions = tag.getLongArray("positions");
        ListTag names = tag.getList("skies", Tag.TAG_STRING);
        for (int i = 0; i < positions.length && i < names.size(); i++) {
            SkyblockType sky = sky(names.getString(i));
            if (sky != null) {
                data.skies.put(positions[i], sky);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = new long[skies.size()];
        ListTag names = new ListTag();
        int i = 0;
        for (Map.Entry<Long, SkyblockType> e : skies.entrySet()) {
            positions[i++] = e.getKey();
            names.add(StringTag.valueOf(e.getValue().name()));
        }
        tag.put("positions", new LongArrayTag(positions));
        tag.put("skies", names);
        return tag;
    }

    /** The sky given to the portal block at {@code pos}, or null. */
    @Nullable
    public SkyblockType skyAt(BlockPos pos) {
        return skies.get(pos.asLong());
    }

    @Nullable
    public static SkyblockType sky(String name) {
        try {
            return SkyblockType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // a sky from an addon that isn't installed any more
        }
    }

    @Nullable
    public SkyblockType skyAt(BlockPos pos) {
        return skies.get(pos.asLong());
    }

    /** The kinds of portal that can show a sky: how they are drawn and what counts as the same portal. */
    public enum Kind {
        /** A flat upright sheet inside a frame (nether portals, and modded portals made like them). */
        SHEET,
        /** A flat floor of portal blocks (end portals). */
        FLOOR,
        /** A single block drawn as a cube (end gateways). */
        CUBE
    }

    /**
     * What kind of portal a block is, or null if it isn't one. Vanilla's portals, modded portals made from vanilla's
     * portal blocks (subclasses), and modded blocks named like a portal (not a portal frame).
     */
    @Nullable
    public static Kind kind(BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof NetherPortalBlock) {
            return Kind.SHEET;
        }
        if (block instanceof net.minecraft.world.level.block.EndPortalBlock) {
            return Kind.FLOOR;
        }
        if (block instanceof net.minecraft.world.level.block.EndGatewayBlock) {
            return Kind.CUBE;
        }
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (name.contains("portal") && !name.contains("frame")) {
            // standing on an axis like the nether portal: a sheet; otherwise lying flat (like the Twilight Forest's
            // portal pool): a floor, drawn at the top of its shape
            return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS)
                    ? Kind.SHEET : Kind.FLOOR;
        }
        return null;
    }

    /** Whether a block is one of the portals that can show a sky. */
    public static boolean isPortal(BlockState state) {
        return kind(state) != null;
    }

    /** A sheet portal's axis (the way its plane runs), X if it doesn't say. */
    public static Direction.Axis axis(BlockState state) {
        return state.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
    }

    /**
     * The whole portal the block at {@code pos} belongs to: the connected portal blocks of the same kind (a nether
     * portal's blocks facing the same way, an end portal's blocks side by side; an end gateway is one block).
     */
    public static List<BlockPos> wholePortal(ServerLevel level, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        List<BlockPos> found = new ArrayList<>();
        Kind kind = kind(start);
        if (kind == null || kind == Kind.CUBE) {
            found.add(pos.immutable());
            return found;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos.immutable());
        seen.add(pos.asLong());
        boolean nether = kind == Kind.SHEET;
        while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos at = queue.poll();
            found.add(at);
            for (Direction d : Direction.values()) {
                if (!nether && d.getAxis() == Direction.Axis.Y) {
                    continue; // an end portal is flat
                }
                BlockPos next = at.relative(d);
                if (seen.add(next.asLong()) && level.isLoaded(next) && same(start, level.getBlockState(next), nether)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static boolean same(BlockState start, BlockState other, boolean sheet) {
        if (!other.is(start.getBlock())) {
            return false;
        }
        return !sheet || axis(other) == axis(start);
    }

    /** Gives the blocks the sky (null: back to the portal's own look); returns whether anything changed. */
    public boolean set(ServerLevel level, List<BlockPos> blocks, @Nullable SkyblockType sky) {
        List<Long> changed = new ArrayList<>();
        for (BlockPos pos : blocks) {
            long key = pos.asLong();
            SkyblockType before = sky == null ? skies.remove(key) : skies.put(key, sky);
            if (before != sky) {
                changed.add(key);
            }
        }
        if (changed.isEmpty()) {
            return false;
        }
        setDirty();
        send(level, changed, null);
        return true;
    }

    /** Forgets portals that are gone (broken, or the portal went out), in loaded chunks. */
    public void prune(ServerLevel level) {
        List<Long> gone = new ArrayList<>();
        for (Long key : skies.keySet()) {
            BlockPos pos = BlockPos.of(key);
            if (level.isLoaded(pos) && !isPortal(level.getBlockState(pos))) {
                gone.add(key);
            }
        }
        if (!gone.isEmpty()) {
            gone.forEach(skies::remove);
            setDirty();
            send(level, gone, null);
        }
    }

    /** Tells clients about changed blocks (to everyone in the dimension), or everything (to one player). */
    private void send(ServerLevel level, List<Long> changed, @Nullable ServerPlayer only) {
        long[] positions = new long[changed.size()];
        ListTag names = new ListTag();
        for (int i = 0; i < positions.length; i++) {
            positions[i] = changed.get(i);
            SkyblockType sky = skies.get(changed.get(i));
            names.add(StringTag.valueOf(sky == null ? "" : sky.name()));
        }
        CompoundTag data = new CompoundTag();
        data.putString("dimension", level.dimension().location().toString());
        data.putBoolean("full", only != null);
        data.put("positions", new LongArrayTag(positions));
        data.put("skies", names);
        PortalSkiesPayload payload = new PortalSkiesPayload(data);
        if (only != null) {
            PacketDistributor.sendToPlayer(only, payload);
        } else {
            PacketDistributor.sendToPlayersInDimension(level, payload);
        }
    }

    /** Sends a player every portal sky of the dimension they are in (on joining, changing dimension, respawning). */
    public void sendAll(ServerLevel level, ServerPlayer player) {
        send(level, new ArrayList<>(skies.keySet()), player);
    }
}
