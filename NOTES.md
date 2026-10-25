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

## Releases (user rule, 19 Oct 2026)
- Every time the user says they want to share/upload on CurseForge: bump mod_version in gradle.properties to the next
  version (patch +1, e.g. 1.0.1 -> 1.0.2) on BOTH branches (main = 1.21.1, 1.20.1), push, wait for CI and send the
  new jars plus the changelog. 1.0.1 = the first update (Sky Liquids, connected islands, shader pack support...). 1.0.2 (23 Oct 2026) = Mirror, Infinity Mirror, Infinity Cube, portal skies, 16 Elden Ring skies, islands show infinity blocks/portal skies/paintings.

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

## Mirror (22 Oct 2026, user idea; 1.21.1 first)
- skyblocks:mirror, full block (8 glass + iron ingot -> 8), plain silver texture in the chunk mesh; NO tint on the
  reflection (user). client/MirrorRenderer at AFTER_CUTOUT_BLOCKS: walls = exposed mirror faces grouped by (facing,
  plane coord), scanned every 10 ticks (4 chunks); up to 4 nearest visible walls facing the camera each get a reflection:
  camera mirrored in the plane, view*reflection matrix (glFrontFace CW), Lengyel oblique projection (near plane = wall),
  sky (LevelRenderer.renderSky), chunk layers solid/cutoutMipped/cutout via LevelRendererInvoker.renderSectionLayer
  (Sodium overwrites it: draws its own lists = the main view's), entities within 48 blocks in front (you included);
  into a TextureTarget (full res for the nearest wall, half for others) sampled in screen space by mirror_surface faces drawn 0.002 in front of the block.
- Sodium: optional skyblocks.sodium.mixins.json, SodiumViewportMixin ORs the mirrors' frustums (last frame) into
  Viewport.isBoxVisible(Looser) so sections seen only in a mirror are in the main list; scheduleTerrainUpdate (reflection)
  when the number of mirror views changes. No translucent layer in reflections (vanilla/Sodium resort by camera).
- Shader packs: mirrors are skipped (block texture) for now; Iris attempt is the next step.

## Chaos sky (23 Oct 2026, user: "make the block work like the other shadered blocks")
- The separate Chaos Block (own block + renderers) became a Shadered sky: "chaos" is the last entry of SkyNames.NAMES,
  so it gets the sky block item (skyblocks:chaos_sky_block), Illusorite ores, Sky Liquid, Sky Emitter, portal skies,
  islands, the item renderer, like every sky. No recipe (sky rule). client/ChaosSky.Pass (a SkyboxRenderPass subclass)
  draws the chaos shader (shaders/core/chaos_sky.*, brandon_chaos.glsl with CHAOS_FULL_BRIGHT) into the sky's
  screen-sized picture instead of six pictures; it repeats SkyPassMixin's usage tracking itself (render is overridden).
  Credit tooltip via ItemTooltipEvent on the chaos sky block and bucket. No textures/environment/chaos folder and no
  tools/skies entry (not a picture sky). Old skyblocks:chaos_block blocks in test worlds disappear.
- Synthwave sky (23 Oct 2026, user: all extras, classic pink/purple/orange, moving grid): last entry of SkyNames,
  shaders/core/synthwave_sky.fsh (our own code), drawn like chaos by client/ShaderSky.Pass (ChaosSky renamed ShaderSky;
  shader_sky.vsh shared). Sun sets towards -X (west).
- Water Caustics (23 Oct 2026, user): skyblocks:water_caustics, animated textures from tools/make_water_caustics.py
  (water_caustics.png = moving water + light, water_caustics_overlay.png = blue tint + light, translucent). Right-click
  with a full solid block (no BE) puts it under the water (WaterCausticsBlockEntity.inside, item used up / given back,
  dropped on break); sneak + empty hand takes it out. client/WaterCausticsModel wraps the block model (ModelData):
  inside block's quads + the overlay model (block/water_caustics_overlay, 0.01 outside). A separate Pool Floor block was
  made and dropped again (user: right-click instead). Recipe: 4 light blue concrete + water bucket + prismarine crystals -> 4.
- Synthwave round 2 (23 Oct 2026, user: "do all reactions + jukebox distance, make it better all round"): city skyline
  east, palms north/south, ringed planet NNE, shooting stars, cloud stripes, far mountain layer, highway along the sun
  axis. client/MusicReactor: PlayStreamingSourceEvent (RECORDS/MUSIC) -> decodes the same .ogg (SoundBufferLibrary.getStream,
  background) into 16 log bands per 1024 samples (FFT), per-band 20-95th percentile normalised; follows play time
  (minus pause); weight = jukebox linear falloff (range = max(vol,1)*attenuationDistance) * volume settings; note blocks
  = kicks by pitch. Uniforms Music/Bass/Mid/High/Bars0-3 + history texture skyblocks:dynamic/music_history (128 steps
  at 24/s x 16 bands) -> floor heightfield raymarch (peaks beside the highway, flowing away), mountains/sun pulse, EQ ring.
- Water Caustics can hold a Shadered sky block too (user, 23 Oct): the BE keeps the item put in (ItemStack "held", old
  "inside" tag read too); a sky -> WaterCausticsRenderer queues the sky cube with SkyblockRenderer.getData(type) like
  Shadered's own BER (drawn AFTER_BLOCK_ENTITIES, before translucent), model gives only the translucent light overlay.
- Synthwave colour skies (23 Oct 2026, user "do all of them"): synthwave_outrun, synthwave_night (moon), synthwave_miami,
  darksynth, synthwave_terminal, vaporwave = same synthwave_sky shader, colours as uniforms set by
  client/SynthwavePalettes (classic = "synthwave"). Particle textures generated per palette.
- Dance Floor (23 Oct 2026, user): skyblocks:dance_floor (POWERED + ANIMATION 0..8 block state). Touching tiles = one
  floor (6-way flood fill); one animation across it in floor coordinates (client/DanceFloorRenderer, per-face shader
  dance_floor.fsh, 2x2 LED tiles per block, Iris overlay via InfinityMirrorRenderer.scoped, mirrors). Redstone on any
  tile: the floor cycles all animations (DanceFloorBlock.shown = (ANIMATION + gameTime/200) % 9), 1.5 s crossfade on
  the client; last signal off -> server stores the shown one. Right-click: next animation. Bass pulses the lights,
  animation 8 = equaliser (MusicReactor bands). Recipe: red dye, glass, blue dye / glowstone, redstone, glowstone /
  glass, glowstone, glass -> 8.
- Disco Ball (23 Oct 2026, user "a disco ball that actually works"): skyblocks:disco_ball (MODE 0 off / 1 white /
  2 coloured, right-click; light 12 when on). client/DiscoBallRenderer (BER, render bounds inflated 24): faceted ball
  (10x20 tiles, glints), 96 Fibonacci beams rotated by the spin, level.clip(VISUAL) each frame (glass passes; the ball's
  visual shape is empty), additive soft spot decals stretched by incidence + faint billboard beams. Spin speed and spot
  brightness follow MusicReactor bass. Item = flat generated icon.
- Party set 2 (23 Oct 2026; a Galaxy Floor was added and removed again the same evening): Fog Machine (client BE ticker puffs skyblocks:fog particles, ON toggled by
  right-click / new signal; FogMachineBlockEntity.ACTIVE -> disco beams 4.5x brighter within 20 blocks); Speaker (BER
  cone on a spring driven by MusicReactor bass, front texture has cut-out holes; SpeakerBlockEntity.CLIENT_SPEAKERS ->
  MusicReactor.weight: a jukebox within 24 blocks of a speaker counts as loud near the speaker, range 32); Aquarium
  (TransparentBlock, touching blocks = one tank, client/AquariumRenderer per-face trace in aquarium.fsh: ellipsoid fish
  x4 species, bubbles, sand + caustics, seaweed on back walls, frame on tank edges).
- Fog machine round 2 (23 Oct): model with feet/nozzle/handle (shape 2..14 x 0..9 + handle), sneak + right-click ->
  client/FogMachineScreen (width 1-32, depth 1-32, height 1-12, Area outline toggle) -> FogAreaPayload; area starts at
  the machine's front, centred; client ticker fills the area with floating fog + nozzle jet, particles thin out when
  leaving the area (FogParticleBounds.next handed to the particle). FogMachineRenderer draws the outline. Speaker model:
  metal corner protectors, raised front trim, badge. Galaxy Floor removed (user).
- 24 Oct 2026: aquarium fixes (shape as vec4 uniforms, Full for boxes; per-fish swim boxes from the CPU (placeFish);
  solid sand layer; surface highlight only via top faces (faceN), which was the seam line); 18 species (10 normal,
  8 ocean: struct Species body/tail/fin radii); Ocean Aquarium block (AquariumBlock(ocean=true), Ocean uniform,
  tanks only join the same kind; fewer, bigger fish; kelp + coral + rocks). Islands: animated block textures for
  aquarium/ocean_aquarium/dance_floor; fog machine BER draws soft fog layers when IslandRendering.inIsland.
  Speaker: dye = light colour, sneak + right-click = RGB mode (BE colour/rgb synced).
- Aquarium/dance floor in Sky Islands (24 Oct): AquariumRenderer/DanceFloorRenderer.build(BlockGetter, tiles) shared by
  the world scan and islandSet(IslandView); queueIsland (pedestals, drawn at AFTER_BLOCK_ENTITIES in scoped()) and
  drawInWindow (paintings), like InfinityMirrorRenderer. Fish = up to 8 ellipsoid parts per fish (fishPart: forked
  tails, peduncle, dorsal/pectoral/ventral fins, turtle flippers, jelly tentacles, manta head fins) with a bounding
  sphere pre-test; up to 32 fish (2.4 per block normal, 0.8 ocean); haze 0.3 normal / 0.22 ocean; round rocks.
- Aquarium round 4 (24 Oct): fish are ray-marched signed-distance shapes (fishSDF: the fishPart ellipsoids blended
  with smin, a teardrop back-body part, the rear bending in a travelling wave); 3D decorations on the floor
  (decoSDF: rock, shell, starfish, castle, coral, chest, anemone, vase; placed by placeDecor, Deco0-23 vec4s);
  no more dark sand patches (they read as shadows).
- Aquarium round 5 (24 Oct, "better fish models"): regular fish use fishBody: profile body (height/width outline
  along x per species, fishShape(kind): deepest point, bluntness, tail type, belly fin), bulging eyes, mouth notch,
  thin 2D-outline tail (fan/forked/shark/dolphin flukes), dorsal + anal fins, paddling pectorals; fin rays, scales,
  gill line, rim light, silver shine. Manta/turtle/jellyfish keep the part-based shape.
- The aquarium "compartments" bug (24 Oct): int(o + 0.5) in water() rounded a word with all 24 bits set (2^24-1) up
  to 2^24 = no bits. Fixed by int(o) (the floats hold exact whole numbers). Ocean sunfish added (kind 18).
- Aquarium fish colours/shapes redone from the user's photos (fishColour per species; fins tagged by q.z: 0 back,
  -1 under, 2 side, 3 angelfish filament). New species 19 basking shark (ocean, gaping mouth). Eyes sized per
  species (eyeScale) and painted where the bulge is. Sunfish ×1.9 size.
- Aquarium speed-up (25 Oct): fish motion (kind, school, size, swim path with exact derivatives, heading/bank,
  wave, beat, bend, jelly squeeze) is computed in Java once per frame per tank (AquariumRenderer.fishFrame, SPECIES
  table must match the shader's species()/fishShape().w) and uploaded as an RGBA32F texture FishTex (4 texels per
  fish). The Fish0-63 uniforms are gone. Each regular fish also gets a tight local box test before ray marching.
- Aquarium fish choice (25 Oct): AquariumRenderer.chooseFish - budget = water * (1.4 normal / 0.6 ocean), COST per
  kind, WEIGHT, CAP per kind scaled by 10/enabledKinds, tankNeeds(kind) minimum tank width/height (giants only in big
  tanks), max 48 normal / 20 ocean, at least 1. Sneak + right-click an aquarium: AquariumScreen toggles kinds;
  client/AquariumSettings saves config/skyblocks-aquarium.properties (client-only: fish are only drawn there).
