package com.example.skyblocks;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Where a frame sits in the cube its multiblock makes, once formed - a corner (low or high), an edge (along which
 * axis) or a face (facing which axis) - so it's drawn as that part of the whole: girders along the edges, the corners'
 * caps, panels on the faces. Corners also say which way in they face (quad: which of the four, seen from above).
 */
public enum FramePart implements StringRepresentable {
    NONE, CORNER_LOW, CORNER_HIGH, EDGE_X, EDGE_Y, EDGE_Z, FACE_X, FACE_Y, FACE_Z;

    public static final EnumProperty<FramePart> PART = EnumProperty.create("part", FramePart.class);
    public static final IntegerProperty QUAD = IntegerProperty.create("quad", 0, 3);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The state of a frame {@code (dx, dy, dz)} from the middle of a cube {@code r} out (formed), or plain (not). */
    public static BlockState shape(BlockState s, int dx, int dy, int dz, int r, boolean formed) {
        if (!s.hasProperty(PART)) {
            return s;
        }
        if (!formed || r <= 0) {
            return s.setValue(PART, NONE).setValue(QUAD, 0);
        }
        boolean ex = Math.abs(dx) == r, ey = Math.abs(dy) == r, ez = Math.abs(dz) == r;
        int n = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
        FramePart p = n == 3 ? (dy > 0 ? CORNER_HIGH : CORNER_LOW)
                : n == 2 ? (!ex ? EDGE_X : !ey ? EDGE_Y : EDGE_Z)
                : n == 1 ? (ex ? FACE_X : ey ? FACE_Y : FACE_Z) : NONE;
        // (its models face in along +x and +z, turned round for the others)
        int quad = dx < 0 ? (dz < 0 ? 0 : 3) : (dz < 0 ? 1 : 2);
        return s.setValue(PART, p).setValue(QUAD, n == 3 ? quad : 0);
    }
}
