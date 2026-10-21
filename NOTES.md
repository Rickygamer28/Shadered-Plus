# Project notes

- Shadered (https://github.com/Noodlegamer76/Shadered): the author (Noodlegamer76) has allowed Shadered+ to use
  material from the repo as a Shadered addon. DONE 2026-10-09: the 1.21.1 branch now declares
  mod_license=Attribution-NonCommercial 4.0 International (commit 02e330c), matching master's CC BY-NC 4.0 LICENSE.txt.
  Shadered code/assets may be used: credit Noodlegamer76, link CC BY-NC 4.0, state changes, no commercial use.
  Skybox images in Shadered are by other artists (OpenGameArt): keep their credits and check their own licenses.
- Shadered 2 (https://github.com/Noodlegamer76/Shadered-2) is Forge 1.20.1 only for now; revisit when a 1.21.1 version exists.
- 2026-10-09: Shadered+ is now a Shadered 1.21.1 addon (user's choice: Shadered renderer only). Our own chunk-shader,
  Sodium and Iris code was removed; Framed Blocks camo of sky blocks no longer works. Hook points used:
  ComplexPassRenderer.add(AFTER_SKY, SkyboxRenderPass), SkyblockBatchData, SkyblockHolderEntity, RegisterShaders.get.
  Our mod license is still MIT because no Shadered code is copied; if Shadered code is copied in, change it to fit CC BY-NC 4.0.
- Sky Emitter support via mixins into SkyEmitterEntity, SkyblockHolderEntity.setBlockType, SkyEmitterRenderer.render (Shadered 1.21.1-1.5.8).
- Sky ores (user request, 2026-10-09): every sky must also have ore versions "like Shadered" — keep this for all future skies.
  Implemented generically (SkyOreBlock with `sky` block-state property, SkyOreFeature picks a random sky per vein),
  so a new sky only needs its `sky.skyblocks.<name>` lang entry; textures are Shadered's illusorite_ore / deepslate_illusorite_ore.
- 2026-10-09 (later): user asked to drop our own ores and instead add our skies everywhere Shadered uses SkyblockType.
  Now: mixins extend SkyblockType + SkyblockItemTypes enums (SKYBLOCKS_<NAME>), map them in SkyblockRegistry, and hook
  SkyblockRenderer.getData / SkyblockEntityRenderer.render / ModRenderTypes.getSkyboxTextureId. Our items are Shadered
  SkyblockItems. Illusorite ore veins therefore include our skies automatically (this replaces the earlier "every sky
  gets ores" rule: new skies get Illusorite ores for free). Our own blocks, ores and Sky Emitter patches were removed.
- 2026-10-09: added OIIA Cat model block (user-supplied FBX -> tools/models/oiia_cat, converted to models/complex/oiia_cat.glb). Model credit/licence still needed from the user.

## Skies added 9 Oct 2026: nebula (aurora, snow added then removed: user said they looked bad)
- Nebula = SpaceSkyboxes `01_teal_magenta_nebula` (Godot export, labels correct as-is).
- Aurora = psx-skyboxes-2-free `aurora_green`, Snow = `snow_falling` (256 px, paletted; converted to RGB, upscaled 4x
  nearest). Labels checked with a rendered panorama: correct as-is (seam metric is noisy because of the dithering).
- Credits/licenses for both packs still needed from the user.
- No sky block recipes (removed 9 Oct 2026): skies come from Shadered's Illusorite ores. Don't add recipes for future skies.
- Golden Hour added 9 Oct 2026: StylizedSkyboxes `02_golden_hour` (Godot export, labels correct as-is). Credit/license needed.
- Big OIIA Cat (9 Oct 2026): 3x3x3 cube of small cats forms it (OiiaCatBlock.setPlacedBy -> BigOiiaCatBlock.tryForm); breaking gives back 27 cats (user's choice). Not compiled in the sandbox (mavens blocked): check the first GitHub Actions build.
- Big OIIA Cat reworked (10 Oct 2026): any exact cube 2..23 (connected small cats must be exactly the cube); master BE stores size/facing/powered; parts link to master. Big cats from the 3x3x3 version in test worlds won't survive (block states changed).
- Sky Islands (13 Oct 2026): Island Scanner (menu: size 3..25 odd, above 1..24, depth 0..24) -> Sky Island item with skyblocks:island component (IslandData NBT) -> pedestal block whose BER bakes the island (fake BlockAndTintGetter + VertexBuffers) and draws mob figures. User choices: miniature, snapshot, animated figures, centered + menu. Not compiled here (mavens blocked); reviewed by a second agent and fixes applied.
- Sky Islands round 2 (13 Oct 2026): block entities copied (update tag + id, drawn at far-away fake pos with client level), quads re-emitted via BufferSource (translucentMovingBlock) for sorting/Fabulous/Iris, fish wasTouchingWater accessor mixin. Second review applied.

## 1.20.1 Forge port (branch 1.20.1, 14 Oct 2026)
- Built with ModDevGradle legacyforge (Forge 47.4.10), Java 17, against Shadered 1.4.7 (Modrinth maven). Shadered's sky
  type system (SkyblockType, SkyblockItemTypes, SkyblockRegistry, SkyblockEntity) exists from 1.4.3; 1.3.x lacks it.
- Mixins targeting Shadered use remap = false. Shadered's SkyblockEntityRenderer.render is not mixed into (its name is
  remapped in production); client/OurSkyblockRenderer replaces Shadered's renderer for its skyblock BE and delegates.
- Items: data components became NBT (IslandData.get/set use BlockEntityTag.island; names via hover name).
- 1.20.1 BER poses include the camera rotation: OiiaCatRenderer undoes it for AssimpRenderer.addModel (world space).
- CI starts the client under Xvfb with Shadered + GeckoLib (title screen) to check loading; Shadered 1.4.7 can't start
  a dedicated dev server (LWJGL natives), so no server test.
- Iris / shader packs (18 Oct 2026, from the Shadered Compat Fix conversation; setup NeoForge 1.21.1, Shadered 1.5.8, Iris 1.8.12 +
  Sodium 0.8.13, Complementary Reimagined r5.9.3): Island Paintings (IslandWindow) and pedestal-island cats render into
  client/iris/IrisIslandTarget (offscreen TextureTarget with depth + stencil, cleared once a frame, world depth copied
  from the main target before each use) instead of Iris's gbuffers (no stencil, depth 1.0 treated as sky, custom
  shaders overwritten). Composited after LevelRenderer.renderLevel (GameRendererIrisMixin), premultiplied, discarding
  where the final main depth is in front. Skipped in Iris's shadow pass. Code adapted from the compat addon (MIT).
  Not tested in game yet; compat addon still handles ComplexPassRenderer output and skyboxes.
  User: it works, FPS a bit low. Batched every painting into one end-of-frame pass (f184939): paintings invisible.
  Cause (found by the user/compat session): the overlay's main-depth discard; the island is behind the painting surface,
  so the wall was always "closer". Briefly reverted to per-painting drawing (19387ba/955d1de). Now (user's suggested fix):
  end-of-frame batch again, target gets the frame's FINAL depth once, the window's stencil mark (IslandWindow step 1,
  depth-tested) handles occlusion and the island is drawn inside the mark; cats depth-tested directly; the overlay just
  blends the target where it has content (no depth discard). No glGet state queries (state set twice, texture cache
  forgotten); only FBO + SSBO bindings read, once a frame. Compat Fix v16 adds Iris's view bob to AssimpRenderer's ViewMat.
  User then: inside slides while walking + flicker. Iris moves the view bob from the projection into the camera matrix
  passed to LevelRenderer.renderLevel; paintings now use exactly those two matrices (captured by @ModifyArg index 5/6 in
  GameRendererIrisMixin, like Compat Fix's ViewBob) instead of RenderSystem's at block entity time.
  User: same problems; screenshot showed farther paintings drawn over a nearer one = the window's depth restore (step 4)
  not landing in our target. Shadered's renderers (sky pictures via SkyUsage.ensureFresh, AssimpRenderer for cats) can
  bind another framebuffer / change the stencil mid-painting: IslandWindow.backInside() now rebinds the target
  (IrisIslandTarget.rebindIfDrawing) and re-asserts the stencil/depth state after each of them.
  User: small paintings drawn at a wrong spot, sliding; the big one vanishing at some angles. Likely copies noted down from
  Iris's shadow pass (its BER poses have the sun's view turned in; drawn with the main camera matrix they land elsewhere,
  and their restored depth hides real paintings). Now only poses with no rotation (main view: camera-relative
  translation only) are noted down (IrisIslandTarget.mainViewPose), besides Iris's isRenderingShadowPass; logs once.
  18 Oct (later): the end-of-frame batch kept misbehaving under Iris (other paintings' insides drawn at wrong spots /
  over nearer ones, the big one vanishing at angles; no shadow-pass poses were seen, so not that). Likely the frame's
  final depth isn't the world's at that point (Iris may clear it for the hand), and replaying block-entity draws late is
  fragile. Back to drawing each painting in place at block-entity time (the version the user confirmed working,
  ac03984) with the lean GL state handling (955d1de) and IslandWindow.backInside() (rebind target + stencil after
  Shadered's renderers). End-of-frame batching code removed; GameRendererIrisMixin only composites.
  Timings (user log, 10 paintings/frame, shaders on): cats gpu ~5-6 ms cpu 3.3 ms, iris_setup gpu ~4 ms; shaders off cats
  gpu 1.7 ms cpu 3.3 ms. Cause of cats: Shadered's AssimpRenderer.render uploads its whole bone SSBO (16384 matrices =
  1 MB, rebuilt on CPU) + glMemoryBarrier on every batch; IslandCatBatch makes one batch per painting. Fixed with
  AssimpRendererBonesMixin (upload only used matrices, nothing + no barrier when no animated model). iris_setup now
  split (IRIS_DEPTH_COPY) to see whether the depth blit or the framebuffer switches cost.
  Next log: cats fixed (gpu 0.5 ms, cpu 0.3 ms). iris_depth_copy (the once-a-frame clear + depth blit of the main
  target) was 4.5-7.5 ms gpu. Now no copy: the target's depth/stencil attachment IS Minecraft's main depth texture
  (Iris's depthtex0), its stencil cleared once a frame; the painting leaves the depth as found (step 4). A second
  colour attachment (R32F, cleared to 1.0) gets each painting's visible picture depth (iris_picture_depth shader,
  IslandWindow -> IrisIslandTarget.recordPictureDepth before step 4) for the overlay's hand test. Pedestal cats: depth
  test only (no writes into the pack's depth), no own depth -> always shown by the overlay.
  User: all OK now (paintings right, FPS fine). Painting timings logging now off unless -Dskyblocks.paintingTimings=true.

## 1.20.1 catch-up (18 Oct 2026)
- Squash-merged main (up to 7de82f2) into 1.20.1 and ported: Sky Liquids (Forge FluidType / ForgeFlowingFluid, client
  looks via initializeClient; bucket item's initializeClient runs in Item's constructor, so the sky is looked up lazily),
  GPU island mesh (1.20.1 BufferBuilder.end + VertexBuffer.Usage.STATIC, one shared upload builder), sky usage tracking,
  Oculus support (IrisCompat checks mod id "oculus" too; Iris API v0 is the same), IslandMobsPayload on the SimpleChannel
  (id 1, PLAY_TO_CLIENT, PacketDistributor.TRACKING_CHUNK), RenderFrameEvent -> TickEvent.RenderTickEvent (START).
- GlCacheSync: field names are SRG in production; fields found by shape (TextureState[] array, its int, the non-final
  static int). Only forgetTextures is kept.
- Shadered 1.20.1's AssimpRenderer.renderDirect sets ViewMat = identity (1.21.1 uses the camera turn): island-item cats
  and IslandCatBatch's one-by-one fallback pass full view-space matrices.
- CI: compiles, client reaches the title screen (sky tracking mixins log as active). Not tested in a world.

## Releases (user rule, 19 Oct 2026)
- Every time the user says they want to share/upload on CurseForge: bump mod_version in gradle.properties to the next
  version (patch +1, e.g. 1.0.1 -> 1.0.2) on BOTH branches (main = 1.21.1, 1.20.1), push, wait for CI and send the
  new jars plus the changelog. 1.0.1 = the first update (Sky Liquids, connected islands, shader pack support...).

## Portal skies (21 Oct 2026, user request)
- Right-click a nether portal / end portal / end gateway with a Shadered sky block: the whole connected portal shows
  that sky (block used up, not in creative); right-click with an empty main hand undoes it. Particles take the sky's
  colour. 1.21.1 first, then port to 1.20.1.
- portal/PortalSkies: SavedData per dimension (pos -> SkyblockType name), pruned every 40 ticks when the portal block is
  gone; PortalSkiesPayload (changes to the dimension, full list on login/dimension change/respawn).
- client/PortalSkyRenderer: AFTER_ENTITIES, quads 0.01 outside the portal's faces with Shadered's sky render type
  (covers the vanilla portal, which draws later); deferred to IrisIslandTarget with a shader pack.
  mixin/PortalParticlesMixin recolours animateTick's particles (colours from the sky pictures' colourful pixels).
- Modded portals (user: "the auto choice"): PortalSkies.kind() also takes subclasses of NetherPortalBlock / EndPortalBlock
  / EndGatewayBlock, and blocks whose id contains "portal" (not "frame") with a horizontal axis property (drawn as a
  sheet on their shape's thin axis). Flat modded portals (no axis, e.g. Twilight Forest twilight_portal: up face at 13/16 = its shape top) are FLOOR, drawn at
  the top of their shape. Particles: mixin into ClientLevel.doAnimateTick (notes the animating block) + ClientLevel.addParticle
  (recolours its particles), so any portal's particles change colour.
- 1.20.1 port (21 Oct 2026): SimpleChannel id 2 (PLAY_TO_CLIENT, DIMENSION/PLAYER distributors), SavedData load/save without
  registries, stage pose applied to the quads (1.20.1 keeps the view rotation there), amethyst chime instead of resonate.
