package com.example.skyblocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A teleport pad at work: whatever is on it charges up - a player while crouching (a second), anything else just by
 * staying (two seconds) - then goes in a flash to the next pad of its colour. Arrivals aren't sent straight back: they
 * have to step off first. On the client: how charged it looks, and the flash when something comes or goes.
 */
public class TeleportPadBlockEntity extends BlockEntity {
    private static final int PLAYER_CHARGE = 20, OTHER_CHARGE = 40;
    private final Map<UUID, Integer> charge = new HashMap<>();
    /** Its name, who placed it, whether only they may use it, and where crouching on it sends you (none: next in line). */
    private String name = "";
    @org.jetbrains.annotations.Nullable
    private UUID owner;
    private boolean priv;
    private long target = Long.MIN_VALUE;
    private final Set<UUID> arrived = new HashSet<>();
    /** Client: how charged it looks (0-1), a tick ago, and the flash (ticks left). */
    public float glow, oGlow;
    public int flash;

    public TeleportPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORT_PAD.get(), pos, state);
    }

    private AABB top() {
        return new AABB(worldPosition.getX() + 0.05, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.05,
                worldPosition.getX() + 0.95, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.95);
    }

    public String name() {
        return name;
    }

    public TeleportPads.Pad pad() {
        return new TeleportPads.Pad(getBlockState().getValue(TeleportPadBlock.COLOR), name, owner, priv);
    }

    public boolean canEdit(Player p) {
        return owner == null || owner.equals(p.getUUID()) || p.hasPermissions(2);
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            TeleportPads.get(server).put(worldPosition, pad());
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public void setOwner(UUID id) {
        owner = id;
        changed();
    }

    public void setColour(int c) {
        if (level == null) return;
        level.setBlock(worldPosition, getBlockState().setValue(TeleportPadBlock.COLOR, c), net.minecraft.world.level.block.Block.UPDATE_ALL);
        target = Long.MIN_VALUE; // (a new network: wherever it sent you is likely not on it)
        changed();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            TeleportPads.get(server).put(worldPosition, pad());
        }
    }

    /** Sends {@code p} this pad's menu: its settings, and every pad on its network they may go to. */
    public void openMenu(ServerPlayer p) {
        if (!(level instanceof ServerLevel server)) return;
        int colour = getBlockState().getValue(TeleportPadBlock.COLOR);
        List<TeleportPadPayloads.Entry> list = new java.util.ArrayList<>();
        for (Map.Entry<BlockPos, TeleportPads.Pad> e : TeleportPads.get(server).network(server, worldPosition, colour, p)) {
            if (list.size() >= 64) break;
            list.add(new TeleportPadPayloads.Entry(e.getKey(), e.getValue().name()));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new TeleportPadPayloads.Menu(worldPosition, name, colour, priv, canEdit(p),
                target, list));
    }

    /** Something chosen in the menu. */
    public void act(ServerPlayer p, int action, String text, long to, int value) {
        if (!(level instanceof ServerLevel server)) return;
        boolean edit = canEdit(p);
        switch (action) {
            case TeleportPadPayloads.RENAME -> {
                if (edit) {
                    name = text.length() > 32 ? text.substring(0, 32) : text;
                    changed();
                }
            }
            case TeleportPadPayloads.COLOUR -> {
                if (edit) setColour(Math.max(0, Math.min(16, value)));
            }
            case TeleportPadPayloads.PRIVATE -> {
                if (edit) {
                    priv = value != 0;
                    changed();
                }
            }
            case TeleportPadPayloads.TARGET -> {
                if (edit) {
                    target = to;
                    changed();
                }
            }
            case TeleportPadPayloads.GO -> {
                if (!pad().usableBy(p) || p.distanceToSqr(worldPosition.getCenter()) > 4.5 * 4.5) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skyblocks.teleport_pad.too_far"), true);
                    return;
                }
                BlockPos dest = BlockPos.of(to);
                if (TeleportPads.get(server).reachable(server, dest, getBlockState().getValue(TeleportPadBlock.COLOR), p)) {
                    send(server, p, dest);
                }
                return;
            }
            default -> {}
        }
        openMenu(p); // (the menu again, as it is now)
    }

    private static boolean ready(Entity e) {
        return e instanceof Player p ? p.isShiftKeyDown() && !p.isSpectator() : (e instanceof ItemEntity || e instanceof LivingEntity) && !e.isPassenger();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleportPadBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        List<Entity> on = level.getEntities((Entity) null, be.top(), e -> e.isAlive() && !e.isVehicle());
        Set<UUID> here = new HashSet<>();
        for (Entity e : on) here.add(e.getUUID());
        be.arrived.retainAll(here);
        be.charge.keySet().retainAll(here);
        for (Entity e : on) {
            UUID id = e.getUUID();
            if (be.arrived.contains(id) || !ready(e) || e instanceof Player p && !be.pad().usableBy(p)) {
                be.charge.remove(id);
                continue;
            }
            int c = be.charge.merge(id, 1, Integer::sum);
            if (c == 1) {
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5F, 1.8F);
            }
            int need = e instanceof Player ? PLAYER_CHARGE : OTHER_CHARGE;
            if (c % 4 == 0) {
                server.sendParticles(ParticleTypes.PORTAL, e.getX(), pos.getY() + 0.4, e.getZ(), 6, 0.3, 0.6, 0.3, 0.4);
            }
            if (c >= need) {
                be.charge.remove(id);
                be.send(server, e);
            }
        }
    }

    /** Off to where crouching on it sends you: the pad it's set to, or the next in line. */
    private void send(ServerLevel level, Entity e) {
        int colour = getBlockState().getValue(TeleportPadBlock.COLOR);
        Player who = e instanceof Player p ? p : null;
        TeleportPads pads = TeleportPads.get(level);
        BlockPos to = target != Long.MIN_VALUE && pads.reachable(level, BlockPos.of(target), colour, who) ? BlockPos.of(target)
                : pads.next(level, worldPosition, colour, who);
        if (to == null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
            if (e instanceof Player p) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skyblocks.teleport_pad.none"), true);
            }
            return;
        }
        send(level, e, to);
    }

    private void send(ServerLevel level, Entity e, BlockPos to) {
        double x = to.getX() + 0.5, y = to.getY() + 3.0 / 16, z = to.getZ() + 0.5;
        if (level.getBlockEntity(to) instanceof TeleportPadBlockEntity there) {
            there.arrived.add(e.getUUID());
        }
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
        level.blockEvent(to, level.getBlockState(to).getBlock(), 1, 0);
        level.playSound(null, worldPosition, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.3F);
        if (e instanceof ServerPlayer p) {
            p.teleportTo(level, x, y, z, p.getYRot(), p.getXRot());
        } else {
            e.teleportTo(x, y, z);
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
        level.playSound(null, to, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 0.8F, 1.5F);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 20, 0.25, 0.6, 0.25, 0.05);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TeleportPadBlockEntity be) {
        be.oGlow = be.glow;
        boolean charging = false;
        for (Entity e : level.getEntities((Entity) null, be.top(), Entity::isAlive)) {
            if (ready(e)) charging = true;
        }
        be.glow = charging ? Math.min(1.0F, be.glow + 0.05F) : Math.max(0.0F, be.glow - 0.08F);
        if (be.flash > 0) be.flash--;
        if (charging && level.random.nextInt(3) == 0) {
            float r = 0.35F, a = level.random.nextFloat() * (float) Math.PI * 2;
            level.addParticle(new DustParticleOptions(vec(colour(state)), 0.8F), pos.getX() + 0.5 + Math.cos(a) * r,
                    pos.getY() + 0.25, pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.08, 0);
        }
    }

    private static org.joml.Vector3f vec(float[] c) {
        return new org.joml.Vector3f(c[0], c[1], c[2]);
    }

    /** Its colour (rgb 0-1). */
    public static float[] colour(BlockState state) {
        int c = state.getValue(TeleportPadBlock.COLOR);
        int rgb = c < 16 ? net.minecraft.world.item.DyeColor.byId(c).getTextureDiffuseColor()
                : net.minecraft.util.Mth.hsvToRgb((System.currentTimeMillis() % 4000L) / 4000.0F, 0.6F, 1.0F);
        return new float[] {((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", name);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putBoolean("private", priv);
        tag.putLong("target", target);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("name");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        priv = tag.getBoolean("private");
        target = tag.contains("target") ? tag.getLong("target") : Long.MIN_VALUE;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("name", name);
        tag.putBoolean("private", priv);
        return tag;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            flash = 16;
            return true;
        }
        return super.triggerEvent(id, param);
    }
}
