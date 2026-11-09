package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An effect block: which effect it shows (client/EffectBlockRenderer, shaders/core/effect_block), with how many of its
 * eight colours, how fast, how big, how bright and whether pixelated - and the group it's part of: blocks placed
 * against one join it, taking its settings and its anchor, so the effect runs across all of them as one picture.
 */
public class EffectBlockEntity extends BlockEntity {
    /** The effects, in the menu's order (the shader's numbers). */
    public static final String[] EFFECTS = {"swirl", "wave", "cycle", "breathe", "strobe", "sparkle", "plasma", "scanner",
            "ripples", "gradient", "aurora", "music", "ink", "fire", "rain",
            "lava", "warp", "spectrum"};
    public static final int MAX_COLOURS = 8;
    /** (a Balatro-like start: red, blue and a dark teal - then gold, green, purple, white, orange) */
    public static final int[] DEFAULT_COLOURS = {0xDE443B, 0x006BB4, 0x162325, 0xF2B53A, 0x3FBF6F, 0x8E5BD9, 0xF4F4F4, 0xFF7A1A, 0x000000};
    /** Where in the colours the background is (behind the effects that light only part of the side), after the eight. */
    public static final int BACKGROUND = MAX_COLOURS;

    private Settings settings = Settings.DEFAULT;
    private long group;
    @Nullable
    private BlockPos anchor;

    /**
     * What an effect block shows. Speed, size, brightness and blur in percent (speed 0-400, size 25-800, brightness
     * 10-100, blur 0-100: how soft the changes from one colour to the next are).
     */
    public record Settings(int effect, int count, int[] colours, int speed, int size, int brightness, boolean pixel, int blur) {
        public static final Settings DEFAULT = new Settings(0, 3, DEFAULT_COLOURS.clone(), 100, 100, 100, false, 0);

        public Settings {
            effect = Math.floorMod(effect, EFFECTS.length);
            count = Math.max(1, Math.min(MAX_COLOURS, count));
            int[] c = new int[MAX_COLOURS + 1];
            for (int i = 0; i <= MAX_COLOURS; i++) {
                c[i] = (colours != null && i < colours.length ? colours[i] : DEFAULT_COLOURS[i]) & 0xFFFFFF;
            }
            colours = c;
            speed = Math.max(0, Math.min(400, speed));
            size = Math.max(25, Math.min(800, size));
            brightness = Math.max(10, Math.min(100, brightness));
            blur = Math.max(0, Math.min(100, blur));
        }

        /** The colour behind the effects that light only part of the side (black to start with). */
        public int background() {
            return colours[BACKGROUND];
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("effect", effect);
            tag.putInt("count", count);
            tag.putIntArray("colours", colours);
            tag.putInt("speed", speed);
            tag.putInt("size", size);
            tag.putInt("brightness", brightness);
            tag.putBoolean("pixel", pixel);
            tag.putInt("blur", blur);
            return tag;
        }

        public static Settings load(CompoundTag tag) {
            if (!tag.contains("effect")) {
                return DEFAULT;
            }
            return new Settings(tag.getInt("effect"), tag.getInt("count"), tag.getIntArray("colours"),
                    tag.contains("speed") ? tag.getInt("speed") : 100, tag.contains("size") ? tag.getInt("size") : 100,
                    tag.contains("brightness") ? tag.getInt("brightness") : 100, tag.getBoolean("pixel"),
                    tag.contains("blur") ? tag.getInt("blur") : DEFAULT.blur());
        }

        /** (for drawing: one number that changes when anything does) */
        public int key() {
            int h = java.util.Arrays.hashCode(colours);
            return ((((h * 31 + effect) * 31 + count) * 31 + speed) * 31 + size) * 31 + brightness * 2 + (pixel ? 1 : 0) + blur * 7919;
        }
    }

    public EffectBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EFFECT_BLOCK.get(), pos, state);
    }

    public Settings settings() {
        return settings;
    }

    public long group() {
        return group;
    }

    /** Where the group's effect is measured from (its first block). */
    public BlockPos anchor() {
        return anchor != null ? anchor : worldPosition;
    }

    /** A new group of its own (a block not put against another). */
    public void startGroup(long id) {
        group = id;
        anchor = worldPosition;
        changed();
    }

    /** Joins another's group: its settings, its anchor (the other starting a group, if it's on its own). */
    public void join(EffectBlockEntity other) {
        if (other.group == 0L) {
            other.startGroup(level != null ? level.random.nextLong() | 1L : System.nanoTime() | 1L);
        }
        settings = other.settings;
        group = other.group;
        anchor = other.anchor();
        changed();
    }

    public void setSettings(Settings s) {
        settings = s;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("settings", settings.save());
        tag.putLong("group", group);
        if (anchor != null) {
            tag.putLong("anchor", anchor.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = Settings.load(tag.getCompound("settings"));
        group = tag.getLong("group");
        anchor = tag.contains("anchor") ? BlockPos.of(tag.getLong("anchor")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
