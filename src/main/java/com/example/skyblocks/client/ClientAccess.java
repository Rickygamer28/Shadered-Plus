package com.example.skyblocks.client;

import com.example.skyblocks.client.island.IslandScannerScreen;
import com.example.skyblocks.island.IslandScannerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only actions called from common code (only ever run on the client, so servers never load this class). */
public final class ClientAccess {

    public static void openIslandScanner(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof IslandScannerBlockEntity scanner) {
            mc.setScreen(new IslandScannerScreen(pos, scanner.size(), scanner.above(), scanner.depth(), scanner.showArea()));
        }
    }

    public static void openFogMachine(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.FogMachineBlockEntity machine) {
            mc.setScreen(new FogMachineScreen(pos, machine.front(), machine.back(), machine.left(), machine.right(), machine.height(), machine.showArea()));
        }
    }

    public static void openAquarium(boolean ocean, boolean deep, int theme) {
        Minecraft.getInstance().setScreen(new AquariumScreen(ocean, deep, theme));
    }

    /** An aquarium lid clicked: its tank's new light, said over the hotbar. */
    public static void showAquariumLight(int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.skyblocks.aquarium_light").append(": ")
                    .append(net.minecraft.network.chat.Component.translatable("screen.skyblocks.aquarium_light." + AquariumSettings.LIGHTS[light])
                            .withStyle(net.minecraft.ChatFormatting.AQUA)), true);
        }
    }

    public static void openGuide() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    /** Someone's game on an arcade cabinet's screen (or their stopping). */
    public static void arcadeFrame(com.example.skyblocks.ArcadeFramePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(p.pos()) instanceof com.example.skyblocks.ArcadeBlockEntity be) {
            int[] picture = p.picture().length == 0 ? null : ArcadePicture.decode(p.picture(), p.w(), p.h());
            if (picture != null || p.picture().length == 0) { // (one that didn't come right: the last one stays)
                be.livePicture = picture;
                be.liveW = p.w();
                be.liveH = p.h();
            }
            be.liveInput = p.input();
            if (picture != null) ArcadeRenderer.LIVE.add(p.pos().immutable());
            else if (p.picture().length == 0) ArcadeRenderer.LIVE.remove(p.pos());
            be.liveGame = p.game();
            be.liveScore = p.score();
            be.liveName = p.name();
            be.liveAt = mc.level.getGameTime();
            // its sounds, from the cabinet (not for whoever's playing it: they've heard them already)
            if (mc.player != null && !p.name().equals(mc.player.getGameProfile().getName())) ArcadeSounds.playAt(p.pos(), p.sounds());
        }
    }

    public static void openClawMachine(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new ClawScreen(pos));
    }

    public static void openLightBridge(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.LightBridgeEmitterBlockEntity be) {
            mc.setScreen(new LightBridgeScreen(pos, be));
        }
    }

    /** A teleport pad's menu, from the server: opened, or brought up to date if it's open. */
    public static void teleportMenu(com.example.skyblocks.TeleportPadPayloads.Menu p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TeleportPadScreen s && s.pos().equals(p.pos())) {
            s.update(p);
        } else {
            mc.setScreen(new TeleportPadScreen(p));
        }
    }

    /** This client's own player. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.entity.player.Player player() {
        return Minecraft.getInstance().player;
    }

    /** Someone's claw on a claw machine. */
    public static void clawState(com.example.skyblocks.ClawPayloads.State p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(p.pos()) instanceof com.example.skyblocks.ClawMachineBlockEntity be) {
            be.liveX = p.x() / 1000.0F;
            be.liveZ = p.z() / 1000.0F;
            be.liveOpen = p.depth() < 2000;
            be.liveDepth = (p.depth() % 2000) / 1000.0F;
            be.liveHolding = p.holding();
            be.liveAt = mc.level.getGameTime();
        }
    }

    /** Versus at the arcade: a match starting at the cabinet you're at, or ending. */
    public static void arcadeVersus(com.example.skyblocks.ArcadeVersusPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.kind() == com.example.skyblocks.ArcadeVersusPayload.START) {
            if (mc.screen instanceof ArcadeScreen s && s.pos().equals(p.pos())) {
                s.startVersus(p);
            } else { // (not at it any more)
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(
                        com.example.skyblocks.ArcadeVersusPayload.LEAVE, p.pos(), p.match()));
            }
        } else if (p.kind() == com.example.skyblocks.ArcadeVersusPayload.END && mc.screen instanceof ArcadeScreen s) {
            s.endVersus(p);
        }
    }

    public static void openArcade(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new ArcadeScreen(pos));
    }

    public static void openEffectBlock(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.EffectBlockEntity be) {
            mc.setScreen(new EffectBlockScreen(pos, be.settings()));
        }
    }

    public static void openNeonSign(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.NeonSignBlockEntity sign) {
            mc.setScreen(new NeonSignScreen(pos, sign.text(), sign.colour(), sign.mode() | (sign.outline() ? com.example.skyblocks.NeonSignBlockEntity.OUTLINE_BIT : 0), sign.size(), sign.background(),
                    sign.font(), sign.colour2()));
        }
    }

    public static void openVanillaPond(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.example.skyblocks.KoiPondBlockEntity pond) {
            mc.setScreen(new VanillaPondScreen(pos, pond.rim(), pond.floor()));
        }
    }

    private ClientAccess() {}
}
