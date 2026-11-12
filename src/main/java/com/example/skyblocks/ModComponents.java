package com.example.skyblocks;

import com.example.skyblocks.island.IslandData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SkyBlocksMod.MOD_ID);

    /** The scanned island a Sky Island item holds. */
    public static final Supplier<DataComponentType<IslandData>> ISLAND = COMPONENTS.register("island",
            () -> DataComponentType.<IslandData>builder()
                    .persistent(IslandData.CODEC)
                    .networkSynchronized(IslandData.STREAM_CODEC)
                    .build());

    /** A trophy's engraving: who, at which arcade game, what score. */
    public record Trophy(String name, String game, int score) {
        public static final com.mojang.serialization.Codec<Trophy> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.STRING.fieldOf("name").forGetter(Trophy::name),
                com.mojang.serialization.Codec.STRING.fieldOf("game").forGetter(Trophy::game),
                com.mojang.serialization.Codec.INT.fieldOf("score").forGetter(Trophy::score)).apply(i, Trophy::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Trophy> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, Trophy::name,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, Trophy::game,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Trophy::score, Trophy::new);
    }

    public static final Supplier<DataComponentType<Trophy>> TROPHY = COMPONENTS.register("trophy",
            () -> DataComponentType.<Trophy>builder().persistent(Trophy.CODEC).networkSynchronized(Trophy.STREAM_CODEC).build());

    /** A photo (CameraItem): which picture (PhotoStore), who took it, on which day. */
    public record Photo(int id, String by, long day) {
        public static final com.mojang.serialization.Codec<Photo> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.INT.fieldOf("id").forGetter(Photo::id),
                com.mojang.serialization.Codec.STRING.fieldOf("by").forGetter(Photo::by),
                com.mojang.serialization.Codec.LONG.fieldOf("day").forGetter(Photo::day)).apply(i, Photo::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Photo> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Photo::id,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, Photo::by,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Photo::day, Photo::new);
    }

    public static final Supplier<DataComponentType<Photo>> PHOTO = COMPONENTS.register("photo",
            () -> DataComponentType.<Photo>builder().persistent(Photo.CODEC).networkSynchronized(Photo.STREAM_CODEC).build());

    /**
     * Shaders painted onto an item's texture at the infusion altar: for each pixel of its sprite (w x h, row by row from
     * the top), which ShaderPaint style (0: none).
     */
    public record ShaderPaint(int w, int h, byte[] pixels) {
        public static final com.mojang.serialization.Codec<ShaderPaint> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.INT.fieldOf("w").forGetter(ShaderPaint::w),
                com.mojang.serialization.Codec.INT.fieldOf("h").forGetter(ShaderPaint::h),
                com.mojang.serialization.Codec.BYTE_BUFFER.xmap(b -> {
                    byte[] a = new byte[b.remaining()];
                    b.duplicate().get(a);
                    return a;
                }, java.nio.ByteBuffer::wrap).fieldOf("pixels").forGetter(ShaderPaint::pixels)).apply(i, ShaderPaint::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, ShaderPaint> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ShaderPaint::w,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ShaderPaint::h,
                        net.minecraft.network.codec.ByteBufCodecs.byteArray(64 * 64), ShaderPaint::pixels, ShaderPaint::new);

        public int at(int x, int y) {
            int i = y * w + x;
            return i >= 0 && i < pixels.length ? pixels[i] & 255 : 0;
        }

        public boolean empty() {
            for (byte b : pixels) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ShaderPaint p && p.w == w && p.h == h && java.util.Arrays.equals(p.pixels, pixels);
        }

        @Override
        public int hashCode() {
            return (w * 31 + h) * 31 + java.util.Arrays.hashCode(pixels);
        }
    }

    public static final Supplier<DataComponentType<ShaderPaint>> SHADER_PAINT = COMPONENTS.register("shader_paint",
            () -> DataComponentType.<ShaderPaint>builder().persistent(ShaderPaint.CODEC).networkSynchronized(ShaderPaint.STREAM_CODEC).build());

    /**
     * An item's own texture, drawn at the infusion altar: its pixels (w x h, row by row from the top), 4 bytes each
     * (alpha, red, green, blue; alpha 0: no pixel). Drawn instead of the item's own (client/ShaderPaintRender).
     */
    public record ItemTexture(int w, int h, byte[] argb) {
        public static final com.mojang.serialization.Codec<ItemTexture> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.INT.fieldOf("w").forGetter(ItemTexture::w),
                com.mojang.serialization.Codec.INT.fieldOf("h").forGetter(ItemTexture::h),
                com.mojang.serialization.Codec.BYTE_BUFFER.xmap(b -> {
                    byte[] a = new byte[b.remaining()];
                    b.duplicate().get(a);
                    return a;
                }, java.nio.ByteBuffer::wrap).fieldOf("argb").forGetter(ItemTexture::argb)).apply(i, ItemTexture::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, ItemTexture> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ItemTexture::w,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ItemTexture::h,
                        net.minecraft.network.codec.ByteBufCodecs.byteArray(64 * 64 * 4), ItemTexture::argb, ItemTexture::new);

        public static ItemTexture of(int w, int h, int[] argb) {
            byte[] b = new byte[w * h * 4];
            for (int i = 0; i < w * h; i++) {
                b[i * 4] = (byte) (argb[i] >>> 24);
                b[i * 4 + 1] = (byte) (argb[i] >> 16);
                b[i * 4 + 2] = (byte) (argb[i] >> 8);
                b[i * 4 + 3] = (byte) argb[i];
            }
            return new ItemTexture(w, h, b);
        }

        public int at(int x, int y) {
            int i = (y * w + x) * 4;
            if (x < 0 || y < 0 || x >= w || y >= h || i + 3 >= argb.length) {
                return 0;
            }
            return (argb[i] & 255) << 24 | (argb[i + 1] & 255) << 16 | (argb[i + 2] & 255) << 8 | argb[i + 3] & 255;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ItemTexture t && t.w == w && t.h == h && java.util.Arrays.equals(t.argb, argb);
        }

        @Override
        public int hashCode() {
            return (w * 31 + h) * 31 + java.util.Arrays.hashCode(argb);
        }
    }

    public static final Supplier<DataComponentType<ItemTexture>> ITEM_TEXTURE = COMPONENTS.register("item_texture",
            () -> DataComponentType.<ItemTexture>builder().persistent(ItemTexture.CODEC).networkSynchronized(ItemTexture.STREAM_CODEC).build());

    private ModComponents() {}
}
