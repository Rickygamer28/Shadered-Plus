package com.example.skyblocks.island;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Scans the area around an Island Scanner into an IslandData snapshot. The area is size x size blocks centred on
 * the scanner, from depth blocks below the block the scanner stands on up to above blocks from the scanner's layer.
 * Everything from the ground layer up is kept as it is; below it, the island narrows into a rough rounded point
 * (a floating-island underside). Mobs, armor stands, item frames and paintings in the area are saved too.
 */
public final class IslandScan {
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 25;
    public static final int MAX_ABOVE = 24;
    public static final int MAX_DEPTH = 24;
    public static final int MAX_ENTITIES = 64;
    private static final int MAX_ENTITY_BYTES = 16 * 1024;
    private static final int MAX_BLOCK_ENTITY_BYTES = 16 * 1024;
    private static final int MAX_ALL_BLOCK_ENTITY_BYTES = 384 * 1024;
    private static final int MAX_ALL_ENTITY_BYTES = 256 * 1024;
    /** Saved entity data a figure doesn't need to look right (inventories, AI memory, trades...). */
    private static final String[] HEAVY_ENTITY_DATA = {"Passengers", "UUID", "Items", "Inventory", "Brain", "Offers",
            "Gossips", "Attributes", "ActiveEffects", "active_effects", "attributes", "Leash", "leash", "ArmorDropChances",
            "HandDropChances", "body_armor_drop_chance", "LastHurtByPlayer", "AngryAt", "HurtByTimestamp", "Xp",
            "LastRestock", "LastGossipDecay", "RestocksToday", "FoodLevel", "SaddleItem", "DecorItem"};

    public static int clampSize(int size) {
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        return size % 2 == 0 ? size - 1 : size; // odd, so the scanner is in the middle
    }

    public static int clampAbove(int above) {
        return Math.max(1, Math.min(MAX_ABOVE, above));
    }

    public static int clampDepth(int depth) {
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    /** The scanned area's lowest north-west corner. */
    public static BlockPos origin(BlockPos scanner, int size, int depth) {
        int r = size / 2;
        return scanner.offset(-r, -1 - depth, -r);
    }

    public static int height(int above, int depth) {
        return depth + 1 + above;
    }

    public static IslandData scan(ServerLevel level, BlockPos scanner, int size, int above, int depth) {
        size = clampSize(size);
        above = clampAbove(above);
        depth = clampDepth(depth);
        int height = height(above, depth);
        int ground = depth; // island layer of the block the scanner stands on
        BlockPos origin = origin(scanner, size, depth);

        ListTag blockEntities = new ListTag();
        ListTag portalSkies = new ListTag(); // portals given a Shadered sky: which one (the miniature shows it too)
        com.example.skyblocks.portal.PortalSkies skies = com.example.skyblocks.portal.PortalSkies.get(level);
        long blockEntityBytes = 0;
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        palette.put(Blocks.AIR.defaultBlockState(), 0);
        int[] blocks = new int[size * height * size];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (p.equals(scanner) || (y < ground && !keepUnderground(x, z, ground - y, depth, size))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(p);
                    if (state.isAir()) {
                        continue;
                    }
                    Integer index = palette.get(state);
                    if (index == null) {
                        index = palette.size();
                        palette.put(state, index);
                    }
                    blocks[(y * size + z) * size + x] = index;
                    if (com.example.skyblocks.portal.PortalSkies.isPortal(state)) {
                        var sky = skies.skyAt(p);
                        if (sky != null) {
                            CompoundTag entry = new CompoundTag();
                            entry.putInt("x", x);
                            entry.putInt("y", y);
                            entry.putInt("z", z);
                            entry.putString("sky", sky.name());
                            portalSkies.add(entry);
                        }
                    }

                    // Chests, beds, signs, banners, heads...: keep their data so the miniature can draw them.
                    if (state.hasBlockEntity() && blockEntityBytes < MAX_ALL_BLOCK_ENTITY_BYTES) {
                        CompoundTag saved = saveBlockEntity(level, p);
                        if (saved != null && blockEntityBytes + saved.sizeInBytes() <= MAX_ALL_BLOCK_ENTITY_BYTES) {
                            blockEntityBytes += saved.sizeInBytes();
                            CompoundTag entry = new CompoundTag();
                            entry.putInt("x", x);
                            entry.putInt("y", y);
                            entry.putInt("z", z);
                            entry.put("data", saved);
                            blockEntities.add(entry);
                        }
                    }
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt("size", size);
        tag.putInt("height", height);
        tag.putInt("ground", ground);
        ListTag paletteTag = new ListTag();
        for (BlockState state : palette.keySet()) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", paletteTag);
        if (palette.size() <= 256) {
            byte[] bytes = new byte[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                bytes[i] = (byte) blocks[i];
            }
            tag.putByteArray("blocks", bytes);
        } else {
            tag.putIntArray("blocks", blocks);
        }

        // Biomes (for grass, leaves and water colours), one per column, taken at the ground layer.
        Map<String, Integer> biomes = new LinkedHashMap<>();
        byte[] biomeMap = new byte[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                Holder<Biome> biome = level.getBiome(origin.offset(x, ground, z));
                String id = biome.unwrapKey().orElse(Biomes.PLAINS).location().toString();
                Integer index = biomes.get(id);
                if (index == null) {
                    index = biomes.size();
                    if (index > 255) {
                        index = 0;
                    } else {
                        biomes.put(id, index);
                    }
                }
                biomeMap[z * size + x] = (byte) (int) index;
            }
        }
        ListTag biomeList = new ListTag();
        for (String id : biomes.keySet()) {
            biomeList.add(StringTag.valueOf(id));
        }
        tag.put("biomes", biomeList);
        tag.put("block_entities", blockEntities);
        if (!portalSkies.isEmpty()) {
            tag.put("portal_skies", portalSkies);
        }
        tag.putByteArray("biome_map", biomeMap);

        // Mobs and decorations.
        AABB area = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size, origin.getY() + height, origin.getZ() + size);
        List<Entity> found = level.getEntities((Entity) null, area,
                e -> e instanceof Mob || e instanceof ArmorStand || e instanceof HangingEntity);
        ListTag entities = new ListTag();
        int mobs = 0;
        long total = 0;
        for (Entity entity : found) {
            if (entities.size() >= MAX_ENTITIES) {
                break;
            }
            CompoundTag saved = new CompoundTag();
            if (entity.isPassenger() || !entity.save(saved)) {
                continue;
            }
            for (String key : HEAVY_ENTITY_DATA) {
                saved.remove(key);
            }
            if (saved.sizeInBytes() > MAX_ENTITY_BYTES || total + saved.sizeInBytes() > MAX_ALL_ENTITY_BYTES) {
                continue; // keep the item small enough to sync
            }
            total += saved.sizeInBytes();
            CompoundTag entry = new CompoundTag();
            entry.put("entity", saved);
            entry.putDouble("x", entity.getX() - origin.getX());
            entry.putDouble("y", entity.getY() - origin.getY());
            entry.putDouble("z", entity.getZ() - origin.getZ());
            entities.add(entry);
            if (entity instanceof Mob) {
                mobs++;
            }
        }
        tag.put("entities", entities);
        tag.putInt("mobs", mobs);
        tag.putString("dimension", level.dimension().location().toString());
        // where the scanner stood (for travelling there with an ender pearl) and when (day number)
        tag.putInt("origin_x", scanner.getX());
        tag.putInt("origin_y", scanner.getY());
        tag.putInt("origin_z", scanner.getZ());
        tag.putLong("scanned_day", level.getDayTime() / 24000L);
        return new IslandData(tag);
    }

    /** A block entity's data for drawing it, or null (none, too big, or a Sky Island / scanner of our own). */
    @org.jetbrains.annotations.Nullable
    private static CompoundTag saveBlockEntity(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof SkyIslandBlockEntity || be instanceof IslandScannerBlockEntity) {
            return null; // no islands inside islands
        }
        // Only what clients normally get for drawing it (sign text, banner patterns, head owner, lectern book...),
        // never server-only data such as loot seeds or command block commands.
        CompoundTag saved;
        try {
            saved = be.getUpdateTag(level.registryAccess());
        } catch (RuntimeException e) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.world.level.block.entity.BlockEntityType.getKey(be.getType());
        if (id == null) {
            return null;
        }
        saved.putString("id", id.toString());
        return saved.sizeInBytes() > MAX_BLOCK_ENTITY_BYTES ? null : saved;
    }

    /**
     * The floating-island underside: k layers below the ground layer, keep a block only within a radius that shrinks
     * towards the bottom, with some roughness.
     */
    public static boolean keepUnderground(int x, int z, int k, int depth, int size) {
        double c = (size - 1) / 2.0;
        double dx = x - c, dz = z - c;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double f = k / (depth + 1.0);
        double radius = size / 2.0 * Math.pow(1 - f, 0.75) + jitter(x, z, k) * 1.2;
        return dist <= radius;
    }

    /** Deterministic noise in -0.5..0.5. */
    private static double jitter(int x, int z, int k) {
        long h = x * 73428767L ^ z * 912931L ^ k * 4382113L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return ((h & 0xFFFF) / 65535.0) - 0.5;
    }

    private IslandScan() {}
}
