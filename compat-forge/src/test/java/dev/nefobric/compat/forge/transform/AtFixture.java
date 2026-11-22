package dev.nefobric.compat.forge.transform;

/** AccessTransformerTest 的目标类。 */
@SuppressWarnings("unused")
class AtFixture {
    private int counter;
    private final String name = "x";
    protected static final long CONSTANT = 1L;

    private AtFixture() {
    }

    private int secret(int a) {
        return a + counter;
    }

    private static void helper() {
    }

    public void open() {
    }

    private static final class Hidden {
        private Hidden() {
        }
    }
}
