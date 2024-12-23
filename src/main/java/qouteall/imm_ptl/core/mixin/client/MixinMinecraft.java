package qouteall.imm_ptl.core.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.miscellaneous.IPortalInitialScreen;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

import java.util.List;
import java.util.function.Function;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IEMinecraftClient {
    @Final
    @Shadow
    @Mutable
    private RenderTarget mainRenderTarget;
    
    @Shadow
    public Screen screen;
    
    @Mutable
    @Shadow
    @Final
    public LevelRenderer levelRenderer;
    
    @Shadow
    private static int fps;
    
    @Shadow
    @Nullable
    public ClientLevel level;
    
    @Mutable
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    
    @Shadow
    @Final
    private static Logger LOGGER;
    
    @Shadow private Thread gameThread;
    
    @WrapOperation(
        method = "Lnet/minecraft/client/Minecraft;run()V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"
        )
    )
    private Thread testMixinExtra(Operation<Thread> original) {
        LOGGER.info("[ImmPtl] MixinExtra is working!");
        return original.call();
    }
    
    /**
     * The whole process involving portal animation and teleportation:
     * - begin ticking
     * <p>
     * - tick entities:
     * - set last tick pos to current pos
     * - do collision calculation for movements, update current pos
     * - portal.animation.lastTickAnimatedState = thisTickAnimatedState, thisTickAnimatedState = null
     * - increase game time
     * - end ticking
     * - partialTick should be 0
     * - update portal animation (set thisTickAnimatedState as 1 tick later)
     * - manage teleportation (right after updating portal animation)
     * - rendering (interpolate between last tick pos and current pos)
     * Note: the camera position is always behind the current tick position
     * - partialTick should be 1
     * - loop
     */
    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickEntities()V"
        )
    )
    private void onBeforeTickingEntities(CallbackInfo ci) {
//        RenderStates.tickDelta = 1;
//        StableClientTimer.update(level.getGameTime(), RenderStates.tickDelta);
//        ClientPortalAnimationManagement.update();
//        IPCGlobal.clientTeleportationManager.manageTeleportation(true);
    }
    
    // this happens after ticking client world and entities
    @Inject(
        method = "Lnet/minecraft/client/Minecraft;tick()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onAfterClientTick(CallbackInfo ci) {
        Profiler.get().push("imm_ptl_client_tick");
        
        // including ticking remote worlds
        ClientWorldLoader.tick();
        
        RenderStates.setPartialTick(0);
        StableClientTimer.tick();
        StableClientTimer.update(level.getGameTime(), RenderStates.getPartialTick());
        ClientPortalAnimationManagement.tick(); // must be after remote world ticking
        ClientTeleportationManager.manageTeleportation(true);
        
        IPGlobal.POST_CLIENT_TICK_EVENT.invoker().run();
        
        Profiler.get().pop();
    }
    
    @Inject(
        method = "Lnet/minecraft/client/Minecraft;runTick(Z)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;fps:I",
            shift = At.Shift.AFTER
        )
    )
    private void onSnooperUpdate(boolean tick, CallbackInfo ci) {
        ClientPerformanceMonitor.updateEverySecond(fps);
    }
    
    @Inject(
        method = "Lnet/minecraft/client/Minecraft;updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
        at = @At("HEAD")
    )
    private void onSetWorld(ClientLevel clientLevel, CallbackInfo ci) {
        if (ClientWorldLoader.getIsInitialized()) {
            LOGGER.info("Client cleanup");
            IPCGlobal.CLIENT_CLEANUP_EVENT.invoker().run();
            
            if (clientLevel == null) {
                LOGGER.info("Client exit world");
                IPCGlobal.CLIENT_EXIT_EVENT.invoker().run();
            }
            
            ClientWorldLoader.cleanUp();
        }
        else {
            LOGGER.info("Client world updated but not counted as cleanup");
        }
    }
    
    //avoid messing up rendering states in fabulous
    @Inject(method = "Lnet/minecraft/client/Minecraft;useShaderTransparency()Z", at = @At("HEAD"), cancellable = true)
    private static void onIsFabulousGraphicsOrBetter(CallbackInfoReturnable<Boolean> cir) {
        if (WorldRenderInfo.isRendering()) {
            cir.setReturnValue(false);
        }
    }
    
    @Inject(
        method = "addInitialScreens",
        at = @At("RETURN")
    )
    private void onAddInitialScreens(List<Function<Runnable, Screen>> output, CallbackInfo ci) {
        IPConfig config = IPConfig.getConfig();
        if (!config.initialScreenShown) {
            output.add(IPortalInitialScreen::new);
        }
    }
    
    @Override
    public void ip_setFrameBuffer(RenderTarget buffer) {
        mainRenderTarget = buffer;
    }
    
    @Override
    public Screen ip_getCurrentScreen() {
        return screen;
    }
    
    @Override
    public void ip_setWorldRenderer(LevelRenderer r) {
        levelRenderer = r;
    }
    
    @Override
    public void ip_setRenderBuffers(RenderBuffers arg) {
        renderBuffers = arg;
    }
    
    @Override
    public Thread ip_getRunningThread() {
        return gameThread;
    }
}
