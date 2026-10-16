package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The hunters' camp over an arena, and the way down: the arena's east tunnel (as the arena was built) carries on a
 * few blocks into a walled spiral stairway with a lit pillar, up to the surface; on top, a little roof over the shaft
 * and a camp around it: two tents (a chest of supplies in one, a crafting table in the other), a campfire with log
 * seats, lantern posts, a banner and a sign. Built by the arena's core once all of it is loaded (existing arenas too).
 */
final class ArenaCamp {
    private static final int SHAFT = 41;      // the stairway's middle, out from the core along the tunnel
    private static final int CAMP_RADIUS = 10;
    static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "chests/hunter_camp"));

    private final ServerLevel level;
    private final BlockPos shaft;
    private final Direction forward, side;
    private final boolean stygian;

    private ArenaCamp(ServerLevel level, BlockPos shaft, Direction forward, boolean stygian) {
        this.level = level;
        this.shaft = shaft;
        this.forward = forward;
        this.side = forward.getClockWise();
        this.stygian = stygian;
    }

    /** Builds it, if everything it touches is loaded; returns whether it was built. */
    static boolean build(ServerLevel level, BlockPos core, Direction facing, boolean stygian) {
        Direction tunnel = facing.getClockWise(); // the arena's east tunnel, in the arena's own turn
        BlockPos shaft = core.relative(tunnel, SHAFT).above(6);
        for (int k = -CAMP_RADIUS; k <= CAMP_RADIUS; k += 4) {
            for (int j = -CAMP_RADIUS; j <= CAMP_RADIUS; j += 4) {
                BlockPos p = shaft.offset(k, 0, j);
                if (!level.hasChunk(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getZ()))) {
                    return false;
                }
            }
        }
        new ArenaCamp(level, shaft, tunnel, stygian).build(core.relative(tunnel, 36).above(6));
        return true;
    }

    // ---- local coordinates: a along the tunnel (away from the arena), b to its right, y absolute
    private BlockPos at(int a, int y, int b) {
        return new BlockPos(shaft.getX() + forward.getStepX() * a + side.getStepX() * b, y,
                shaft.getZ() + forward.getStepZ() * a + side.getStepZ() * b);
    }

    private void set(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private BlockState wall() {
        return (stygian ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
    }

    private BlockState planks() {
        return (stygian ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS).defaultBlockState();
    }

    private BlockState log(Direction.Axis axis) {
        return (stygian ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private BlockState stairs(Direction facing) {
        return (stygian ? Blocks.DARK_OAK_STAIRS : Blocks.SPRUCE_STAIRS).defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private void build(BlockPos tunnelEnd) {
        int y0 = shaft.getY(); // walking level of the tunnel
        // 1. the tunnel carries on from where the arena's ends, to the stairway
        for (int a = -5; a <= -2; a++) {
            for (int b = -2; b <= 2; b++) {
                for (int y = y0 - 1; y <= y0 + 4; y++) {
                    boolean edge = Math.abs(b) == 2 || y == y0 - 1 || y == y0 + 4;
                    set(at(a, y, b), edge ? (y == y0 - 1 && Math.abs(b) < 2 ? planks() : wall()) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // 2. the stairway: walls round a 3x3, a pillar in the middle, steps going round it, up to the surface
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shaft.getX(), shaft.getZ()); // stand height
        boolean water = !level.getFluidState(new BlockPos(shaft.getX(), top - 1, shaft.getZ())).isEmpty();
        if (water) {
            top += 1; // comes up just above the water, onto a little jetty
        }
        top = Math.max(top, y0 + 6);
        for (int y = y0 - 1; y < top; y++) {
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    boolean ring = Math.abs(a) == 2 || Math.abs(b) == 2;
                    if (ring) {
                        boolean door = a == -2 && Math.abs(b) <= 1 && y >= y0 && y <= y0 + 3;
                        set(at(a, y, b), door ? Blocks.AIR.defaultBlockState() : wall());
                    } else if (a == 0 && b == 0) {
                        set(at(a, y, b), (y - y0) % 6 == 5 ? (stygian ? Blocks.SHROOMLIGHT : Blocks.OCHRE_FROGLIGHT).defaultBlockState()
                                : log(Direction.Axis.Y));
                    } else {
                        set(at(a, y, b), y == y0 - 1 ? planks() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // steps: round the ring, one block up each, starting from the tunnel side
        int[][] ring = {{-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}};
        for (int i = 1; y0 - 1 + i <= top - 1; i++) {
            int[] c = ring[i % 8], prev = ring[(i - 1) % 8];
            int da = c[0] - prev[0], db = c[1] - prev[1];
            Direction up = da > 0 ? forward : da < 0 ? forward.getOpposite() : db > 0 ? side : side.getOpposite();
            set(at(c[0], y0 - 1 + i, c[1]), stairs(up));
        }
        // 3. at the top: the shaft open, posts and a little roof over it
        for (int y = top; y <= top + 4; y++) {
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    boolean corner = Math.abs(a) == 2 && Math.abs(b) == 2;
                    BlockState s = Blocks.AIR.defaultBlockState();
                    if (y == top + 4) {
                        s = (stygian ? Blocks.DARK_OAK_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    } else if (corner) {
                        s = log(Direction.Axis.Y);
                    } else if (a == 0 && b == 0) {
                        s = y == top ? log(Direction.Axis.Y) : Blocks.AIR.defaultBlockState();
                    }
                    set(at(a, y, b), s);
                }
            }
        }
        set(at(0, top + 3, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        if (water) {
            for (int a = -4; a <= 4; a++) {
                for (int b = -4; b <= 4; b++) {
                    if (Math.abs(a) > 2 || Math.abs(b) > 2) {
                        set(at(a, top - 1, b), planks());
                    }
                }
            }
            return; // no room for a camp on the water
        }
        // 4. the camp around it, each piece standing on the ground where it is (left out on steep or wet ground)
        tent(5, 1, top);
        tent(5, -1, top);
        place(6, 0, top, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, forward));
        place(6, 2, top, log(side.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X));
        place(6, -2, top, log(side.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X));
        for (int[] p : new int[][] {{3, 3}, {3, -3}, {9, 3}, {9, -3}}) {
            BlockPos g = ground(p[0], p[1], top);
            if (g != null) {
                set(g, (stygian ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE).defaultBlockState());
                set(g.above(), Blocks.LANTERN.defaultBlockState());
            }
        }
        BlockPos pole = ground(9, 0, top);
        if (pole != null) {
            for (int k = 0; k < 3; k++) set(pole.above(k), (stygian ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE).defaultBlockState());
            set(pole.above(3), (stygian ? Blocks.RED_BANNER : Blocks.CYAN_BANNER).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BannerBlock.ROTATION, rotationFacing(forward.getOpposite())));
        }
        BlockPos signAt = ground(3, 1, top);
        if (signAt != null) {
            set(signAt, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotationFacing(forward)));
            if (level.getBlockEntity(signAt) instanceof SignBlockEntity sign) {
                SignText text = new SignText()
                        .setMessage(0, Component.translatable("sign.zinogre.camp.0"))
                        .setMessage(1, Component.translatable(stygian ? "sign.zinogre.camp.stygian" : "sign.zinogre.camp.zinogre"))
                        .setMessage(2, Component.translatable("sign.zinogre.camp.2"));
                sign.setText(text, true);
                sign.setText(text, false);
                sign.setChanged();
            }
        }
    }

    /** Where to put something at (a, b): on the ground there, or null if that's far from the camp's level or wet. */
    private BlockPos ground(int a, int b, int top) {
        BlockPos p = at(a, 0, b);
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        if (Math.abs(h - top) > 3 || !level.getFluidState(new BlockPos(p.getX(), h - 1, p.getZ())).isEmpty()) {
            return null;
        }
        return new BlockPos(p.getX(), h, p.getZ());
    }

    private void place(int a, int b, int top, BlockState state) {
        BlockPos g = ground(a, b, top);
        if (g != null) set(g, state);
    }

    /** An A-frame tent to one side (dir +1 right, -1 left), 5 wide and 4 deep, open toward the shaft. */
    private void tent(int a0, int dir, int top) {
        BlockPos base = ground(a0, 6 * dir, top);
        if (base == null) {
            return;
        }
        int y = base.getY();
        BlockState cloth = (stygian ? Blocks.BLACK_WOOL : Blocks.CYAN_WOOL).defaultBlockState();
        BlockState trim = (stygian ? Blocks.RED_WOOL : Blocks.LIGHT_GRAY_WOOL).defaultBlockState();
        for (int d = 0; d < 4; d++) {
            int b = dir * (4 + d);
            for (int w = -2; w <= 2; w++) {
                set(at(a0 + w, y - 1, b), d == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : planks());
                for (int h = 0; h < 3; h++) {
                    boolean cloth3 = Math.abs(w) == 2 - h;               // the sloping sides and the ridge
                    boolean back = d == 3 && Math.abs(w) < 2 - h;          // the back is closed
                    set(at(a0 + w, y + h, b), cloth3 ? (h == 2 ? trim : cloth) : back ? cloth : Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (dir > 0) { // supplies in one tent, a workbench in the other
            BlockPos chest = at(a0, y, dir * 6);
            set(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, dir > 0 ? side.getOpposite() : side));
            RandomizableContainer.setBlockEntityLootTable(level, level.random, chest, SUPPLIES);
        } else {
            set(at(a0, y, dir * 6), Blocks.CRAFTING_TABLE.defaultBlockState());
            set(at(a0 + 1, y, dir * 6), Blocks.BARREL.defaultBlockState());
        }
    }

    /** A standing sign's or banner's 16-step rotation for facing this way. */
    private static int rotationFacing(Direction d) {
        return Mth.floor(d.toYRot() * 16.0F / 360.0F + 0.5F) & 15;
    }
}
