package com.example.skyblocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What can be built of several blocks: the containment chamber, the containment tank and the big OIIA cat, block by
 * block (for the guide book's pages and JEI's).
 */
public final class Structures {
    /** One structure: its blocks (relative to its lowest corner), what it makes, what it takes. */
    public record Structure(String id, ItemStack result, int size, Map<BlockPos, BlockState> blocks) {
        /** What it takes, how many of each. */
        public List<ItemStack> needs() {
            Map<Item, Integer> count = new LinkedHashMap<>();
            for (BlockState s : blocks.values()) {
                count.merge(s.getBlock().asItem(), 1, Integer::sum);
            }
            List<ItemStack> out = new ArrayList<>();
            count.forEach((item, n) -> out.add(new ItemStack(item, n)));
            return out;
        }
    }

    private Structures() {}

    /** The edges of a cube {@code size} across (and of its faces, if {@code faces} isn't null), its middle {@code middle}. */
    private static Map<BlockPos, BlockState> cube(int size, Block edges, Block faces, Block middle) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        int m = size - 1;
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int outside = (x == 0 || x == m ? 1 : 0) + (y == 0 || y == m ? 1 : 0) + (z == 0 || z == m ? 1 : 0);
                    if (outside >= 2) {
                        map.put(new BlockPos(x, y, z), edges.defaultBlockState());
                    } else if (outside == 1 && faces != null) {
                        map.put(new BlockPos(x, y, z), faces.defaultBlockState());
                    }
                }
            }
        }
        map.put(new BlockPos(size / 2, size / 2, size / 2), middle.defaultBlockState());
        return map;
    }

    private static Structure chamber(int size) {
        return new Structure("containment_chamber", new ItemStack(ModItems.SINGULARITY.get()), size,
                cube(size, ModBlocks.CONTAINMENT_FRAME.get(), null, ModBlocks.SINGULARITY.get()));
    }

    private static Structure tank(int size) {
        return new Structure("containment_tank", new ItemStack(ModItems.WHIRLPOOL.get()), size,
                cube(size, ModBlocks.TANK_FRAME.get(), ModBlocks.TANK_GLASS.get(), ModBlocks.WHIRLPOOL.get()));
    }

    private static Structure coreChamber(int size) {
        return new Structure("core_chamber", new ItemStack(ModItems.POWER_CORE.get()), size,
                cube(size, ModBlocks.CORE_FRAME.get(), null, ModBlocks.POWER_CORE.get()));
    }

    private static Structure forge(int size) {
        return new Structure("star_forge", new ItemStack(ModItems.STAR_FORGE.get()), size, cube(size, ModBlocks.FORGE_FRAME.get(), null, ModBlocks.STAR_FORGE.get()));
    }

    private static Structure accelerator(int r) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                if (ParticleAcceleratorBlockEntity.onRing(x, z, r)) {
                    map.put(new BlockPos(x + r, 0, z + r), ModBlocks.ACCELERATOR_FRAME.get().defaultBlockState());
                }
            }
        }
        map.put(new BlockPos(r, 0, r), ModBlocks.PARTICLE_ACCELERATOR.get().defaultBlockState());
        return new Structure("particle_accelerator", new ItemStack(ModItems.PARTICLE_ACCELERATOR.get()), 2 * r + 1, map);
    }

    private static Structure weather(int masts) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        map.put(new BlockPos(0, 0, 0), ModBlocks.WEATHER_MACHINE.get().defaultBlockState());
        for (int i = 1; i <= masts; i++) {
            map.put(new BlockPos(0, i, 0), ModBlocks.WEATHER_MAST.get().defaultBlockState());
        }
        return new Structure("weather_machine", new ItemStack(ModItems.WEATHER_MACHINE.get()), masts + 1, map);
    }

    /** For JEI: the chamber, the tank and the core chamber, each in every size (5, 7 and 9 across). */
    public static List<Structure> everySize() {
        List<Structure> out = new ArrayList<>();
        for (int size = 5; size <= 9; size += 2) {
            out.add(chamber(size));
        }
        for (int size = 5; size <= 9; size += 2) {
            out.add(tank(size));
        }
        for (int size = 5; size <= 9; size += 2) {
            out.add(coreChamber(size));
        }
        out.add(forge(5));
        out.add(forge(7));
        for (int r = 3; r <= 7; r += 2) {
            out.add(accelerator(r));
        }
        for (int m = 2; m <= 6; m += 2) {
            out.add(weather(m));
        }
        return out;
    }

    /** For the guide book: each structure once, its smallest. */
    public static List<Structure> all() {
        List<Structure> out = new ArrayList<>();
        out.add(chamber(5));
        out.add(tank(5));
        out.add(coreChamber(5));
        out.add(forge(5));
        out.add(accelerator(3));
        out.add(weather(2));
        Map<BlockPos, BlockState> cats = new LinkedHashMap<>();
        for (int i = 0; i < 27; i++) {
            cats.put(new BlockPos(i % 3, i / 9, (i / 3) % 3), ModBlocks.OIIA_CAT.get().defaultBlockState());
        }
        out.add(new Structure("big_oiia_cat", new ItemStack(ModItems.OIIA_CAT.get()), 3, cats));
        return out;
    }
}
