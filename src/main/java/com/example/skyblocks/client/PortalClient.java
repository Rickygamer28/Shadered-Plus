package com.example.skyblocks.client;

import com.example.skyblocks.PortalCrossPayload;
import com.example.skyblocks.PortalListPayload;
import com.example.skyblocks.PortalMirrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Portal mirrors on your side: every portal in your world (as the server keeps them: which links to which, however
 * far), and you going into one - moved at once when the other one is in sight (no snap a moment later; your speed and
 * the way you look turned with the portal, last tick's place moved too so nothing jumps between ticks), the server told
 * (PortalCrossPayload) and doing the same; when the other is too far to be loaded here, the server moves you.
 */
public final class PortalClient {
    private static List<PortalMirrors.Portal> known = List.of();

    private PortalClient() {}

    /** The portals in your world, from the server. */
    public static void known(PortalListPayload payload) {
        List<PortalMirrors.Portal> list = new ArrayList<>();
        for (PortalListPayload.Entry e : payload.portals()) {
            list.add(new PortalMirrors.Portal(Set.of(), e.min(), e.facing(), e.colour(), e.origin()));
        }
        known = List.copyOf(list);
        MirrorRenderer.portalsChanged();
    }

    /** The portal a is linked to (null: none). */
    public static PortalMirrors.Portal partnerOf(PortalMirrors.Portal a) {
        return PortalMirrors.nearest(a, known);
    }

    public static void crossed(Entity entity, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || mc.level == null) {
            return;
        }
        LocalPlayer p = mc.player;
        PortalMirrors.Portal a = PortalMirrors.component(mc.level, pos);
        PortalMirrors.Portal b = a == null ? null : partnerOf(a);
        if (b == null) {
            return;
        }
        Vec3 at = p.position();
        Vec3 to = PortalMirrors.apply(a, b, at);
        boolean here = mc.level.hasChunkAt(BlockPos.containing(to));
        com.example.skyblocks.SkyBlocksMod.NETWORK.sendToServer(new PortalCrossPayload(pos, at.x, at.y, at.z, here));
        p.setPortalCooldown(5);
        if (!here) {
            return; // (the server moves you there)
        }
        int q = PortalMirrors.turns(a, b);
        float turn = 90.0F * q;
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
        mc.level.playLocalSound(to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F, false);
    }
}
