package com.example.skyblocks.client.arcade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fight's state - everything a frame of it reads or changes - copied out (to rewind to), put back, and written out
 * as bytes (to check two cabinets are in the same fight, or to send one's fight to the other). Netplay's.
 * <p>
 * All of Showdown's fields but those only for drawing it (the camera, the shapes of the last picture, the move list
 * open, the link itself). The fighters, their pads, the dice, the lists - copied deep; the moves, the fighters' looks,
 * the stages - the same ones (they never change).
 */
final class Rewind {
    private Rewind() {}

    /** Not the fight: only for drawing it (or the link to the other cabinet). */
    private static final Set<String> SKIP = Set.of("guardShape", "quads", "lastFrame", "camU", "camV", "camD", "oCamU", "oCamD",
            "curBody", "curPose", "curLift", "pt", "cu", "cd", "csp", "horizon", "camH", "rTX", "rTZ", "depthBias", "mirrored", "picks",
            "frameId", "gridOf", "grid", "showList", "listTab", "listSel", "listTop", "netplay", "random", "sounds");
    /** Kept per fighter (their poses - which the hits are judged by - shown health, trails): only the two fighting
     *  (the select screen's are only drawn). */
    private static final Set<String> BY_FIGHTER = Set.of("poses", "shownHp", "trails");

    private static final Field[] FIELDS;
    private static final Field ME, FOE;

    static {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c : new Class<?>[] {Showdown.class, ArcadeGame.class}) {
            List<Field> own = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || SKIP.contains(f.getName())) continue;
                f.setAccessible(true);
                own.add(f);
            }
            own.sort(Comparator.comparing(Field::getName)); // (the same order on every cabinet)
            fields.addAll(own);
        }
        FIELDS = fields.toArray(new Field[0]);
        try {
            ME = Showdown.class.getDeclaredField("me");
            FOE = Showdown.class.getDeclaredField("foe");
            ME.setAccessible(true);
            FOE.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= copying =================

    /** The fight now, copied. */
    static Object[] capture(Showdown g) {
        try {
            IdentityHashMap<Object, Object> seen = new IdentityHashMap<>();
            Object me = ME.get(g), foe = FOE.get(g);
            Object[] out = new Object[FIELDS.length];
            for (int i = 0; i < FIELDS.length; i++) {
                Field f = FIELDS[i];
                Object v = f.get(g);
                if (BY_FIGHTER.contains(f.getName())) {
                    Map<?, ?> m = (Map<?, ?>) v;
                    out[i] = new Object[] {me == null ? null : copy(m.get(me), seen), foe == null ? null : copy(m.get(foe), seen)};
                } else {
                    out[i] = copy(v, seen);
                }
            }
            return out;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The fight put back as it was (the copy kept: it can be put back again). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void restore(Showdown g, Object[] snap) {
        try {
            IdentityHashMap<Object, Object> seen = new IdentityHashMap<>();
            Object oldMe = ME.get(g), oldFoe = FOE.get(g);
            for (int i = 0; i < FIELDS.length; i++) {
                Field f = FIELDS[i];
                if (BY_FIGHTER.contains(f.getName())) continue;
                Object v = copy(snap[i], seen);
                if (!Modifier.isFinal(f.getModifiers())) {
                    f.set(g, v);
                } else if (v instanceof List list) { // (a list it holds on to: refilled)
                    List live = (List) f.get(g);
                    live.clear();
                    live.addAll(list);
                } else if (v != null && v.getClass().isArray()) {
                    Object live = f.get(g);
                    System.arraycopy(v, 0, live, 0, Array.getLength(v));
                } else {
                    f.set(g, v);
                }
            }
            Object me = ME.get(g), foe = FOE.get(g);
            for (int i = 0; i < FIELDS.length; i++) {
                Field f = FIELDS[i];
                if (!BY_FIGHTER.contains(f.getName())) continue;
                Map live = (Map) f.get(g);
                live.remove(oldMe); // (theirs: as they were; the select screen's fighters: left as they are)
                live.remove(oldFoe);
                Object[] pair = (Object[]) snap[i];
                if (me != null && pair[0] != null) live.put(me, copy(pair[0], seen));
                if (foe != null && pair[1] != null) live.put(foe, copy(pair[1], seen));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean unchanging(Object o) {
        return o instanceof String || o instanceof Integer || o instanceof Float || o instanceof Long || o instanceof Double
                || o instanceof Boolean || o instanceof Short || o instanceof Byte || o instanceof Character || o instanceof Enum<?>
                || o instanceof Record || o instanceof Class<?>;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copy(Object o, IdentityHashMap<Object, Object> seen) throws IllegalAccessException {
        if (o == null || unchanging(o)) return o;
        Object done = seen.get(o);
        if (done != null) return done;
        Class<?> c = o.getClass();
        if (c.isArray()) {
            int n = Array.getLength(o);
            Object a = Array.newInstance(c.getComponentType(), n);
            seen.put(o, a);
            if (c.getComponentType().isPrimitive()) {
                System.arraycopy(o, 0, a, 0, n);
            } else {
                Object[] from = (Object[]) o, to = (Object[]) a;
                for (int i = 0; i < n; i++) to[i] = copy(from[i], seen);
            }
            return a;
        }
        if (c == ArrayList.class) {
            List from = (List) o;
            ArrayList to = new ArrayList(from.size());
            seen.put(o, to);
            for (Object e : from) to.add(copy(e, seen));
            return to;
        }
        if (c == HashSet.class || c == LinkedHashSet.class) {
            Set to = c == HashSet.class ? new HashSet() : new LinkedHashSet();
            seen.put(o, to);
            for (Object e : (Set) o) to.add(copy(e, seen));
            return to;
        }
        if (c == HashMap.class || c == IdentityHashMap.class) {
            Map to = c == HashMap.class ? new HashMap() : new IdentityHashMap();
            seen.put(o, to);
            for (Map.Entry e : ((Map<?, ?>) o).entrySet()) to.put(copy(e.getKey(), seen), copy(e.getValue(), seen));
            return to;
        }
        if (c.getName().startsWith("com.example.skyblocks.client.arcade.")) { // (a fighter, a pad, the dice)
            Object to = make(c);
            seen.put(o, to);
            for (Field f : fieldsOf(c)) f.set(to, copy(f.get(o), seen));
            return to;
        }
        throw new IllegalStateException("The fight holds something that can't be rewound: " + c.getName());
    }

    private static final Map<Class<?>, Field[]> FIELDS_OF = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> MAKERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A class's own fields (each settable, finals too), in name order. */
    private static Field[] fieldsOf(Class<?> c) {
        return FIELDS_OF.computeIfAbsent(c, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> x = k; x != null && x != Object.class; x = x.getSuperclass()) {
                for (Field f : x.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            out.sort(Comparator.comparing(Field::getName));
            return out.toArray(new Field[0]);
        });
    }

    /** A blank one (made with nothing - every field is set after). */
    private static Object make(Class<?> c) {
        Constructor<?> k = MAKERS.computeIfAbsent(c, x -> {
            Constructor<?> best = x.getDeclaredConstructors()[0];
            best.setAccessible(true);
            return best;
        });
        Class<?>[] types = k.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = blank(types[i]);
        }
        try {
            return k.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Nothing, as a value of this type (a mixed ?: would turn them all into doubles). */
    private static Object blank(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == long.class) return 0L;
        if (t == float.class) return 0F;
        if (t == double.class) return 0D;
        if (t == char.class) return (char) 0;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        return 0;
    }

    // ================= as bytes =================

    /** The fight's never-changing things (the moves, the fighters, the stages ...), numbered: written as their number. */
    private static final List<Object> CONSTANTS = new ArrayList<>();
    private static final IdentityHashMap<Object, Integer> CONSTANT_NO = new IdentityHashMap<>();

    static {
        List<Field> statics = new ArrayList<>();
        for (Field f : Showdown.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) statics.add(f);
        }
        statics.sort(Comparator.comparing(Field::getName));
        for (Field f : statics) {
            try {
                f.setAccessible(true);
                addConstant(f.get(null));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // (not one of the fight's)
            }
        }
    }

    private static void addConstant(Object v) throws IllegalAccessException {
        if (v instanceof Record r) {
            if (CONSTANT_NO.containsKey(r)) return;
            CONSTANT_NO.put(r, CONSTANTS.size());
            CONSTANTS.add(r);
            for (var comp : r.getClass().getRecordComponents()) { // (a move's follow-ups ...)
                try {
                    var acc = comp.getAccessor();
                    acc.setAccessible(true);
                    addConstant(acc.invoke(r));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else if (v instanceof Object[] arr && v.getClass().getComponentType() != Object.class) {
            for (Object e : arr) addConstant(e);
        }
    }

    private static final int NULL = 0, REF = 1, INT = 2, FLOAT = 3, LONG = 4, DOUBLE = 5, BOOL = 6, SHORT = 7, BYTE = 8, CHAR = 9, STRING = 10,
            ENUM = 11, CONSTANT = 12, PRIM_ARRAY = 13, ARRAY = 14, LIST = 15, SET = 16, LINKED_SET = 17, MAP = 18, OBJECT = 19;

    /** A copy of the fight as bytes: the same fight, the same bytes, on any cabinet. */
    static byte[] write(Object[] snap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            Writer w = new Writer(out);
            out.writeInt(snap.length);
            for (Object v : snap) w.value(v);
        } catch (IOException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** A number for the fight (two cabinets' the same: the same fight). */
    static int hash(Object[] snap) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(write(snap));
        return (int) crc.getValue();
    }

    private static final class Writer {
        final DataOutputStream out;
        final IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();

        Writer(DataOutputStream out) {
            this.out = out;
        }

        void value(Object o) throws IOException, IllegalAccessException {
            if (o == null) {
                out.writeByte(NULL);
            } else if (o instanceof Integer v) {
                out.writeByte(INT);
                out.writeInt(v);
            } else if (o instanceof Float v) {
                out.writeByte(FLOAT);
                out.writeInt(Float.floatToRawIntBits(v));
            } else if (o instanceof Long v) {
                out.writeByte(LONG);
                out.writeLong(v);
            } else if (o instanceof Double v) {
                out.writeByte(DOUBLE);
                out.writeLong(Double.doubleToRawLongBits(v));
            } else if (o instanceof Boolean v) {
                out.writeByte(BOOL);
                out.writeBoolean(v);
            } else if (o instanceof Short v) {
                out.writeByte(SHORT);
                out.writeShort(v);
            } else if (o instanceof Byte v) {
                out.writeByte(BYTE);
                out.writeByte(v);
            } else if (o instanceof Character v) {
                out.writeByte(CHAR);
                out.writeChar(v);
            } else if (o instanceof String v) {
                out.writeByte(STRING);
                out.writeUTF(v);
            } else if (o instanceof Enum<?> v) {
                out.writeByte(ENUM);
                out.writeUTF(v.getDeclaringClass().getName());
                out.writeShort(v.ordinal());
            } else if (o instanceof Record) {
                Integer n = CONSTANT_NO.get(o);
                if (n == null) throw new IllegalStateException("Not one of the fight's own: " + o.getClass().getName());
                out.writeByte(CONSTANT);
                out.writeShort(n);
            } else if (ids.containsKey(o)) {
                out.writeByte(REF);
                out.writeInt(ids.get(o));
            } else {
                ids.put(o, ids.size());
                Class<?> c = o.getClass();
                if (c.isArray() && c.getComponentType().isPrimitive()) {
                    out.writeByte(PRIM_ARRAY);
                    Class<?> t = c.getComponentType();
                    int n = Array.getLength(o);
                    out.writeUTF(t.getName());
                    out.writeInt(n);
                    if (t == int.class) for (int v : (int[]) o) out.writeInt(v);
                    else if (t == float.class) for (float v : (float[]) o) out.writeInt(Float.floatToRawIntBits(v));
                    else if (t == boolean.class) for (boolean v : (boolean[]) o) out.writeBoolean(v);
                    else if (t == byte.class) out.write((byte[]) o);
                    else if (t == long.class) for (long v : (long[]) o) out.writeLong(v);
                    else if (t == double.class) for (double v : (double[]) o) out.writeLong(Double.doubleToRawLongBits(v));
                    else if (t == short.class) for (short v : (short[]) o) out.writeShort(v);
                    else for (char v : (char[]) o) out.writeChar(v);
                } else if (c.isArray()) {
                    out.writeByte(ARRAY);
                    out.writeUTF(c.getComponentType().getName());
                    Object[] a = (Object[]) o;
                    out.writeInt(a.length);
                    for (Object e : a) value(e);
                } else if (c == ArrayList.class) {
                    out.writeByte(LIST);
                    List<?> l = (List<?>) o;
                    out.writeInt(l.size());
                    for (Object e : l) value(e);
                } else if (c == HashSet.class || c == LinkedHashSet.class) {
                    out.writeByte(c == HashSet.class ? SET : LINKED_SET);
                    Object[] es = ((Set<?>) o).toArray();
                    if (c == HashSet.class) sort(es); // (in order: however the set happens to be laid out)
                    out.writeInt(es.length);
                    for (Object e : es) value(e);
                } else if (c == HashMap.class) {
                    out.writeByte(MAP);
                    Object[] keys = ((Map<?, ?>) o).keySet().toArray();
                    sort(keys);
                    out.writeInt(keys.length);
                    for (Object k : keys) {
                        value(k);
                        value(((Map<?, ?>) o).get(k));
                    }
                } else if (c.getName().startsWith("com.example.skyblocks.client.arcade.")) {
                    out.writeByte(OBJECT);
                    out.writeUTF(c.getName());
                    for (Field f : fieldsOf(c)) value(f.get(o));
                } else {
                    throw new IllegalStateException("The fight holds something that can't be written: " + c.getName());
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void sort(Object[] es) {
            boolean comparable = true;
            for (Object e : es) comparable &= e instanceof Comparable;
            if (!comparable) throw new IllegalStateException("A set the fight holds can't be put in order");
            Arrays.sort(es, (a, b) -> ((Comparable) a).compareTo(b));
        }
    }

    /** Bytes (as write makes them) back into a copy of the fight. */
    static Object[] read(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Reader r = new Reader(in);
            Object[] snap = new Object[in.readInt()];
            if (snap.length != FIELDS.length) throw new IllegalStateException("Not this version's fight");
            for (int i = 0; i < snap.length; i++) snap[i] = r.value();
            return snap;
        } catch (IOException | ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Reader {
        final DataInputStream in;
        final List<Object> made = new ArrayList<>();

        Reader(DataInputStream in) {
            this.in = in;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Object value() throws IOException, ReflectiveOperationException {
            int tag = in.readByte();
            switch (tag) {
                case NULL: return null;
                case REF: return made.get(in.readInt());
                case INT: return in.readInt();
                case FLOAT: return Float.intBitsToFloat(in.readInt());
                case LONG: return in.readLong();
                case DOUBLE: return Double.longBitsToDouble(in.readLong());
                case BOOL: return in.readBoolean();
                case SHORT: return in.readShort();
                case BYTE: return in.readByte();
                case CHAR: return in.readChar();
                case STRING: return in.readUTF();
                case ENUM: {
                    Class<?> c = Class.forName(in.readUTF(), false, Rewind.class.getClassLoader());
                    return c.getEnumConstants()[in.readShort()];
                }
                case CONSTANT: return CONSTANTS.get(in.readShort());
                case PRIM_ARRAY: {
                    String t = in.readUTF();
                    int n = in.readInt();
                    Object a;
                    switch (t) {
                        case "int" -> {
                            int[] v = new int[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = in.readInt();
                            a = v;
                        }
                        case "float" -> {
                            float[] v = new float[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = Float.intBitsToFloat(in.readInt());
                            a = v;
                        }
                        case "boolean" -> {
                            boolean[] v = new boolean[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = in.readBoolean();
                            a = v;
                        }
                        case "byte" -> {
                            byte[] v = new byte[n];
                            made.add(v);
                            in.readFully(v);
                            a = v;
                        }
                        case "long" -> {
                            long[] v = new long[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = in.readLong();
                            a = v;
                        }
                        case "double" -> {
                            double[] v = new double[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = Double.longBitsToDouble(in.readLong());
                            a = v;
                        }
                        case "short" -> {
                            short[] v = new short[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = in.readShort();
                            a = v;
                        }
                        default -> {
                            char[] v = new char[n];
                            made.add(v);
                            for (int i = 0; i < n; i++) v[i] = in.readChar();
                            a = v;
                        }
                    }
                    return a;
                }
                case ARRAY: {
                    Class<?> t = Class.forName(in.readUTF(), false, Rewind.class.getClassLoader());
                    Object[] a = (Object[]) Array.newInstance(t, in.readInt());
                    made.add(a);
                    for (int i = 0; i < a.length; i++) a[i] = value();
                    return a;
                }
                case LIST: {
                    int n = in.readInt();
                    ArrayList l = new ArrayList(n);
                    made.add(l);
                    for (int i = 0; i < n; i++) l.add(value());
                    return l;
                }
                case SET:
                case LINKED_SET: {
                    Set s = tag == SET ? new HashSet() : new LinkedHashSet();
                    made.add(s);
                    int n = in.readInt();
                    for (int i = 0; i < n; i++) s.add(value());
                    return s;
                }
                case MAP: {
                    Map m = new HashMap();
                    made.add(m);
                    int n = in.readInt();
                    for (int i = 0; i < n; i++) {
                        Object k = value();
                        m.put(k, value());
                    }
                    return m;
                }
                case OBJECT: {
                    Class<?> c = Class.forName(in.readUTF(), false, Rewind.class.getClassLoader());
                    if (!c.getName().startsWith("com.example.skyblocks.client.arcade.")) throw new IllegalStateException("Not the fight's");
                    Object o = make(c);
                    made.add(o);
                    for (Field f : fieldsOf(c)) f.set(o, value());
                    return o;
                }
                default: throw new IllegalStateException("Bad fight bytes");
            }
        }
    }
}
