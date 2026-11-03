package com.example.skyblocks.client;

import com.example.skyblocks.PortalCrossPayload;
import com.example.skyblocks.PortalMirrors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * You went through a portal mirror: moved at once on this side (no snap a moment later), your speed and the way you
 * look turned with the portal, last tick's place moved too so nothing jumps between ticks; the server is told
 * (PortalCrossPayload) and does the same.
 */
public final class PortalClient {
    private PortalClient() {}

    public static void crossed(Entity entity, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || mc.level == null) {
            return;
        }
        LocalPlayer p = mc.player;
        PortalMirrors.Portal a = PortalMirrors.component(mc.level, pos);
        PortalMirrors.Portal b = a == null ? null : MirrorRenderer.partnerOf(a);
        if (b == null) {
            return;
        }
        Vec3 at = p.position();
        PacketDistributor.sendToServer(new PortalCrossPayload(pos, at.x, at.y, at.z));
        int q = PortalMirrors.turns(a.facing(), b.facing());
        float turn = 90.0F * q;
        Vec3 to = PortalMirrors.apply(a, b, at);
        Vec3 o = PortalMirrors.apply(a, b, new Vec3(p.xo, p.yo, p.zo));
        Vec3 old = PortalMirrors.apply(a, b, new Vec3(p.xOld, p.yOld, p.zOld));
        p.setPos(to);
        p.xo = o.x;
        p.yo = o.y;
        p.zo = o.z;
        p.xOld = old.x;
        p.yOld = old.y;
        p.zOld = old.z;
        p.setDeltaMovement(PortalMirrors.rotate(p.getDeltaMovement(), q));
        p.setYRot(p.getYRot() + turn);
        p.yRotO += turn;
        p.yHeadRot += turn;
        p.yHeadRotO += turn;
        p.yBodyRot += turn;
        p.yBodyRotO += turn;
        p.yBob += turn;
        p.yBobO += turn;
        p.setPortalCooldown(5);
        mc.level.playLocalSound(to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F, false);
    }
}
