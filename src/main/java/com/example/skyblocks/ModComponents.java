package com.example.skyblocks;

import com.example.skyblocks.island.IslandData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SkyBlocksMod.MOD_ID);

    /** The scanned island a Sky Island item holds. */
    public static final Supplier<DataComponentType<IslandData>> ISLAND = COMPONENTS.register("island",
            () -> DataComponentType.<IslandData>builder()
                    .persistent(IslandData.CODEC)
                    .networkSynchronized(IslandData.STREAM_CODEC)
                    .build());

    private ModComponents() {}
}
