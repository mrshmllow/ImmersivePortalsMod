package qouteall.imm_ptl.core.api.example;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.WeakHashMap;

/**
 * <p>
 * Example code about how to use
 * </p>
 * <ul>
 *     <li>GUI Portal API</li>
 *     <li>Chunk Loading API</li>
 *     <li>Remote Procedure Call Utility API</li>
 * </ul>
 *
 * <p>
 *     To test this, use command /portal debug gui_portal minecraft:the_end 0 80 0
 * </p>
 * <p>It involves:</p>
 *
 * <ul>
 *     <li>The server sending the dimension and position to client</li>
 *     <li>The server adding an additional per-player chunk loader so that the chunks near that
 *     position is being generated and synchronized to that player's client</li>
 *     <li>Client opening the GUI portal screen and render the GUI portal.
 *     It controls the camera rotation transformation and camera position.</li>
 *     <li>If the client closes the screen, the server removes the additional chunk loader</li>
 * </ul>
 */
public class ExampleGuiPortalRendering {
    /**
     * The Framebuffer that the GUI portal is going to render onto
     */
    @Environment(EnvType.CLIENT)
    private static RenderTarget frameBuffer;
    
    /**
     * A weak hash map storing ChunkLoader objects for each players
     */
    private static final WeakHashMap<ServerPlayer, ChunkLoader>
        chunkLoaderMap = new WeakHashMap<>();
    
    /**
     * Remove the GUI portal chunk loader for a player
     */
    private static void removeChunkLoaderFor(ServerPlayer player) {
        ChunkLoader chunkLoader = chunkLoaderMap.remove(player);
        if (chunkLoader != null) {
            PortalAPI.removeChunkLoaderForPlayer(player, chunkLoader);
        }
    }
    
    public static void onCommandExecuted(ServerPlayer player, ServerLevel world, Vec3 pos) {
        removeChunkLoaderFor(player);
        
        ChunkLoader chunkLoader = new ChunkLoader(
            new DimensionalChunkPos(
                world.dimension(), new ChunkPos(BlockPos.containing(pos))
            ),
            8
        );
        
        // Add the per-player additional chunk loader
        PortalAPI.addChunkLoaderForPlayer(player, chunkLoader);
        chunkLoaderMap.put(player, chunkLoader);
        
        // Tell the client to open the screen
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.clientActivateExampleGuiPortal",
            world.dimension(),
            pos
        );
        /**{@link RemoteCallables#clientActivateExampleGuiPortal(ResourceKey, Vec3)}*/
    }
    
    public static class RemoteCallables {
        @Environment(EnvType.CLIENT)
        public static void clientActivateExampleGuiPortal(
            ResourceKey<Level> dimension,
            Vec3 position
        ) {
            if (frameBuffer == null) {
                // the framebuffer size doesn't matter here
                // because it will be automatically resized when rendering
                frameBuffer = new TextureTarget(2, 2, true);
            }
            
            Minecraft.getInstance().setScreen(new GuiPortalScreen(dimension, position));
        }
        
        public static void serverRemoveChunkLoader(ServerPlayer player) {
            removeChunkLoaderFor(player);
        }
    }
    
    @Environment(EnvType.CLIENT)
    public static class GuiPortalScreen extends Screen {
        
        private final ResourceKey<Level> viewingDimension;
        
        private final Vec3 viewingPosition;
        
        public GuiPortalScreen(ResourceKey<Level> viewingDimension, Vec3 viewingPosition) {
            super(Component.literal("GUI Portal Example"));
            this.viewingDimension = viewingDimension;
            this.viewingPosition = viewingPosition;
        }
        
        @Override
        public void onClose() {
            super.onClose();
            
            // Tell the server to remove the additional chunk loader
            McRemoteProcedureCall.tellServerToInvoke(
                "qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.serverRemoveChunkLoader"
            );
        }
        
        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            super.render(guiGraphics, mouseX, mouseY, delta);
            
            double t1 = CHelper.getSmoothCycles(503);
            double t2 = CHelper.getSmoothCycles(197);
            
            // Determine the camera transformation
            Matrix4f cameraTransformation = new Matrix4f();
            cameraTransformation.identity();
            cameraTransformation.mul(
                DQuaternion.rotationByDegrees(
                    new Vec3(1, 1, 1).normalize(),
                    t1 * 360
                ).toMatrix()
            );
            
            // Determine the camera position
            Vec3 cameraPosition = this.viewingPosition.add(
                new Vec3(Math.cos(t2 * 2 * Math.PI), 0, Math.sin(t2 * 2 * Math.PI)).scale(30)
            );
            
            // Create the world render info
            WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
                .setWorld(ClientWorldLoader.getWorld(viewingDimension))
                .setCameraPos(cameraPosition)
                .setCameraTransformation(cameraTransformation)
                .setOverwriteCameraTransformation(true) // do not apply camera transformation to existing player camera transformation
                .setDescription(null)
                .setRenderDistance(minecraft.options.getEffectiveRenderDistance())
                .setDoRenderHand(false)
                .setEnableViewBobbing(false)
                .setDoRenderSky(false)
                .setHasFog(false)
                .build();
            
            // Ask it to render the world into the framebuffer the next frame
            GuiPortalRendering.submitNextFrameRendering(worldRenderInfo, frameBuffer);
            
            // Draw the framebuffer
            int h = minecraft.getWindow().getHeight();
            int w = minecraft.getWindow().getWidth();
            MyRenderHelper.drawFramebufferWithBounds(
                frameBuffer,
                true, // enable alpha blend
                false, // don't modify alpha
                (int) (w * 0.2f), (int) (w * 0.8f),
                (int) (h * 0.2f), (int) (h * 0.8f)
            );
            
            guiGraphics.drawCenteredString(
                this.font, this.title, this.width / 2, 70, 16777215
            );
        }
        
        @Override
        public boolean isPauseScreen() {
            return false;
        }
        
        // close when E is pressed
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (super.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                this.onClose();
                return true;
            }
            
            return false;
        }
    }
}
