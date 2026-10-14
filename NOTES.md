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

## Shadered 1.3 version (branch 1.20.1-shadered-1.3, 14 Oct 2026)
- For Shadered 1.3.7 - 1.4.2 (mods.toml range [1.3.7,1.4.3)); compiled against 1.3.7. No SkyblockType system there,
  so: no six skies (their code, mixins, textures and lang removed), no sky block backgrounds in paintings, and sky
  blocks inside a painting's island are dropped from Shadered's queues (SkyblockRenderer.spaceData... static fields)
  instead of drawn in the window. Pedestals and the scanner preview show them normally.
- CI load test runs with Shadered 1.4.2: 1.3.7 crashes on Linux by itself (no libassimp.so; fixed in 1.4.2).
