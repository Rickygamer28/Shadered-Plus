package com.example.skyblocks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * The arcade's controls: for each game, the things you do in it (up, down, left, right, and its buttons - "Rotate",
 * "Left punch" ...), each with two keys and two controller buttons of your choice, kept in config/skyblocks-arcade-
 * controls.json. A game itself only ever hears each action's own key (its "canonical" key, the one it was written for):
 * whatever you press is turned into that. Controllers: any GLFW knows as a gamepad (Xbox, PlayStation, Switch Pro and
 * most others) - buttons, the D-pad, both sticks (as directions) and the triggers.
 */
public final class ArcadeControls {
    private ArcadeControls() {}

    // controller inputs: 0-14 the buttons (GLFW's gamepad order), 15-18 left stick up/down/left/right, 19-22 the right
    // stick's, 23 left trigger, 24 right trigger
    public static final int PAD_INPUTS = 25, NONE = -1;
    public static final int A = GLFW.GLFW_GAMEPAD_BUTTON_A, B = GLFW.GLFW_GAMEPAD_BUTTON_B, X = GLFW.GLFW_GAMEPAD_BUTTON_X,
            Y = GLFW.GLFW_GAMEPAD_BUTTON_Y, LB = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, RB = GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,
            BACK = GLFW.GLFW_GAMEPAD_BUTTON_BACK, START = GLFW.GLFW_GAMEPAD_BUTTON_START, DUP = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP,
            DRIGHT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, DDOWN = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, DLEFT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT,
            LS_UP = 15, LS_DOWN = 16, LS_LEFT = 17, LS_RIGHT = 18, RS_UP = 19, RS_DOWN = 20, RS_LEFT = 21, RS_RIGHT = 22, LT = 23, RT = 24;
    private static final String[] PAD_NAMES = {"A", "B", "X", "Y", "LB", "RB", "Back", "Start", "Guide", "L-Stick", "R-Stick",
            "D-Up", "D-Right", "D-Down", "D-Left", "LS Up", "LS Down", "LS Left", "LS Right", "RS Up", "RS Down", "RS Left", "RS Right", "LT", "RT"};

    /** One thing to do in a game: its name, the key the game reads for it, and its default keys and controller buttons. */
    public record Action(String name, int canonical, int[] keys, int[] pad) {}

    private static Action up() {
        return new Action("Up", GLFW.GLFW_KEY_UP, new int[] {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP}, new int[] {DUP, LS_UP});
    }

    private static Action down() {
        return new Action("Down", GLFW.GLFW_KEY_DOWN, new int[] {GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN}, new int[] {DDOWN, LS_DOWN});
    }

    private static Action left() {
        return new Action("Left", GLFW.GLFW_KEY_LEFT, new int[] {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT}, new int[] {DLEFT, LS_LEFT});
    }

    private static Action right() {
        return new Action("Right", GLFW.GLFW_KEY_RIGHT, new int[] {GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT}, new int[] {DRIGHT, LS_RIGHT});
    }

    private static Action button(String name, int key, int alt, int pad, int padAlt) {
        return new Action(name, key, new int[] {key, alt}, new int[] {pad, padAlt});
    }

    /** The games' ids (as the arcade knows them) and their actions. */
    public static final String[] IDS = {"snake", "breakout", "tetris", "whisker_maze", "pong", "frogger", "minesweeper", "showdown"};
    public static final Action[][] GAMES = {
            {up(), down(), left(), right()},
            {left(), right(), button("Launch", GLFW.GLFW_KEY_SPACE, NONE, A, B)},
            {left(), right(),
                    new Action("Rotate", GLFW.GLFW_KEY_X, new int[] {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP}, new int[] {A, DUP}),
                    new Action("Soft drop", GLFW.GLFW_KEY_DOWN, new int[] {GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN}, new int[] {DDOWN, LS_DOWN}),
                    button("Hard drop", GLFW.GLFW_KEY_SPACE, NONE, B, RB)},
            {up(), down(), left(), right()},
            {up(), down()},
            {up(), down(), left(), right()},
            {up(), down(), left(), right(), button("Reveal", GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, A, NONE),
                    button("Flag", GLFW.GLFW_KEY_F, NONE, X, Y)},
            {up(), down(), left(), right(), button("Left punch", GLFW.GLFW_KEY_U, NONE, X, NONE),
                    button("Right punch", GLFW.GLFW_KEY_I, NONE, Y, NONE), button("Left kick", GLFW.GLFW_KEY_J, NONE, A, NONE),
                    button("Right kick", GLFW.GLFW_KEY_K, NONE, B, NONE), button("Rage art", GLFW.GLFW_KEY_L, NONE, RB, RT),
                    button("Jump", GLFW.GLFW_KEY_SPACE, NONE, LB, LT), button("Move list", GLFW.GLFW_KEY_H, NONE, START, NONE),
                    button("Practice", GLFW.GLFW_KEY_P, NONE, NONE, NONE)}};

    /** Your choices: [game][action][slot] (two of each). */
    private static int[][][] keys, pads;
    private static boolean loaded;

    private static Path file() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("skyblocks-arcade-controls.json");
    }

    private static void ensure() {
        if (loaded) return;
        loaded = true;
        keys = new int[GAMES.length][][];
        pads = new int[GAMES.length][][];
        for (int g = 0; g < GAMES.length; g++) {
            keys[g] = new int[GAMES[g].length][];
            pads[g] = new int[GAMES[g].length][];
            for (int a = 0; a < GAMES[g].length; a++) reset(g, a);
        }
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f)) {
                JsonObject root = new Gson().fromJson(r, JsonObject.class);
                for (int g = 0; g < GAMES.length; g++) {
                    if (root == null || !root.has(IDS[g])) continue;
                    JsonObject game = root.getAsJsonObject(IDS[g]);
                    for (int a = 0; a < GAMES[g].length; a++) {
                        if (!game.has(GAMES[g][a].name())) continue;
                        JsonObject o = game.getAsJsonObject(GAMES[g][a].name());
                        keys[g][a] = two(o.getAsJsonArray("keys"), keys[g][a]);
                        pads[g][a] = two(o.getAsJsonArray("pad"), pads[g][a]);
                    }
                }
            }
        } catch (Exception e) {
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't read the arcade controls: {}", e.toString());
        }
    }

    private static int[] two(JsonArray a, int[] fallback) {
        if (a == null || a.size() < 2) return fallback;
        return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
    }

    public static void save() {
        ensure();
        JsonObject root = new JsonObject();
        for (int g = 0; g < GAMES.length; g++) {
            JsonObject game = new JsonObject();
            for (int a = 0; a < GAMES[g].length; a++) {
                JsonObject o = new JsonObject();
                JsonArray k = new JsonArray(), p = new JsonArray();
                for (int v : keys[g][a]) k.add(v);
                for (int v : pads[g][a]) p.add(v);
                o.add("keys", k);
                o.add("pad", p);
                game.add(GAMES[g][a].name(), o);
            }
            root.add(IDS[g], game);
        }
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(root, w);
            }
        } catch (Exception e) {
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't save the arcade controls: {}", e.toString());
        }
    }

    public static void reset(int game, int action) {
        Action a = GAMES[game][action];
        keys[game][action] = a.keys().clone();
        pads[game][action] = a.pad().clone();
    }

    public static void resetGame(int game) {
        ensure();
        for (int a = 0; a < GAMES[game].length; a++) reset(game, a);
        save();
    }

    public static int key(int game, int action, int slot) {
        ensure();
        return keys[game][action][slot];
    }

    public static int pad(int game, int action, int slot) {
        ensure();
        return pads[game][action][slot];
    }

    public static void setKey(int game, int action, int slot, int key) {
        ensure();
        keys[game][action][slot] = key;
        save();
    }

    public static void setPad(int game, int action, int slot, int input) {
        ensure();
        pads[game][action][slot] = input;
        save();
    }

    /** The game's own keys for whatever this key does in it (none: it does nothing there). */
    public static List<Integer> fromKey(int game, int key) {
        ensure();
        List<Integer> out = new ArrayList<>();
        if (game < 0 || game >= GAMES.length) return out;
        for (int a = 0; a < GAMES[game].length; a++) {
            if (keys[game][a][0] == key || keys[game][a][1] == key) out.add(GAMES[game][a].canonical());
        }
        return out;
    }

    /** The game's own keys for whatever this controller input does in it. */
    public static List<Integer> fromPad(int game, int input) {
        ensure();
        List<Integer> out = new ArrayList<>();
        if (game < 0 || game >= GAMES.length) return out;
        for (int a = 0; a < GAMES[game].length; a++) {
            if (pads[game][a][0] == input || pads[game][a][1] == input) out.add(GAMES[game][a].canonical());
        }
        return out;
    }

    public static String keyName(int key) {
        if (key == NONE) return "-";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    public static String padName(int input) {
        return input >= 0 && input < PAD_NAMES.length ? PAD_NAMES[input] : "-";
    }

    // ---- reading a controller ----

    /** What a controller has held down, by input (with a little give on the sticks so a direction doesn't flicker). */
    public static final class Pad {
        public final boolean[] down = new boolean[PAD_INPUTS];
        private final GLFWGamepadState state = GLFWGamepadState.create();
        public boolean connected;

        /** Reads the first connected gamepad (or {@code which}, if it's one); returns the inputs newly pressed and let go. */
        public void poll(List<Integer> pressed, List<Integer> released) {
            int jid = -1;
            for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
                if (GLFW.glfwJoystickPresent(j) && GLFW.glfwJoystickIsGamepad(j)) {
                    jid = j;
                    break;
                }
            }
            boolean[] now = new boolean[PAD_INPUTS];
            connected = jid >= 0 && GLFW.glfwGetGamepadState(jid, state);
            if (connected) {
                for (int b = 0; b <= GLFW.GLFW_GAMEPAD_BUTTON_LAST && b < 15; b++) now[b] = state.buttons(b) == GLFW.GLFW_PRESS;
                float lx = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), ly = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
                float rx = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), ry = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
                now[LS_UP] = stick(-ly, LS_UP);
                now[LS_DOWN] = stick(ly, LS_DOWN);
                now[LS_LEFT] = stick(-lx, LS_LEFT);
                now[LS_RIGHT] = stick(lx, LS_RIGHT);
                now[RS_UP] = stick(-ry, RS_UP);
                now[RS_DOWN] = stick(ry, RS_DOWN);
                now[RS_LEFT] = stick(-rx, RS_LEFT);
                now[RS_RIGHT] = stick(rx, RS_RIGHT);
                now[LT] = stick(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER), LT);
                now[RT] = stick(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER), RT);
            }
            for (int i = 0; i < PAD_INPUTS; i++) {
                if (now[i] && !down[i]) pressed.add(i);
                if (!now[i] && down[i]) released.add(i);
                down[i] = now[i];
            }
        }

        private boolean stick(float v, int input) {
            return down[input] ? v > 0.35F : v > 0.55F;
        }

        /** Lets go of everything (when the screen closes or the game changes). */
        public void clear(List<Integer> released) {
            for (int i = 0; i < PAD_INPUTS; i++) {
                if (down[i]) released.add(i);
                down[i] = false;
            }
        }
    }
}
