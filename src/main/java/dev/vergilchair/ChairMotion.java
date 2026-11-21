package dev.vergilchair;

import net.minecraft.util.Mth;

public final class ChairMotion {
    public static final float STANDING_HIP = 0.704F;
    public static final int SLUMP_TICKS = 20;
    public static final int SLUMP_SIGH_TICK = 8;
    public static final int SIT_UP_TICKS = 12;
    private static final int STARTLE_TICK = 4;
    private static final int SLIDE_TICK = 14;
    private static final int TIP_TICK = 21;
    private static final int DROP_TICK = 26;
    public static final int IMPACT_TICK = 31;
    private static final int SETTLE_TICK = IMPACT_TICK + 5;
    private static final int GET_UP_TICK = IMPACT_TICK + 12;
    private static final int PUSH_UP_TICK = IMPACT_TICK + 21;
    public static final int FALL_TICKS = IMPACT_TICK + 28;

    public enum Action {
        SIT, SLUMP, SIT_UP, FALL, FALL_FROM_SLUMP;

        public boolean falling() {
            return this == FALL || this == FALL_FROM_SLUMP;
        }
    }

    // Heights and offsets are blocks. Angles are degrees in the player model's axes.
    private static final Frame SIT = new Frame(.685F, -.034F, 2, 0, 0, 0, 1,
            limb(-21, 11), limb(-21, -11), limb(-75, 4), limb(-75, -4));
    private static final Frame FLINCH = new Frame(.734F, -.020F, -8, -3, -19, -4, .15F,
            limb(-67, 21), limb(-61, -20), limb(-65, 6), limb(-73, -7));
    private static final Frame RECOIL = new Frame(.676F, .055F, 13, 4, -24, 4, .05F,
            limb(-58, 19), limb(-59, -19), limb(-56, 7), limb(-53, -9));
    private static final Frame DROP_INTO_SEAT = new Frame(.652F, .115F, 29, -6, -23, 11, .05F,
            limb(-22, 18), limb(-27, -22), limb(-43, 6), limb(-48, -10));
    private static final Frame SLUMP = new Frame(.667F, .102F, 28, -4, -19, 9, .15F,
            limb(-19, 21), limb(-25, -24), limb(-46, 6), limb(-50, -10));

    private static final Frame STARTLE = new Frame(.729F, .006F, -4, 0, -14, 3, .12F,
            limb(-78, -8), limb(-72, 7), limb(-66, 5), limb(-70, -6));
    private static final Frame SLIDE = new Frame(.654F, .012F, -4, 0, 8, 0, .08F,
            limb(-68, -9), limb(-63, 8), limb(-49, 5), limb(-53, -6));
    private static final Frame TIP = new Frame(.632F, .023F, -7, -37, 10, -10, 0,
            limb(-66, 15), limb(-50, -24), limb(-51, 5), limb(-57, -12));
    private static final Frame DROP = new Frame(.527F, .029F, -6, -73, 15, -9, 0,
            limb(-62, -13), limb(-67, -18), limb(-24, 4), limb(-38, -13));
    private static final Frame IMPACT = new Frame(.395F, .010F, -4, -86, 9, -8, 0,
            limb(-58, -13), limb(-54, -18), limb(0, 3), limb(-22, -12));
    private static final Frame ON_FLOOR = new Frame(.387F, .010F, -3, -84, 6, -6, .08F,
            limb(-51, -13), limb(-43, -19), limb(1, 3), limb(-17, -10));
    private static final Frame PUSH_UP = new Frame(.660F, .020F, -18, -28, 12, -3, .35F,
            limb(-10, 7), limb(-31, -12), limb(-63, 1), limb(-61, -6));

    private static final Limb RIGHT_BRACE = limb(-10, 6);
    private static final Limb LEFT_BRACE = limb(-17, -7);
    private static final Frame STAND = new Frame(STANDING_HIP, 0, 0, 0, 0, 0, 1,
            limb(0, 0), limb(0, 0), limb(0, 0), limb(0, 0));

    private ChairMotion() {}

    public static Frame sample(Action action, float time, boolean left) {
        return switch (action) {
            case SIT -> SIT;
            case SLUMP -> slump(time);
            case SIT_UP -> blend(SLUMP, SIT, time, 0, SIT_UP_TICKS);
            case FALL -> fall(SIT, time);
            case FALL_FROM_SLUMP -> fall(left ? SLUMP.mirrored() : SLUMP, time);
        };
    }

    private static Frame slump(float time) {
        if (time < 3) return blend(SIT, FLINCH, time, 0, 3);
        if (time < 7) return blend(FLINCH, RECOIL, time, 3, 7);
        if (time < 15) return blend(RECOIL, DROP_INTO_SEAT, time, 7, 15);
        return blend(DROP_INTO_SEAT, SLUMP, time, 15, SLUMP_TICKS);
    }

    private static Frame fall(Frame startingPose, float time) {
        if (time < STARTLE_TICK) return blend(startingPose, STARTLE, time, 0, STARTLE_TICK);
        if (time < SLIDE_TICK) return blend(STARTLE, SLIDE, time, STARTLE_TICK, SLIDE_TICK);
        if (time < TIP_TICK) return blend(SLIDE, TIP, time, SLIDE_TICK, TIP_TICK);
        if (time < DROP_TICK) return blend(TIP, DROP, time, TIP_TICK, DROP_TICK);
        if (time < IMPACT_TICK) return blend(DROP, IMPACT, time, DROP_TICK, IMPACT_TICK);
        if (time < SETTLE_TICK) return blend(IMPACT, ON_FLOOR, time, IMPACT_TICK, SETTLE_TICK);
        if (time < GET_UP_TICK) return ON_FLOOR;
        if (time < PUSH_UP_TICK) {
            return blend(ON_FLOOR, PUSH_UP, time, GET_UP_TICK, PUSH_UP_TICK)
                    .lift(.17F * arc(ease(time, GET_UP_TICK, PUSH_UP_TICK)));
        }
        return blend(PUSH_UP, STAND, time, PUSH_UP_TICK, FALL_TICKS - 1)
                .lift(.055F * arc(turnToView(time)));
    }

    public static float slide(float time) {
        return ease(time, STARTLE_TICK, SLIDE_TICK);
    }

    public static float travel(float time) {
        return ease(time, SLIDE_TICK, IMPACT_TICK);
    }

    public static float turnToView(float time) {
        return ease(time, PUSH_UP_TICK, FALL_TICKS - 1);
    }

    private static float arc(float progress) {
        return 4.0F * progress * (1.0F - progress);
    }

    private static float ease(float time, int start, int end) {
        float t = Mth.clamp((time - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static Frame blend(Frame a, Frame b, float time, int start, int end) {
        if (time <= start) return a;
        if (time >= end) return b;
        float t = ease(time, start, end);
        return new Frame(
                Mth.lerp(t, a.hipHeight, b.hipHeight), Mth.lerp(t, a.forward, b.forward),
                Mth.lerp(t, a.pitch, b.pitch), Mth.lerp(t, a.roll, b.roll),
                Mth.lerp(t, a.headPitch, b.headPitch), Mth.lerp(t, a.headRoll, b.headRoll),
                Mth.lerp(t, a.look, b.look),
                a.rightArm.lerp(t, b.rightArm), a.leftArm.lerp(t, b.leftArm),
                a.rightLeg.lerp(t, b.rightLeg), a.leftLeg.lerp(t, b.leftLeg));
    }

    private static Limb limb(float pitch, float spread) {
        return new Limb(pitch, spread);
    }

    public record Limb(float pitch, float spread) {
        private Limb lerp(float t, Limb other) {
            return new Limb(Mth.lerp(t, pitch, other.pitch), Mth.lerp(t, spread, other.spread));
        }

        private Limb mirrored() {
            return new Limb(pitch, -spread);
        }
    }

    public record Frame(float hipHeight, float forward, float pitch, float roll,
                        float headPitch, float headRoll, float look,
                        Limb rightArm, Limb leftArm, Limb rightLeg, Limb leftLeg) {
        public Frame mirrored() {
            return new Frame(hipHeight, forward, pitch, -roll, headPitch, -headRoll, look,
                    leftArm.mirrored(), rightArm.mirrored(), leftLeg.mirrored(), rightLeg.mirrored());
        }

        public Frame forwardFall(float amount) {
            float bracing = Mth.clamp((Math.abs(roll) - 29) / 55.0F, 0.0F, 1.0F) * amount;
            return new Frame(hipHeight + .17F * arc(amount) - .075F * bracing, forward,
                    Mth.lerp(amount, pitch, roll * .97F), roll * (1.0F - amount), headPitch, headRoll, look,
                    rightArm.lerp(bracing, RIGHT_BRACE), leftArm.lerp(bracing, LEFT_BRACE),
                    new Limb(rightLeg.pitch + 19 * bracing, rightLeg.spread),
                    new Limb(leftLeg.pitch + 22 * bracing, leftLeg.spread));
        }

        private Frame lift(float height) {
            return new Frame(hipHeight + height, forward, pitch, roll, headPitch, headRoll, look,
                    rightArm, leftArm, rightLeg, leftLeg);
        }
    }
}
