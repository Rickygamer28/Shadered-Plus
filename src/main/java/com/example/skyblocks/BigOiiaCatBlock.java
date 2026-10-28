package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The big OIIA cat: what a cube of OIIA cats turns into, from 2x2x2 up to 23x23x23. The cat is the small cat scaled
 * by the cube's size N, so it fills the cube like the small cat fills its block. The blocks of the cube that the cat
 * covers become parts of this block; the rest of the cube becomes air.
 *
 * The bottom-centre part is the "master": only it has a block entity (BigOiiaCatBlockEntity: size, facing,
 * redstone power), which draws the cat (client/OiiaCatRenderer). Every other part points one step towards the master
 * (LINK), so any part can find it. EDGE_X/Y/Z and CUT give each part its share of the cat's collision shape.
 *
 * A redstone signal on any part makes it spin, like the small cat. Breaking any part breaks the whole cat and gives
 * back all N*N*N small cats.
 */
public class BigOiiaCatBlock extends Block implements EntityBlock {

    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 23;

    /** Which way the master is from this part (MASTER = this is it). */
    public enum Link implements StringRepresentable {
        MASTER(null), DOWN(Direction.DOWN), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST), EAST(Direction.EAST);

        @Nullable
        final Direction direction;

        Link(@Nullable Direction direction) {
            this.direction = direction;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Where a part sits in the cat along one axis: on its low side, inside, or on its high side. */
    public enum Edge implements StringRepresentable {
        LOW, MID, HIGH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Link> LINK = EnumProperty.create("link", Link.class);
    public static final EnumProperty<Edge> EDGE_X = EnumProperty.create("edge_x", Edge.class);
    public static final EnumProperty<Edge> EDGE_Y = EnumProperty.create("edge_y", Edge.class, Edge.MID, Edge.HIGH);
    public static final EnumProperty<Edge> EDGE_Z = EnumProperty.create("edge_z", Edge.class);
    /**
     * How far (in pixels) the cat's sides start into the low-side parts: (3 * N) mod 16. The small cat's shape is
     * 3..13 x 0..13 x 3..13 pixels, so scaled by N its sides start at 3N/16 blocks and end at 13N/16 blocks; the
     * high-side parts are cut at 16 - CUT pixels (13N mod 16 = -3N mod 16), and so is the top.
     */
    public static final IntegerProperty CUT = IntegerProperty.create("cut", 0, 15);

    /** Collision shapes by [cut][edge x][edge y (MID/HIGH)][edge z]. */
    private static final VoxelShape[][][][] SHAPES = new VoxelShape[16][3][2][3];

    static {
        for (int cut = 0; cut < 16; cut++) {
            int high = cut == 0 ? 16 : 16 - cut;
            for (Edge ex : Edge.values()) {
                for (int ey = 0; ey < 2; ey++) {
                    for (Edge ez : Edge.values()) {
                        double x0 = ex == Edge.LOW ? cut : 0, x1 = ex == Edge.HIGH ? high : 16;
                        double z0 = ez == Edge.LOW ? cut : 0, z1 = ez == Edge.HIGH ? high : 16;
                        double y1 = ey == 1 ? high : 16;
                        SHAPES[cut][ex.ordinal()][ey][ez.ordinal()] = Block.box(x0, 0, z0, x1, y1, z1);
                    }
                }
            }
        }
    }

    /** True while this class itself changes parts, so those changes don't start redstone checks or breaks. */
    private static boolean busy;

    public BigOiiaCatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LINK, Link.MASTER).setValue(EDGE_X, Edge.MID)
                .setValue(EDGE_Y, Edge.MID).setValue(EDGE_Z, Edge.MID).setValue(CUT, 0));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINK, EDGE_X, EDGE_Y, EDGE_Z, CUT);
    }

    // ---- geometry (cube coordinates: 0..N-1 from the cube's lowest north-west corner) ----

    /** First block of the cube (per axis, x/z) that the cat covers. */
    static int low(int n) {
        return 3 * n / 16;
    }

    /** Last block of the cube (per axis, x/y/z) that the cat covers. */
    static int high(int n) {
        return (13 * n + 15) / 16 - 1;
    }

    /** The master's x/z in the cube (its y is 0). */
    static int middle(int n) {
        return n / 2;
    }

    static boolean covered(int n, int x, int y, int z) {
        int lo = low(n), hi = high(n);
        return x >= lo && x <= hi && z >= lo && z <= hi && y <= hi;
    }

    /** The cube's lowest north-west corner, from the master's position. */
    static BlockPos origin(BlockPos master, int n) {
        return master.offset(-middle(n), 0, -middle(n));
    }

    /** The cat's whole shape (the small cat's shape scaled by n), in world coordinates. */
    public static AABB catBox(BlockPos master, int n) {
        BlockPos o = origin(master, n);
        double lo = 3 * n / 16.0, hi = 13 * n / 16.0;
        return new AABB(o.getX() + lo, o.getY(), o.getZ() + lo, o.getX() + hi, o.getY() + hi, o.getZ() + hi);
    }

    private BlockState partState(int n, int x, int y, int z) {
        int lo = low(n), hi = high(n), m = middle(n);
        Link link = y > 0 ? Link.DOWN
                : x < m ? Link.EAST : x > m ? Link.WEST
                : z < m ? Link.SOUTH : z > m ? Link.NORTH
                : Link.MASTER;
        return defaultBlockState().setValue(LINK, link)
                .setValue(EDGE_X, x == lo ? Edge.LOW : x == hi ? Edge.HIGH : Edge.MID)
                .setValue(EDGE_Y, y == hi ? Edge.HIGH : Edge.MID)
                .setValue(EDGE_Z, z == lo ? Edge.LOW : z == hi ? Edge.HIGH : Edge.MID)
                .setValue(CUT, (3 * n) % 16);
    }

    /** Follows the parts' links to the master; null if the chain is broken. */
    @Nullable
    public static BlockPos findMaster(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos cur = pos;
        BlockState st = state;
        for (int step = 0; step <= 3 * MAX_SIZE; step++) {
            if (!(st.getBlock() instanceof BigOiiaCatBlock)) {
                return null;
            }
            Link link = st.getValue(LINK);
            if (link == Link.MASTER) {
                return cur;
            }
            cur = cur.relative(link.direction);
            st = level.getBlockState(cur);
        }
        return null;
    }

    // ---- block behaviour ----

    static boolean isMaster(BlockState state) {
        return state.getValue(LINK) == Link.MASTER;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new BigOiiaCatBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    /** This part's share of the cat (the outline of the whole cat is drawn by client/BigOiiaCatOutline). */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(CUT)][state.getValue(EDGE_X).ordinal()]
                [state.getValue(EDGE_Y) == Edge.HIGH ? 1 : 0][state.getValue(EDGE_Z).ordinal()];
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.OIIA_CAT.get());
    }

    /** Drops are handled below (all the cats once, not one per part). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
    }

    // ---- forming ----

    /**
     * Called when a small cat is placed. If the cats touching it (sides, not corners) form exactly a solid cube of
     * 2x2x2 to 23x23x23, they become one big cat, facing the same way as the cat just placed (so, the player).
     */
    public static void tryForm(Level level, BlockPos placed) {
        BlockState placedState = level.getBlockState(placed);
        Block cat = ModBlocks.OIIA_CAT.get();
        if (!placedState.is(cat)) {
            return;
        }
        Set<BlockPos> cats = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        placed = placed.immutable();
        cats.add(placed);
        queue.add(placed);
        int minX = placed.getX(), minY = placed.getY(), minZ = placed.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            if (maxX - minX >= MAX_SIZE || maxY - minY >= MAX_SIZE || maxZ - minZ >= MAX_SIZE) {
                return; // bigger than the biggest cube
            }
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (!cats.contains(next) && level.isLoaded(next) && level.getBlockState(next).is(cat)) {
                    cats.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        int n = maxX - minX + 1;
        if (n < MIN_SIZE || maxY - minY + 1 != n || maxZ - minZ + 1 != n || cats.size() != n * n * n) {
            return;
        }
        ((BigOiiaCatBlock) ModBlocks.BIG_OIIA_CAT.get()).form(level, new BlockPos(minX, minY, minZ), n,
                placedState.getValue(OiiaCatBlock.FACING));
    }

    private void form(Level level, BlockPos origin, int n, Direction facing) {
        busy = true;
        try {
            // Replace the whole cube without neighbour updates first (no part sees a half-built cat)...
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        BlockState s = covered(n, x, y, z) ? partState(n, x, y, z) : Blocks.AIR.defaultBlockState();
                        level.setBlock(origin.offset(x, y, z), s, Block.UPDATE_CLIENTS);
                    }
                }
            }
            BlockPos master = origin.offset(middle(n), 0, middle(n));
            if (level.getBlockEntity(master) instanceof BigOiiaCatBlockEntity be) {
                be.setup(n, facing);
            }
            // ...then tell the blocks around the cube.
            forEachShell(origin, 0, n - 1, 0, n - 1, p -> level.updateNeighborsAt(p, this));
        } finally {
            busy = false;
        }
        BlockPos master = origin.offset(middle(n), 0, middle(n));
        if (level.getBlockEntity(master) instanceof BigOiiaCatBlockEntity be) {
            be.setPowered(anySignal(level, origin, n));
        }

        AABB box = catBox(master, n);
        float pitch = Math.max(0.5F, 1.2F - n * 0.05F);
        level.playSound(null, master, SoundEvents.CAT_AMBIENT, SoundSource.BLOCKS, 1.0F + n / 4.0F, pitch);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, box.getCenter().x, box.getCenter().y, box.getCenter().z,
                    Math.min(400, 30 * n), n * 0.35, n * 0.35, n * 0.35, 0.02);
        }
    }

    /** Runs for each block on the surface of the box lo..hi (x/z) by 0..yHi (y), in cube coordinates. */
    private static void forEachShell(BlockPos origin, int lo, int hi, int yLo, int yHi,
                                     java.util.function.Consumer<BlockPos> action) {
        for (int x = lo; x <= hi; x++) {
            for (int y = yLo; y <= yHi; y++) {
                for (int z = lo; z <= hi; z++) {
                    if (x == lo || x == hi || y == yLo || y == yHi || z == lo || z == hi) {
                        action.accept(origin.offset(x, y, z));
                    }
                }
            }
        }
    }

    // ---- redstone ----

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || busy) {
            return;
        }
        BlockPos master = findMaster(level, pos, state);
        if (master == null || !(level.getBlockEntity(master) instanceof BigOiiaCatBlockEntity be) || be.size() < MIN_SIZE) {
            return;
        }
        if (level.hasNeighborSignal(pos)) {
            be.setPowered(true);
        } else if (be.powered()) {
            // This part lost its signal: still powered only if another part has one.
            be.setPowered(anySignal(level, origin(master, be.size()), be.size()));
        }
    }

    /** Any part gets a redstone signal (only parts on the cat's surface can: inside ones only touch parts). */
    private static boolean anySignal(Level level, BlockPos origin, int n) {
        boolean[] found = {false};
        forEachShell(origin, low(n), high(n), 0, high(n), p -> {
            if (!found[0] && level.hasNeighborSignal(p)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    // ---- breaking ----

    /** A player breaks a part: remove the rest, and give back the cats unless creative or the wrong tool. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            dismantle(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Any other removal (explosion, command, ...): the same, with the cats dropped. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !busy && !newState.is(this)) {
            dismantle(level, pos, state, true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void dismantle(Level level, BlockPos pos, BlockState state, boolean drop) {
        BlockPos master = findMaster(level, pos, state);
        if (master == null || !(level.getBlockEntity(master) instanceof BigOiiaCatBlockEntity be) || be.dismantled) {
            return; // already being taken apart
        }
        be.dismantled = true;
        int n = be.size();
        if (n < MIN_SIZE) {
            return;
        }
        BlockPos origin = origin(master, n);
        int lo = low(n), hi = high(n);
        busy = true;
        try {
            for (int x = lo; x <= hi; x++) {
                for (int y = 0; y <= hi; y++) {
                    for (int z = lo; z <= hi; z++) {
                        BlockPos p = origin.offset(x, y, z);
                        if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        } finally {
            busy = false;
        }
        forEachShell(origin, lo, hi, 0, hi, p -> level.updateNeighborsAt(p, Blocks.AIR));

        if (drop) {
            int cats = n * n * n;
            int width = hi - lo + 1;
            while (cats > 0) {
                int count = Math.min(64, cats);
                cats -= count;
                BlockPos at = origin.offset(lo + level.random.nextInt(width), 0, lo + level.random.nextInt(width));
                popResource(level, at, new ItemStack(ModItems.OIIA_CAT.get(), count));
            }
        }
    }
}
