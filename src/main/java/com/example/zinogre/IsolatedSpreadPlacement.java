package com.example.zinogre;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Random spread (like vanilla's) that also keeps clear of every other underground structure, vanilla's or another
 * mod's: an arena isn't started where any underground structure set (trial chambers, ancient cities, strongholds,
 * mineshafts, modded dungeons...) would start within {@code keep_away_chunks} chunks. Vanilla's exclusion zone can only
 * name one other set. Structures that start in nearly every chunk at a low chance (mineshafts) are checked over a
 * smaller range, so they don't rule out most spots.
 */
public class IsolatedSpreadPlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<IsolatedSpreadPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
            Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
            Codec.INT.fieldOf("salt").forGetter(p -> p.saltValue),
            Codec.intRange(0, 16).optionalFieldOf("keep_away_chunks", 8).forGetter(p -> p.keepAway)
    ).apply(i, IsolatedSpreadPlacement::new));

    private final int saltValue;
    private final int keepAway;

    public IsolatedSpreadPlacement(int spacing, int separation, int salt, int keepAway) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), spacing, separation, RandomSpreadType.LINEAR);
        this.saltValue = salt;
        this.keepAway = keepAway;
    }

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int x, int z) {
        if (!super.isStructureChunk(state, x, z)) {
            return false;
        }
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            // not our own arenas (they are spread apart already, and checking them would loop back here)
            if (set.value().placement() instanceof IsolatedSpreadPlacement || !underground(set.value())) {
                continue;
            }
            int range = set.value().placement() instanceof RandomSpreadStructurePlacement spread && spread.spacing() <= 2
                    ? Math.min(3, keepAway) : keepAway;
            if (state.hasStructureChunkInRange(set, x, z, range)) {
                return false;
            }
        }
        return true;
    }

    /** Whether any of the set's structures is built underground (where it could cut into an arena). */
    private static boolean underground(StructureSet set) {
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            GenerationStep.Decoration step = entry.structure().value().step();
            if (step == GenerationStep.Decoration.UNDERGROUND_STRUCTURES || step == GenerationStep.Decoration.UNDERGROUND_DECORATION
                    || step == GenerationStep.Decoration.STRONGHOLDS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ZinogreMod.ISOLATED_SPREAD.get();
    }
}
