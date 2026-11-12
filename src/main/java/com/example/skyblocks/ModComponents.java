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

    private ModComponents() {}
}
