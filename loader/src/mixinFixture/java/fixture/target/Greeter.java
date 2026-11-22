package fixture.target;

/** Mixin 测试的目标类：由 NeFobricClassLoader 从测试 jar 里加载。 */
public class Greeter {
    private String name = "world";

    public String greet() {
        return "hello " + name;
    }

    public int count() {
        return 1;
    }

    public String join(String a, String b) {
        return concat(a, b);
    }

    private String concat(String a, String b) {
        return a + "+" + b;
    }
}
