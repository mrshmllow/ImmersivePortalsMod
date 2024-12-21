package qouteall.imm_ptl.core.platform_specific.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerEntity_MA {
    @Inject(method = "changeDimension", at = @At("HEAD"))
    private void onChangeDimensionByVanilla(
        TeleportTransition teleportTransition, CallbackInfoReturnable<Entity> cir
    ) {
        ServerPlayer this_ = (ServerPlayer) (Object) this;
        onBeforeDimensionTravel(this_);
    }
    
    // update chunk visibility data
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"))
    private void onTeleported(
            ServerLevel serverLevel, double d, double e, double f, Set<Relative> set, float g, float h, boolean bl, CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayer this_ = (ServerPlayer) (Object) this;
        
        if (this_.level() != serverLevel) {
            onBeforeDimensionTravel(this_);
        }
    }
    
    private static void onBeforeDimensionTravel(ServerPlayer player) {
        CustomPortalGenManager customPortalGenManager =
            IPPerServerInfo.of(player.server).customPortalGenManager;
        
        if (customPortalGenManager != null) {
            customPortalGenManager.onBeforeConventionalDimensionChange(player);
            ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(player);
            
            ServerTaskList.of(player.server).addTask(() -> {
                customPortalGenManager.onAfterConventionalDimensionChange(player);
                return true;
            });
        }
    }
}
