package rocks.minestom.worldgen.structure.processor;

public sealed interface PosRuleTest permits PosRuleTest.AlwaysTrueTest, PosRuleTest.AlwaysFalseTest {
    boolean test();

    final class AlwaysTrueTest implements PosRuleTest {
        public static final AlwaysTrueTest INSTANCE = new AlwaysTrueTest();

        private AlwaysTrueTest() {
        }

        @Override
        public boolean test() {
            return true;
        }
    }

    final class AlwaysFalseTest implements PosRuleTest {
        public static final AlwaysFalseTest INSTANCE = new AlwaysFalseTest();

        private AlwaysFalseTest() {
        }

        @Override
        public boolean test() {
            return false;
        }
    }
}
