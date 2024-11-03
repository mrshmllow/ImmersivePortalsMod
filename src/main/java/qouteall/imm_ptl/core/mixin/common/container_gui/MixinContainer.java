package qouteall.imm_ptl.core.mixin.common.container_gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@SuppressWarnings("ALL")
@Mixin(Container.class)
public interface MixinContainer {
    @WrapOperation(
        method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;I)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private static double wrapDistanceToSqr(
        Player player, double x, double y, double z, Operation<Double> operation,
        @Local BlockEntity blockEntity, @Local(argsOnly = true) int maxDistance
    ) {
        double dist = operation.call(player, x, y, z);
        
        if (dist > maxDistance) {
            BlockPos targetPos = blockEntity.getBlockPos();
            Level targetWorld = blockEntity.getLevel();
            if (BlockManipulationServer.validateReach(player, targetWorld, targetPos)) {
                return 0;
            }
            else {
                return 999.0;
            }
        }
        else {
            return dist;
        }
    }
    
}
