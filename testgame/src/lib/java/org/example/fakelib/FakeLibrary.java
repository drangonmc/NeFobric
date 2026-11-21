package org.example.fakelib;

/** 模拟 bundler 里的一个依赖库。 */
public final class FakeLibrary {
    private FakeLibrary() {
    }

    public static String hello() {
        return "fake-library-1.0";
    }
}
