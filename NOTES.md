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
