package qouteall.imm_ptl.core.portal.animation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

import javax.annotation.Nullable;

public class RotationAnimation implements PortalAnimationDriver {
    
    public static void init() {
        PortalAnimationDriver.registerDeserializer(
            new ResourceLocation("imm_ptl:rotation"),
            RotationAnimation::deserialize
        );
    }
    
    public final Vec3 initialPosition;
    public final DQuaternion initialOrientation;
    public final Vec3 rotationCenter;
    public final Vec3 rotationAxis;
    public final double degreesPerTick;
    public final long startGameTime;
    public final long endGameTime;
    @Nullable
    public final TimingFunction timingFunction;
    
    public RotationAnimation(
        Vec3 initialPosition, DQuaternion initialOrientation,
        Vec3 rotationCenter, Vec3 rotationAxis,
        double degreesPerTick, long startGameTime, long endGameTime,
        @Nullable TimingFunction timingFunction
    ) {
        this.initialPosition = initialPosition;
        this.initialOrientation = initialOrientation;
        this.rotationCenter = rotationCenter;
        this.rotationAxis = rotationAxis;
        this.degreesPerTick = degreesPerTick;
        this.startGameTime = startGameTime;
        this.endGameTime = endGameTime;
        this.timingFunction = timingFunction;
    }
    
    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        
        tag.putString("type", "imm_ptl:rotation");
        Helper.putVec3d(tag, "initialPosition", initialPosition);
        tag.put("initialOrientation", initialOrientation.toTag());
        Helper.putVec3d(tag, "rotationCenter", rotationCenter);
        Helper.putVec3d(tag, "rotationAxis", rotationAxis);
        tag.putDouble("degreesPerTick", degreesPerTick);
        tag.putLong("startGameTime", startGameTime);
        tag.putLong("endGameTime", endGameTime);
        if (timingFunction != null) {
            tag.putString("timingFunction", timingFunction.toString());
        }
        
        return tag;
    }
    
    private static RotationAnimation deserialize(CompoundTag tag) {
        Vec3 initialPosition = Helper.getVec3d(tag, "initialPosition");
        DQuaternion initialOrientation = DQuaternion.fromTag(tag.getCompound("initialOrientation"));
        Vec3 rotationCenter = Helper.getVec3d(tag, "rotationCenter");
        Vec3 rotationAxis = Helper.getVec3d(tag, "rotationAxis");
        double degreesPerTick = tag.getDouble("degreesPerTick");
        long startGameTime = tag.getLong("startGameTime");
        long endGameTime = tag.getLong("endGameTime");
        TimingFunction timingFunction = tag.contains("timingFunction") ?
            TimingFunction.fromString(tag.getString("timingFunction")) : null;
        return new RotationAnimation(
            initialPosition, initialOrientation, rotationCenter, rotationAxis,
            degreesPerTick, startGameTime, endGameTime,
            timingFunction
        );
    }
    
    @Override
    public AnimationResult getAnimationResult(long tickTime, float partialTicks, AnimationContext context) {
        double passedTicks = ((double) (tickTime - 1 - startGameTime)) + partialTicks;
        
        boolean ended = false;
        long durationTicks = endGameTime - startGameTime;
        if (passedTicks >= durationTicks) {
            ended = true;
            passedTicks = durationTicks;
        }
        
        if (timingFunction != null) {
            passedTicks = timingFunction.mapProgress(passedTicks / durationTicks) * durationTicks;
        }
        
        double angle = degreesPerTick * passedTicks;
        DQuaternion rotation = DQuaternion.rotationByDegrees(rotationAxis, angle);
        
        Vec3 vec = initialPosition.subtract(rotationCenter);
        Vec3 rotatedVec = rotation.rotate(vec);
        Vec3 offset = rotatedVec.subtract(vec);
        
        return new AnimationResult(
            new DeltaUnilateralPortalState(
                offset, rotation, null
            ),
            ended
        );
    }
    
    @Nullable
    @Override
    public DeltaUnilateralPortalState getEndingResult(long tickTime, AnimationContext context) {
        if (endGameTime == Long.MAX_VALUE) {
            // infinite animation, keep the current state when stopping
            return null;
        }
        else {
            return getAnimationResult(endGameTime, 0, context).delta();
        }
    }
    
    @Override
    public PortalAnimationDriver getFlippedVersion() {
        return new RotationAnimation(
            initialPosition,
            initialOrientation.hamiltonProduct(UnilateralPortalState.flipAxisH),
            rotationCenter,
            rotationAxis,
            degreesPerTick,
            startGameTime,
            endGameTime,
            timingFunction
        );
    }
    
    // generated by GitHub Copilot
    public static class Builder {
        public Vec3 initialPosition;
        public DQuaternion initialOrientation;
        public Vec3 rotationCenter;
        public Vec3 rotationAxis;
        public double degreesPerTick;
        public long startGameTime;
        public long endGameTime;
        public TimingFunction timingFunction;
        
        public RotationAnimation build() {
            return new RotationAnimation(
                initialPosition, initialOrientation, rotationCenter, rotationAxis,
                degreesPerTick, startGameTime, endGameTime,
                timingFunction
            );
        }
        
        public Builder setInitialPosition(Vec3 initialPosition) {
            this.initialPosition = initialPosition;
            return this;
        }
        
        public Builder setInitialOrientation(DQuaternion initialOrientation) {
            this.initialOrientation = initialOrientation;
            return this;
        }
        
        public Builder setRotationCenter(Vec3 rotationCenter) {
            this.rotationCenter = rotationCenter;
            return this;
        }
        
        public Builder setRotationAxis(Vec3 rotationAxis) {
            this.rotationAxis = rotationAxis;
            return this;
        }
        
        public Builder setDegreesPerTick(double degreesPerTick) {
            this.degreesPerTick = degreesPerTick;
            return this;
        }
        
        public Builder setStartGameTime(long startGameTime) {
            this.startGameTime = startGameTime;
            return this;
        }
        
        public Builder setEndGameTime(long endGameTime) {
            this.endGameTime = endGameTime;
            return this;
        }
        
        public Builder setTimingFunction(TimingFunction timingFunction) {
            this.timingFunction = timingFunction;
            return this;
        }
        
    }
    
}
