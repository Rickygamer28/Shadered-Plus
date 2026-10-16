package com.example.zinogre;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Zinogre's own sounds (assets/zinogre/sounds.json): its voice and attacks are vanilla sounds mixed and pitched
 * for a big wolf-like beast, the electricity (crackle, charge-up, zap, power-down) is synthesized for the mod.
 */
public final class ZinogreSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ZinogreMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = sound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROWL = sound("growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = sound("roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOWL = sound("howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE = sound("bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING = sound("swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound("slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAP = sound("leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> PART_BREAK = sound("part_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACKLE = sound("crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_UP = sound("charge_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZAP = sound("zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCHARGE = sound("discharge");
    /** The Alpha fight's music (see client/ZinogreBossMusic); its track is supplied locally, not in the repository. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_MUSIC = sound("boss_music");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, name)));
    }

    private ZinogreSounds() {}
}
