# Shadered+ (NeoForge 1.21.1): an addon for Shadered

Adds six new skies to **Shadered** by Noodlegamer76: Jupiter, Cat, Twilight, Hell, Nebula and Golden Hour.
They are added as real Shadered sky types, so everything in Shadered that works with sky types works with them:
- Skyblock items (Jupiter Sky Block, ...) that place Shadered's own skyblock, drawn by Shadered's renderer.
- Shadered's filters (posterize, grayscale, ...) on those skyblocks.
- The **Sky Emitter**: right-click it with one of our skyblocks, like with Shadered's.
- **Illusorite ore** veins can hold our skies, Illusorite can roll them, and Shadered's creative tab lists the ores for them.
- Breaking, pick-block, saving and syncing go through Shadered's own code.

**Requires Shadered for 1.21.1** (built against 1.21.1-1.5.8, https://modrinth.com/mod/shadered) and everything
Shadered itself needs (GeckoLib, Sodium, ...). Our skyblock items are listed in Shadered's creative tab.
Our skies have no crafting recipes: like Shadered's own skies, you get them from Illusorite ore veins.

**Removing Shadered+ from a world:** Shadered can't load blocks whose sky type no longer exists. Break all
Shadered+ skies (blocks, ores, emitter settings) before removing the mod from a world.

## Models (like Shadered's Maxwell)
- **OIIA Cat** (`skyblocks:oiia_cat`): a 3D cat that faces you when placed. Give it a **redstone signal** and it turns
  into the curled-up "loaf" shape and spins and bobs (the original model's animation, about 4.8 s loop) for as long as the
  signal lasts; without a signal it is the standing cat, still. Inventory shows a flat icon, hands/item frames the model.
  Recipe (shaped, like Maxwell's): gray wool / light gray wool frame around a Diamond Block
  (top `G L G`, middle `L D L`, bottom `G G G`; G = Gray Wool, L = Light Gray Wool, D = Diamond Block).
  It casts a round shadow on the floor like mobs do (Minecraft's own shadow texture; follows the "Entity Shadows" setting,
  fades as the cat floats up while spinning).
- **Big OIIA Cat**: build a solid cube of OIIA cats, any size from 2x2x2 (8 cats) up to 23x23x23 (12,167 cats).
  Placing the last cat turns the cube into one big cat, as many times bigger as the cube is wide, facing the way that
  last cat faced (with a poof and a deep meow). The cats touching each other (by their sides) must form exactly the
  cube: extra cats stuck to it stop it from forming. The cat fills the cube like the small cat fills its block, so the
  blocks of the cube around the cat become air.
  It works the same as the small cat: a redstone signal on any of its blocks makes it spin, and it casts a shadow to
  scale. Breaking any part (with a pickaxe) breaks the whole cat and gives back all the cats used (N x N x N); nothing
  drops in creative. Pistons can't move it. It has no item of its own.
  How it works: `BigOiiaCatBlock` fills the blocks the cat covers. Each part points one step towards the bottom-centre
  part (`link`), whose block entity (`BigOiiaCatBlockEntity`) stores the size, facing and redstone power and is drawn
  by `OiiaCatRenderer` at that size. `edge_x/y/z` and `cut` give each part its piece of the cat's collision shape.
  `client/BigOiiaCatOutline` outlines the whole cat when you look at any part. Sizes: `MIN_SIZE`/`MAX_SIZE` in
  `BigOiiaCatBlock`.

How it works: Shadered's model loader reads every `models/complex/` file of every mod, so our models live in
`assets/skyblocks/models/complex/` (`oiia_cat.glb` and `oiia_cat_spin.glb`, converted from the original FBX in
`tools/models/oiia_cat/`, texture embedded and reduced to 1024x1024). The FBX has a blend shape (the loaf pose) that the
original animation switches fully on while spinning; Shadered can't blend shapes, so the loaf is the second model
(base positions + blend-shape offsets, normals recomputed) and the renderer swaps between the two. `OiiaCatRenderer` hands the model to Shadered's model renderer,
like Shadered's `MaxwellRenderer`. Shadered's animator only animates skeletons, and this model animates as a whole, so
`OiiaCatModel.animate` plays the file's keyframes (`OiiaCatAnimation`) itself.

To add another model: put the .glb in `models/complex/`, then copy the OIIA Cat block, block entity, renderers,
blockstate/models/loot/lang and adjust the scale. Models with a skeleton can use Shadered's `Animator` instead.

## Sky Islands (miniature copies of an area)
- **Island Scanner** (`skyblocks:island_scanner`, in Shadered's creative tab). Recipe: Glass x3 on top, Amethyst Shard /
  Spyglass / Amethyst Shard in the middle, Iron Ingot x3 at the bottom.
  Place it in the middle of the area you want and right-click it. The menu sets the **area** (3x3 up to 25x25, centred
  on the scanner), the **height above** (1-24 layers, starting at the scanner's own layer) and the **depth below**
  (0-24 layers under the block the scanner stands on). The **Area** button shows or hides the outline of the area in
  the world (white, with the ground layer in green); it stays on after closing the menu, until you turn it off.
  Shift-click - / + for bigger steps. **Scan** gives you a Sky Island item (a message says why if it can't scan).
  The scanner has its own model: a dark machine with a screen showing a little island on each side and a glowing
  amethyst lens on top; it gives off a little light. Above the lens floats a slowly turning **live preview** of the
  island a scan would make right now (follows the menu's settings as you change them, and updates every 2 seconds).
- **Sky Island** item: a snapshot of the area. Everything from the ground layer up is copied as it is; below it the
  island narrows into a rough rounded point, like a floating island. The original area is not changed.
  Mobs (and armor stands, item frames, paintings) in the area are copied too (up to 64). Tooltip: size and mob count.
- **Placing** it puts down a small pedestal with the island floating and gently bobbing above it as a miniature
  (2 blocks wide by default; right-click with an empty hand: 1, 2, 3 or 4 blocks wide). The mini mobs are
  look-alikes, not real mobs: they can't be hit, don't drop anything and can't be used to duplicate anything. Mobs that
  were standing on the ground wander around the island now and then and look around; flying and swimming ones stay where
  they were. Breaking the pedestal gives the item back, island included.
- How it's drawn: the island is a tiny stand-alone "level" (`client/island/IslandView`). Minecraft's own block and fluid
  renderers turn it into quads once (`IslandMesh`), with biome colours and light (sunlight, torches, lava...) worked
  out from the copy; every frame those quads go through the normal render buffers, the way falling blocks do. So
  see-through blocks (water, glass, ice) are depth-sorted, Fabulous graphics handles them, and shader packs (Iris)
  treat the island like falling blocks.
- Chests, beds, signs, banners, heads, lecterns, campfires, decorated pots, shulker boxes... are saved with what
  players normally see of them (sign text, banner patterns, head owners; never chest contents or other server-only
  data) and drawn by their normal renderers (`IslandBlockEntity`).
- Mobs are client-only entities drawn by their normal renderers (`IslandFigure`); water mobs are told they're in water
  so fish swim upright (`mixin/EntityAccessor`).
- Notes: block entities and mobs are frozen in their resting look (closed chests, still enchanting-table books, no
  beacon beams). Big islands send all their quads every frame, so many large islands in view at once cost some
  performance. Saved mob data is trimmed (inventories, trades, AI memory) to keep the item small.

## Build
Requires JDK 21 and internet access (Gradle downloads NeoForge, Shadered from Modrinth's maven, and GeckoLib).
    gradle wrapper --gradle-version 8.10.2
    ./gradlew build      # jar in build/libs/shadered-plus-1.0.0.jar

### Build without installing anything (GitHub Actions)
Push this folder to a GitHub repository. The workflow in .github/workflows/build.yml builds the mod;
open the Actions tab, click the latest run, and download the "shadered-plus-jar" artifact.

To build against another Shadered release, change `shadered_version` in gradle.properties.

## How it hooks into Shadered (mixins, skyblocks.mixins.json)
Shadered keeps its skies in two enums, `SkyblockType` and `SkyblockItemTypes`, plus code that branches on them.
- `SkyblockTypeMixin` / `SkyblockItemTypesMixin` add `SKYBLOCKS_<NAME>` constants to both enums while they are created,
  so `values()`, `valueOf()`, saving and every loop over sky types include our skies. (The `*Invoker` mixins give
  access to the enums' private constructors.)
- `SkyblockRegistryMixin` links each new sky type to its item type (used for drops, pick block, Sky Emitter).
- `SkyblockRendererMixin` (`getData`), `SkyblockEntityRendererMixin` (its switch over sky types) and
  `ModRenderTypesMixin` (`getSkyboxTextureId`, used by item render types) send our sky types to our own batch data
  and skybox textures.
- `client/ShaderedSkies` registers one `SkyboxRenderPass` per sky with Shadered's `ComplexPassRenderer`, the same way
  Shadered's `SkyblockRenderer` registers its own skies.
- `ModItems` registers one Shadered `SkyblockItem` per sky (placing Shadered's skyblock).

These mixins target Shadered 1.21.1-1.5.8. If a Shadered update changes those classes, the game stops at startup
with a mixin error, and the mixins need updating.

## Add your own sky
1. Put six square cube-map faces in `tools/skies/<name>_sky_block/px.png nx.png py.png ny.png pz.png nz.png`
   (+X east, -X west, +Y up, -Y down, +Z south, -Z north). From a 2:1 panorama (needs Python, numpy, Pillow):
       python tools/equirect_to_cubemap.py my_sky.png my_sky_block 1024
   then move the six files into that folder with the short names.
2. Add `<name>_sky_block` as a new last line of `tools/skies/order.txt`.
3. Run `python tools/build_shadered_faces.py`. It writes Shadered's skybox layout to
   `textures/environment/<name>/` (front/back/left/right/top/bottom, matching Shadered's default SkyboxTranslation).
4. Add `<name>` to `SkyNames.NAMES`, in the same position as in order.txt.
5. Add a 16x16 icon `textures/block/<name>_sky_block.png` (particle texture), an item model (copy one),
   lang entries `item.skyblocks.<name>_sky_block` and `skyblock_type.skyblocks.<name>` (no recipe: skies come from the ores).
   It then automatically becomes a Shadered sky type: skyblock, Sky Emitter, filters, Illusorite ore veins.

## Limitations
- Framed Blocks can't use skyblocks as camo (Shadered 1.21.1 draws them with block entities).
- Shader packs, Sodium and so on: whatever Shadered supports.

## Credits
Requires and builds on **Shadered** by Noodlegamer76 (https://github.com/Noodlegamer76/Shadered), licensed CC BY-NC 4.0
(master branch LICENSE.txt; the 1.21.1 branch's mod_license since commit 02e330c, 9 Oct 2026). Made with the author's
permission. Shadered+ is an unofficial addon. It patches Shadered's classes at runtime (mixins) and contains no copied
Shadered code or assets. If Shadered code or assets are copied in later: credit Noodlegamer76, link the license,
say what was changed, no commercial use, and keep the original artists' credits for any skybox images.

## Model credit
OIIA Cat: the "Oiiaioooooiai" cat model (file `OiiaioooooiaiFin.fbx`, texture `Muchkin2_BaseColor.png`) was supplied
by the user. Add its source, author and license here (most Sketchfab models require a credit line).

## Image credit
The Jupiter sky (tools/skies/jupiter_sky_block/) is a user-supplied cube map, as are the Cat panorama
(tools/source/cat_sky_block.png), the Twilight cube map and the Hell cube map. Add each source, author and license here
(some images, for example CC BY ones, require a credit line).

- Nebula: `01_teal_magenta_nebula` from a "SpaceSkyboxes" pack (Godot cube-map export, 512x512 faces). Source/author/license: to be added.
- Golden Hour: `02_golden_hour` from a "StylizedSkyboxes" pack (Godot cube-map export, 512x512 faces). Source/author/license: to be added.
