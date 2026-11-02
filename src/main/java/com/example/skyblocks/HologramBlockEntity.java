package com.example.skyblocks;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The hologram projector: what it shows - an item (a copy: the item isn't used up), a player (the one who set it: their
 * skin, from their profile) or a mob (from a spawn egg) - and the hologram's colour.
 */
public class HologramBlockEntity extends GlowColourBlockEntity {
    public static final int ITEM = 0, PLAYER = 1, MOB = 2;
    private int mode = ITEM;
    private ItemStack shown = ItemStack.EMPTY;
    @Nullable private UUID playerId;
    private String playerName = "";
    /** The player's skin as the server knows it (the profile's "textures" property), so every client can show it. */
    private String skinValue = "", skinSignature = "";
    /** The mob's entity type (its registry name). */
    private String mob = "";

    public HologramBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLOGRAM.get(), pos, state, 0x45E0FF);
    }

    public int mode() {
        return mode;
    }

    public ItemStack shown() {
        return mode == ITEM ? shown : ItemStack.EMPTY;
    }

    public boolean showing() {
        return mode == ITEM ? !shown.isEmpty() : mode == PLAYER ? playerId != null : !mob.isEmpty();
    }

    @Nullable
    public UUID playerId() {
        return playerId;
    }

    /** The shown player's profile, with their skin if known (null if not a player). */
    @Nullable
    public GameProfile profile() {
        if (mode != PLAYER || playerId == null) {
            return null;
        }
        GameProfile profile = new GameProfile(playerId, playerName);
        if (!skinValue.isEmpty()) {
            profile.getProperties().put("textures", new Property("textures", skinValue, skinSignature.isEmpty() ? null : skinSignature));
        }
        return profile;
    }

    public String mob() {
        return mode == MOB ? mob : "";
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void setShown(ItemStack stack) {
        mode = ITEM;
        shown = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        changed();
    }

    public void setPlayer(GameProfile profile) {
        mode = PLAYER;
        playerId = profile.getId();
        playerName = profile.getName() == null ? "" : profile.getName();
        Property tex = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        skinValue = tex == null ? "" : tex.value();
        skinSignature = tex == null || tex.signature() == null ? "" : tex.signature();
        changed();
    }

    public void setMob(String type) {
        mode = MOB;
        mob = type;
        changed();
    }

    public void clear() {
        mode = ITEM;
        shown = ItemStack.EMPTY;
        playerId = null;
        mob = "";
        changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("mode", mode);
        if (!shown.isEmpty()) {
            tag.put("shown", shown.save(registries));
        }
        if (playerId != null) {
            tag.putUUID("player", playerId);
            tag.putString("playerName", playerName);
            tag.putString("skin", skinValue);
            tag.putString("skinSig", skinSignature);
        }
        if (!mob.isEmpty()) {
            tag.putString("mob", mob);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.getInt("mode");
        shown = tag.contains("shown") ? ItemStack.parse(registries, tag.getCompound("shown")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        playerId = tag.hasUUID("player") ? tag.getUUID("player") : null;
        playerName = tag.getString("playerName");
        skinValue = tag.getString("skin");
        skinSignature = tag.getString("skinSig");
        mob = tag.getString("mob");
    }
}
