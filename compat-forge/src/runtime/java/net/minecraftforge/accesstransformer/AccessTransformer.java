package net.minecraftforge.accesstransformer;

import org.objectweb.asm.Opcodes;

/**
 * 一条（合并后的）访问变换：目标、要放宽到的访问级别、对 final 的处理，以及来自哪些文件。
 * 合并规则和真正生效的那份一致：访问级别取最宽的，只放宽不收紧；一边去掉 final、一边加上 final 时按去掉处理。
 */
public class AccessTransformer {
    private final Target<?> memberTarget;
    private final Modifier targetAccess;
    private final FinalState targetFinalState;
    private final String origin;
    private final int lineNumber;

    public AccessTransformer(Target<?> target, Modifier modifier, FinalState finalState, String origin,
                             int lineNumber) {
        this.memberTarget = target;
        this.targetAccess = modifier;
        this.targetFinalState = finalState;
        this.origin = origin;
        this.lineNumber = lineNumber;
    }

    public Target<?> getTarget() {
        return memberTarget;
    }

    public Modifier getTargetAccess() {
        return targetAccess;
    }

    public FinalState getTargetFinalState() {
        return targetFinalState;
    }

    public String getOrigin() {
        return origin;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isValid() {
        return targetFinalState != FinalState.CONFLICT;
    }

    /** 和另一条指向同一目标的变换合并。 */
    public AccessTransformer mergeStates(AccessTransformer other, String otherOrigin) {
        Modifier m = targetAccess.ordinal() <= other.targetAccess.ordinal() ? targetAccess : other.targetAccess;
        return new AccessTransformer(memberTarget, m, targetFinalState.merge(other.targetFinalState),
                origin + "," + otherOrigin, lineNumber);
    }

    @Override
    public String toString() {
        return "AccessTransformer{" + memberTarget + " " + targetAccess + " " + targetFinalState + " from "
                + origin + ":" + lineNumber + "}";
    }

    /** 访问级别，按从宽到窄排列。 */
    public enum Modifier {
        PUBLIC(Opcodes.ACC_PUBLIC), PROTECTED(Opcodes.ACC_PROTECTED), DEFAULT(0), PRIVATE(Opcodes.ACC_PRIVATE);

        private static final int MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        private final int flag;

        Modifier(int flag) {
            this.flag = flag;
        }

        /** 放宽到这个级别；原来更宽时不变。 */
        public int mergeWith(int access) {
            return ordinal() < of(access).ordinal() ? (access & ~MASK) | flag : access;
        }

        static Modifier of(int access) {
            if ((access & Opcodes.ACC_PUBLIC) != 0) {
                return PUBLIC;
            } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
                return PROTECTED;
            } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
                return PRIVATE;
            }
            return DEFAULT;
        }
    }

    /** 对 final 的处理。 */
    public enum FinalState {
        LEAVE, MAKEFINAL, REMOVEFINAL, CONFLICT;

        public int mergeWith(int access) {
            return switch (this) {
                case MAKEFINAL -> access | Opcodes.ACC_FINAL;
                case REMOVEFINAL -> access & ~Opcodes.ACC_FINAL;
                default -> access;
            };
        }

        FinalState merge(FinalState other) {
            if (this == other || other == LEAVE) {
                return this;
            }
            if (this == LEAVE) {
                return other;
            }
            return REMOVEFINAL;
        }
    }
}
