package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandData;
import com.example.skyblocks.island.IslandMob;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.example.skyblocks.island.IslandScan;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * A decoded Sky Island on the client. It is also a tiny stand-alone "level" (BlockAndTintGetter) holding only the
 * island's blocks, so Minecraft's own block and fluid renderers can draw it with correct face culling, ambient
 * occlusion, biome colours and light (light is worked out here once: sunlight from above and light from torches,
 * lava, etc. inside the island). Coordinates are island coordinates (0..size-1, 0..height-1).
 */
public final class IslandView implements BlockAndTintGetter, AutoCloseable {
    /** The scanned island this shows (null for a scanner's live preview). */
    @Nullable
    final IslandData source;
    final int size;
    final int height;
    private final BlockState[] states;
    private final Biome[] columnBiomes;
    private final byte[] skyLight;
    private final byte[] blockLight;
    private final LevelLightEngine lightEngine;
    final List<IslandFigure> figures = new ArrayList<>();
    /** Chests, beds, signs, banners, heads...: client-only copies drawn by their normal renderers. */
    private final List<IslandBlockEntity> blockEntities = new ArrayList<>();
    /**
     * Where the copied block entities "are" in the real world: far away in unloaded chunks, so renderers that look
     * at neighbouring blocks (double chests, beds) find nothing there and use the block entity's own block state.
     */
    static final int FAR = 29_000_000;
    private final RandomSource random = RandomSource.create();
    @Nullable
    private IslandMesh mesh;
    /** Portals inside given a Shadered sky (island coordinates -> sky). */
    final java.util.Map<BlockPos, com.noodlegamer76.shadered.client.util.SkyblockType> portalSkies = new java.util.HashMap<>();
    /** The infinity mirrors and cubes inside (worked out the first time it's needed). */
    @Nullable
    private com.example.skyblocks.client.InfinitySet infinity;
    private com.example.skyblocks.client.AquariumRenderer.Island aquariums;
    private com.example.skyblocks.client.DanceFloorRenderer.Island danceFloors;
    /** Resource reload count the mesh was baked for (textures move on the atlas after a reload). */
    private int meshGeneration = -1;
    private long lastTick = -1;

    /** Views alive on this client, closed when the client's world unloads (see IslandClientEvents). */
    static final java.util.Set<IslandView> LIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** Bumped on every resource reload (IslandClientEvents). */
    static int generation;

    private IslandView(@Nullable IslandData source, int size, int height, BlockState[] states, Biome[] columnBiomes) {
        this.source = source;
        this.size = size;
        this.height = height;
        this.states = states;
        this.columnBiomes = columnBiomes;
        this.skyLight = new byte[states.length];
        this.blockLight = new byte[states.length];
        this.lightEngine = new LevelLightEngine(new LightChunkGetter() {
            @Nullable
            @Override
            public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
                return null;
            }

            @Override
            public net.minecraft.world.level.BlockGetter getLevel() {
                return IslandView.this;
            }
        }, false, false);
        computeLight();
    }

    /** Decodes an island; its mob figures are placed (for lighting and name tags) at the given world position. */
    @Nullable
    public static IslandView decode(IslandData data, Level level, BlockPos at) {
        CompoundTag tag = data.view();
        int size = tag.getInt("size");
        int height = tag.getInt("height");
        if (size <= 0 || height <= 0 || size > 64 || height > 128) {
            return null;
        }
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[Math.max(1, paletteTag.size())];
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }
        int count = size * height * size;
        BlockState[] states = new BlockState[count];
        int[] indices;
        if (tag.getTagType("blocks") == Tag.TAG_BYTE_ARRAY) {
            byte[] bytes = tag.getByteArray("blocks");
            indices = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                indices[i] = bytes[i] & 0xFF;
            }
        } else {
            indices = tag.getIntArray("blocks");
        }
        for (int i = 0; i < count; i++) {
            int index = i < indices.length ? indices[i] : 0;
            states[i] = index >= 0 && index < palette.length ? palette[index] : palette[0];
        }

        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome fallback = biomeRegistry.get(Biomes.PLAINS);
        if (fallback == null) {
            fallback = biomeRegistry.stream().findFirst().orElse(null);
        }
        ListTag biomeIds = tag.getList("biomes", Tag.TAG_STRING);
        Biome[] biomePalette = new Biome[Math.max(1, biomeIds.size())];
        for (int i = 0; i < biomePalette.length; i++) {
            ResourceLocation id = i < biomeIds.size() ? ResourceLocation.tryParse(biomeIds.getString(i)) : null;
            Biome biome = id != null ? biomeRegistry.get(id) : null;
            biomePalette[i] = biome != null ? biome : fallback;
        }
        byte[] biomeMap = tag.getByteArray("biome_map");
        Biome[] columns = new Biome[size * size];
        for (int i = 0; i < columns.length; i++) {
            int index = i < biomeMap.length ? biomeMap[i] & 0xFF : 0;
            columns[i] = index < biomePalette.length ? biomePalette[index] : fallback;
        }

        IslandView view = new IslandView(data, size, height, states, columns);

        ListTag savedSkies = tag.getList("portal_skies", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedSkies.size(); i++) {
            CompoundTag entry = savedSkies.getCompound(i);
            var sky = com.example.skyblocks.portal.PortalSkies.sky(entry.getString("sky"));
            if (sky != null) {
                view.portalSkies.put(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")), sky);
            }
        }

        ListTag savedBlockEntities = tag.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedBlockEntities.size(); i++) {
            CompoundTag entry = savedBlockEntities.getCompound(i);
            int x = entry.getInt("x"), y = entry.getInt("y"), z = entry.getInt("z");
            BlockState state = view.stateAt(x, y, z);
            if (!state.hasBlockEntity()) {
                continue;
            }
            try {
                // Shulker boxes: the copy faces up and IslandBlockEntity turns it, whichever way the renderer reads facing.
                Direction turn = null;
                BlockState copyState = state;
                if (state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                        && state.hasProperty(net.minecraft.world.level.block.ShulkerBoxBlock.FACING)) {
                    turn = state.getValue(net.minecraft.world.level.block.ShulkerBoxBlock.FACING);
                    copyState = state.setValue(net.minecraft.world.level.block.ShulkerBoxBlock.FACING, Direction.UP);
                }
                BlockEntity be = BlockEntity.loadStatic(new BlockPos(FAR + x, y, FAR + z), copyState, entry.getCompound("data"),
                        level.registryAccess());
                if (be != null) {
                    be.setLevel(level);
                    view.blockEntities.add(new IslandBlockEntity(be, x, y, z, turn));
                }
            } catch (RuntimeException ignored) {
                // a block entity that can't be recreated on the client is left out
            }
        }

        ListTag entities = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entry = entities.getCompound(i);
            try {
                CompoundTag saved = entry.getCompound("entity").copy();
                saved.putShort("Fire", (short) -1); // not burning forever
                EntityType.create(saved, level).ifPresent(entity -> {
                    entity.setPos(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                    entity.setOldPosAndRot();
                    view.figures.add(new IslandFigure(entity, entry.getDouble("x"), entry.getDouble("y"),
                            entry.getDouble("z"), view.random, view));
                });
            } catch (RuntimeException ignored) {
                // an entity that can't be recreated on the client is left out
            }
        }
        view.figureLevel = level;
        view.figureAt = at;
        LIVE.add(view);
        return view;
    }

    /**
     * The Island Scanner's live preview: what a scan would give right now, read from the client's own copy of the
     * world (blocks only; same floating-island shape as a real scan).
     */
    public static IslandView preview(Level level, BlockPos scanner, int size, int above, int depth) {
        size = IslandScan.clampSize(size);
        above = IslandScan.clampAbove(above);
        depth = IslandScan.clampDepth(depth);
        int height = IslandScan.height(above, depth);
        int ground = depth;
        BlockPos origin = IslandScan.origin(scanner, size, depth);
        BlockState[] states = new BlockState[size * height * size];
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int i = (y * size + z) * size + x;
                    p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    boolean keep = !p.equals(scanner)
                            && (y >= ground || IslandScan.keepUnderground(x, z, ground - y, depth, size));
                    states[i] = keep ? level.getBlockState(p) : air;
                }
            }
        }
        Biome[] columns = new Biome[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                columns[z * size + x] = level.getBiome(origin.offset(x, ground, z)).value();
            }
        }
        IslandView view = new IslandView(null, size, height, states, columns);
        // Blocks drawn by block entities (Shadered's sky blocks, chests, signs, banners...): draw the real ones,
        // at their place in the miniature.
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    BlockState state = states[(y * size + z) * size + x];
                    if (com.example.skyblocks.portal.PortalSkies.isPortal(state)) {
                        var sky = com.example.skyblocks.client.PortalSkyRenderer.skyAt(level, origin.offset(x, y, z));
                        if (sky != null) {
                            view.portalSkies.put(new BlockPos(x, y, z), sky);
                        }
                    }
                    if (!state.hasBlockEntity()) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(origin.offset(x, y, z));
                    if (be != null && !(be instanceof com.example.skyblocks.island.SkyIslandBlockEntity)
                            && !(be instanceof com.example.skyblocks.island.IslandScannerBlockEntity)) {
                        view.blockEntities.add(new IslandBlockEntity(be, x, y, z, null));
                    }
                }
            }
        }
        LIVE.add(view);
        return view;
    }

    /**
     * A cheap fingerprint of the blocks a preview of this area would show (just reads them): the preview is only
     * rebuilt (light, block models, upload) when this changes.
     */
    public static long previewHash(Level level, BlockPos scanner, int size, int above, int depth) {
        size = IslandScan.clampSize(size);
        above = IslandScan.clampAbove(above);
        depth = IslandScan.clampDepth(depth);
        int height = IslandScan.height(above, depth);
        BlockPos origin = IslandScan.origin(scanner, size, depth);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long hash = 1125899906842597L;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    hash = 31 * hash + System.identityHashCode(level.getBlockState(p));
                }
            }
        }
        return hash;
    }

    // ---- island access ----

    boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < size && y < height && z < size;
    }

    private int index(int x, int y, int z) {
        return (y * size + z) * size + x;
    }

    /**
     * The island's blocks where its copied block entities think they are (far away in the real world): so an Island
     * Painting inside the island finds the paintings it's joined to.
     */
    net.minecraft.world.level.BlockGetter copies() {
        IslandView self = this;
        return new net.minecraft.world.level.BlockGetter() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return self.stateAt(pos.getX() - FAR, pos.getY(), pos.getZ() - FAR);
            }

            @Override
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getHeight() {
                return 1024;
            }

            @Override
            public int getMinBuildHeight() {
                return -512;
            }
        };
    }

    /** The infinity mirrors and cubes inside, ready to draw. */
    public com.example.skyblocks.client.InfinitySet infinity() {
        if (infinity == null) {
            infinity = com.example.skyblocks.client.InfinityMirrorRenderer.islandSet(this, size, height, size);
        }
        return infinity;
    }

    /** The aquariums inside, ready to draw. */
    public com.example.skyblocks.client.AquariumRenderer.Island aquariums() {
        if (aquariums == null) {
            aquariums = com.example.skyblocks.client.AquariumRenderer.islandSet(this, size, height, size);
        }
        return aquariums;
    }

    /** The dance floors inside, ready to draw. */
    public com.example.skyblocks.client.DanceFloorRenderer.Island danceFloors() {
        if (danceFloors == null) {
            danceFloors = com.example.skyblocks.client.DanceFloorRenderer.islandSet(this, size, height, size);
        }
        return danceFloors;
    }

    BlockState stateAt(int x, int y, int z) {
        return inside(x, y, z) ? states[index(x, y, z)] : Blocks.AIR.defaultBlockState();
    }

    /** Height a mob standing near (x, fromY, z) stands at, or NaN if there is no floor within reach. */
    double floorBelow(double x, double fromY, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        int top = (int) Math.floor(fromY + 1.2);
        for (int y = Math.min(top, height - 1); y >= Math.max(0, (int) Math.floor(fromY) - 3); y--) {
            if (stateAt(ix, y, iz).blocksMotion()) {
                return y + 1;
            }
        }
        return Double.NaN;
    }

    /** Bounds of the blocks actually there (computed once): min/max x, y, z; whole island if it is empty. */
    private int[] bounds;

    private int[] bounds() {
        if (bounds == null) {
            int minX = size, minY = height, minZ = size, maxX = -1, maxY = -1, maxZ = -1;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        if (!states[index(x, y, z)].isAir()) {
                            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                        }
                    }
                }
            }
            bounds = maxX < 0 ? new int[] {0, 0, 0, size - 1, height - 1, size - 1}
                    : new int[] {minX, minY, minZ, maxX, maxY, maxZ};
        }
        return bounds;
    }

    int lowestLayer() {
        return bounds()[1];
    }

    int highestLayer() {
        return bounds()[4];
    }

    /** Width (x), height and depth (z) of the blocks actually there, and their centre on x / z. */
    int contentWidth() {
        return bounds()[3] - bounds()[0] + 1;
    }

    int contentHeight() {
        return bounds()[4] - bounds()[1] + 1;
    }

    int contentDepth() {
        return bounds()[5] - bounds()[2] + 1;
    }

    float contentCenterX() {
        return (bounds()[0] + bounds()[3] + 1) / 2.0F;
    }

    float contentCenterZ() {
        return (bounds()[2] + bounds()[5] + 1) / 2.0F;
    }

    IslandMesh mesh() {
        if (mesh == null || meshGeneration != generation) {
            if (mesh != null) {
                mesh.close();
            }
            mesh = IslandMesh.build(this);
            meshGeneration = generation;
        }
        return mesh;
    }

    // ---- mobs ----

    /** Advances the mob figures to the given game time (at most a few ticks at once). */
    void tickTo(long time) {
        if (lastTick < 0 || time < lastTick) {
            lastTick = time;
            return;
        }
        int ticks = (int) Math.min(5, time - lastTick);
        lastTick = time;
        for (int t = 0; t < ticks; t++) {
            for (IslandFigure figure : figures) {
                figure.tick(this, random);
            }
        }
    }

    /** Packed light (block, sky) at a point of the island, from the light worked out for the copy. */
    int lightAt(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return net.minecraft.client.renderer.LightTexture.pack(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos));
    }

    void renderBlockEntities(PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        renderBlockEntities(poseStack, buffer, partialTick, null);
    }

    /** Draws the copied block entities ({@code only}: just those it accepts; null: all). */
    void renderBlockEntities(PoseStack poseStack, MultiBufferSource buffer, float partialTick,
                             @Nullable java.util.function.Predicate<BlockEntity> only) {
        if (blockEntities.isEmpty()) {
            return;
        }
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        EntityRenderDispatcher entities = Minecraft.getInstance().getEntityRenderDispatcher();
        entities.setRenderShadow(false); // e.g. a spawner's mob would cast its shadow on the real world
        boolean wasInIsland = IslandRendering.inIsland;
        IslandView wasIsland = IslandRendering.island;
        IslandRendering.inIsland = true;
        IslandRendering.island = this;
        try {
            blockEntities.removeIf(be -> (only == null || only.test(be.blockEntity()))
                    && !be.render(this, dispatcher, poseStack, buffer, partialTick));
        } finally {
            IslandRendering.inIsland = wasInIsland;
            IslandRendering.island = wasIsland;
            entities.setRenderShadow(true);
        }
    }

    void renderFigures(PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        renderFigures(poseStack, buffer, partialTick, 0.0, Double.POSITIVE_INFINITY);
    }

    /**
     * Draws the mob figures; with a finite {@code reach}, only those that would still be more than a couple of pixels
     * big: a mob is skipped when it is farther from the camera than reach x its size (blocks).
     */
    void renderFigures(PoseStack poseStack, MultiBufferSource buffer, float partialTick, double distance, double reach) {
        if (figures.isEmpty()) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false); // their shadows would be cast on the real world around the block
        try {
            figures.removeIf(figure -> distance <= reach * figureSize(figure.entity)
                    && !figure.render(this, dispatcher, poseStack, buffer, partialTick));
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    private static double figureSize(net.minecraft.world.entity.Entity entity) {
        return Math.max(0.5, Math.max(entity.getBbWidth(), entity.getBbHeight()));
    }

    private double biggestFigure = -1;

    // ---- the mobs living on a placed Sky Island (pedestal): the block entity moves them, these figures show them ----

    private Level figureLevel;
    private BlockPos figureAt;
    private final Map<UUID, IslandFigure> pedestal = new LinkedHashMap<>();
    private final Set<UUID> brokenFigures = new HashSet<>();
    private long pedestalTick = -1;

    /** Makes figures for mobs that arrived on the island and drops those of mobs that left. */
    private void syncPedestal(List<IslandMob> mobs) {
        if (pedestal.size() == mobs.size()) {
            boolean same = true;
            for (IslandMob mob : mobs) {
                if (!pedestal.containsKey(mob.id)) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        Map<UUID, IslandFigure> old = new LinkedHashMap<>(pedestal);
        pedestal.clear();
        for (IslandMob mob : mobs) {
            IslandFigure figure = old.get(mob.id);
            if (figure == null && !brokenFigures.contains(mob.id)) {
                figure = newFigure(mob);
            }
            if (figure != null) {
                pedestal.put(mob.id, figure);
            }
        }
    }

    @Nullable
    private IslandFigure newFigure(IslandMob mob) {
        try {
            CompoundTag saved = mob.entity.copy();
            saved.putShort("Fire", (short) -1); // not burning forever
            var entity = EntityType.create(saved, figureLevel).orElse(null);
            if (entity == null) {
                brokenFigures.add(mob.id);
                return null;
            }
            entity.setPos(figureAt.getX() + 0.5, figureAt.getY() + 0.5, figureAt.getZ() + 0.5);
            entity.setOldPosAndRot();
            entity.setYRot(mob.body);
            IslandFigure figure = new IslandFigure(entity, mob.x, mob.y, mob.z, random, this);
            figure.jumpTo(mob);
            return figure;
        } catch (RuntimeException e) {
            brokenFigures.add(mob.id); // an entity that can't be recreated on the client is left out
            return null;
        }
    }

    /** Animates the figures of a pedestal's mobs up to the given game time (where they are comes from the mobs). */
    void tickPedestal(long time, List<IslandMob> mobs) {
        syncPedestal(mobs);
        int ticks = pedestalTick < 0 || time < pedestalTick ? 1 : (int) Math.min(5, time - pedestalTick);
        pedestalTick = time;
        if (ticks <= 0) {
            return;
        }
        for (IslandMob mob : mobs) {
            IslandFigure figure = pedestal.get(mob.id);
            if (figure != null) {
                for (int t = 0; t < ticks; t++) {
                    figure.follow(mob);
                }
            }
        }
    }

    /** Draws a pedestal's mob figures (same culling as {@link #renderFigures}). */
    void renderPedestal(List<IslandMob> mobs, PoseStack poseStack, MultiBufferSource buffer, float partialTick, double distance, double reach) {
        syncPedestal(mobs);
        if (pedestal.isEmpty()) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            pedestal.entrySet().removeIf(e -> {
                if (distance <= reach * figureSize(e.getValue().entity)
                        && !e.getValue().render(this, dispatcher, poseStack, buffer, partialTick)) {
                    brokenFigures.add(e.getKey());
                    return true;
                }
                return false;
            });
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    /** Size (blocks) of the biggest of a pedestal's mob figures, 0 without any. */
    double biggestPedestalFigure(List<IslandMob> mobs) {
        syncPedestal(mobs);
        double max = 0;
        for (IslandFigure figure : pedestal.values()) {
            max = Math.max(max, figureSize(figure.entity));
        }
        return max;
    }

    /** Size (blocks) of the biggest mob figure, 0 without figures. */
    double biggestFigure() {
        if (biggestFigure < 0) {
            double max = 0;
            for (IslandFigure figure : figures) {
                max = Math.max(max, figureSize(figure.entity));
            }
            biggestFigure = max;
        }
        return biggestFigure;
    }

    // ---- light ----

    private void computeLight() {
        int count = states.length;
        byte[] opacity = new byte[count];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int i = index(x, y, z);
                    pos.set(x, y, z);
                    opacity[i] = (byte) Math.max(0, Math.min(15, states[i].getLightBlock(this, pos)));
                    blockLight[i] = (byte) Math.max(0, Math.min(15, states[i].getLightEmission()));
                }
            }
        }
        // Sunlight straight down each column.
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int light = 15;
                for (int y = height - 1; y >= 0; y--) {
                    int i = index(x, y, z);
                    light = Math.max(0, light - opacity[i]);
                    skyLight[i] = (byte) light;
                }
            }
        }
        // Open air around the island (connected to its sides or bottom) is in daylight: a floating island is lit from
        // the sides too, so its underside isn't black. Closed caves stay dark.
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    boolean border = y == 0 || x == 0 || z == 0 || x == size - 1 || z == size - 1;
                    int i = index(x, y, z);
                    if (border && opacity[i] == 0 && skyLight[i] < 15) {
                        skyLight[i] = 15;
                        open.add(i);
                    }
                }
            }
        }
        int layer = size * size;
        while (!open.isEmpty()) {
            int i = open.poll();
            int x = i % size, z = (i / size) % size, y = i / layer;
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                if (inside(nx, ny, nz)) {
                    int n = index(nx, ny, nz);
                    if (opacity[n] == 0 && skyLight[n] < 15) {
                        skyLight[n] = 15;
                        open.add(n);
                    }
                }
            }
        }
        spread(skyLight, opacity);
        spread(blockLight, opacity);
    }

    /** Spreads light to neighbours, losing at least 1 per block (like Minecraft's light engine). */
    private void spread(byte[] light, byte[] opacity) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < light.length; i++) {
            if (light[i] > 1) {
                queue.add(i);
            }
        }
        int layer = size * size;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int l = light[i];
            int x = i % size, z = (i / size) % size, y = i / layer;
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                if (!inside(nx, ny, nz)) {
                    continue;
                }
                int n = index(nx, ny, nz);
                int next = l - Math.max(1, opacity[n]);
                if (next > light[n]) {
                    light[n] = (byte) next;
                    if (next > 1) {
                        queue.add(n);
                    }
                }
            }
        }
    }

    // ---- BlockAndTintGetter ----

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return stateAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 1024;
    }

    @Override
    public int getMinBuildHeight() {
        return -512;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0F;
        }
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!inside(x, y, z)) {
            return layer == LightLayer.SKY ? 15 : 0;
        }
        return layer == LightLayer.SKY ? skyLight[index(x, y, z)] : blockLight[index(x, y, z)];
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return Math.max(getBrightness(LightLayer.SKY, pos) - amount, getBrightness(LightLayer.BLOCK, pos));
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        int x = Math.max(0, Math.min(size - 1, pos.getX()));
        int z = Math.max(0, Math.min(size - 1, pos.getZ()));
        Biome biome = columnBiomes[z * size + x];
        return biome == null ? 0xFFFFFF : resolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        LIVE.remove(this);
    }
}
