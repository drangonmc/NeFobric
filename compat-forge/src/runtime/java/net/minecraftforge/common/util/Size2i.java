package net.minecraftforge.common.util;

/** 不可变的二维整数尺寸。 */
public class Size2i {
    public final int width;
    public final int height;

    public Size2i(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Size2i s && s.width == width && s.height == height;
    }

    @Override
    public int hashCode() {
        return 31 * width + height;
    }

    @Override
    public String toString() {
        return "Size2i{width=" + width + ", height=" + height + "}";
    }
}
