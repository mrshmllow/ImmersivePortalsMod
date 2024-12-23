package qouteall.imm_ptl.peripheral.dim_stack;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DimStackManagement {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static DimStackInfo dimStackToApply = null;
    public static Map<ResourceKey<Level>, BlockState> bedrockReplacementMap = new HashMap<>();
    
    public static void init() {
        if (!isDimStackEnabled()) {
            return;
        }
        
        DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.register(server -> {
            if (dimStackToApply != null) {
                DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT.invoker()
                    .run(server, dimStackToApply);
            }
        });
    }
    
    private static boolean isDimStackEnabled() {
        return true;
    }
    
    // at that time, only overworld has been created, only overworld data can be read
    // it's going to generate overworld spawn chunks
    // make sure the bedrock replacement map for overworld is initialized in time
    public static void onServerEarlyInit(MinecraftServer server) {
        if (!isDimStackEnabled()) {
            return;
        }
        
        updateDimStackFromPresetInDedicatedServer(server);
        
        Map<ResourceKey<Level>, BlockState> bedrockReplacementMap = new HashMap<>();
        
        if (dimStackToApply != null) {
            for (DimStackEntry entry : dimStackToApply.entries) {
                bedrockReplacementMap.put(entry.getDimension(), DimStackInfo.parseBlockString(entry.bedrockReplacementStr));
            }
        }
        
        DimStackManagement.bedrockReplacementMap = bedrockReplacementMap;
    }
    
    public static void onServerCreatedWorlds(MinecraftServer server) {
        if (!isDimStackEnabled()) {
            return;
        }
        
        if (dimStackToApply != null) {
            dimStackToApply.apply(server);
            dimStackToApply = null;
        }
        else {
            updateBedrockReplacementFromStorage(server);
        }
    }
    
    private static void updateDimStackFromPresetInDedicatedServer(MinecraftServer server) {
        if (O_O.isDedicatedServer()) {
            DimStackInfo dimStackPreset = getDimStackPreset();
            
            if (dimStackPreset != null) {
                if (!hasDimStackPortal(server)) {
                    LOGGER.info("Applying dimension stack preset in dedicated server");
                    dimStackToApply = dimStackPreset;
                }
                else {
                    LOGGER.info("There are already dim stack portals, so the preset is not applied");
                }
            }
            else {
                LOGGER.info("The server has no dimension stack preset.");
            }
        }
    }
    
    private static void updateBedrockReplacementFromStorage(MinecraftServer server) {
        // do not mutate the old map to avoid data race
        Map<ResourceKey<Level>, BlockState> newMap = new HashMap<>();
        for (ServerLevel world : server.getAllLevels()) {
            BlockState replacement = GlobalPortalStorage.get(world).bedrockReplacement;
            newMap.put(world.dimension(), replacement);
            LOGGER.info(
                "Bedrock Replacement {} {}",
                world.dimension().location(),
                replacement != null ?
                    BuiltInRegistries.BLOCK.getKey(replacement.getBlock()) : "null"
            );
        }
        bedrockReplacementMap = newMap;
    }
    
    public static void replaceBedrock(ServerLevel world, ChunkAccess chunk) {
        if (!isDimStackEnabled()) {
            return;
        }
        
        if (bedrockReplacementMap == null) {
            LOGGER.error("Dimension Stack Bedrock Replacement Abnormal");
            return;
        }
        
        BlockState replacement = bedrockReplacementMap.get(world.dimension());
        
        if (replacement != null) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = chunk.getMinY(); y < chunk.getMaxY(); y++) {
                        mutable.set(x, y, z);
                        BlockState blockState = chunk.getBlockState(mutable);
                        if (blockState.getBlock() == Blocks.BEDROCK) {
                            chunk.setBlockState(
                                mutable,
                                replacement,
                                false
                            );
                        }
                    }
                }
            }
        }
    }
    
    private static Collection<ResourceKey<Level>> collectDimStackCandidateWhenServerRunning(
        MinecraftServer server
    ) {
        LinkedHashSet<ResourceKey<Level>> result = new LinkedHashSet<>(server.levelKeys());
        
        Collection<ResourceKey<Level>> extra =
            DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
                .invoker().getExtraDimensionKeys(
                    server.registryAccess(), server.getWorldData().worldGenOptions()
                );
        
        result.addAll(extra);
        
        return result;
    }
    
    public static void onDimensionStackCommandExecute(
        ServerPlayer player
    ) {
        List<String> dimIdList = collectDimStackCandidateWhenServerRunning(player.server)
            .stream().map(k -> k.location().toString()).toList();
        
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.clientOpenScreen",
            dimIdList
        );
    }
    
    public static class RemoteCallables {
        @Environment(EnvType.CLIENT)
        public static void clientOpenScreen(List<String> dimensions) {
            List<ResourceKey<Level>> dimensionList =
                dimensions.stream().map(Helper::dimIdToKey).collect(Collectors.toList());
            
            DimStackGuiController controller = new DimStackGuiController(
                null,
                () -> dimensionList,
                dimStackInfo -> {
                    if (dimStackInfo != null) {
                        McRemoteProcedureCall.tellServerToInvoke(
                            "qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.serverSetupDimStack",
                            dimStackInfo
                        );
                    }
                    else {
                        McRemoteProcedureCall.tellServerToInvoke(
                            "qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.serverRemoveDimStack"
                        );
                    }
                    Minecraft.getInstance().setScreen(null);
                }
            );
            controller.initializeAsDefault();
            Minecraft.getInstance().setScreen(controller.view);
        }
        
        public static void serverSetupDimStack(
            ServerPlayer player, DimStackInfo dimStackInfo
        ) {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                    "You don't have permission to change dimension stack"
                ));
                return;
            }
            
            if (!isDimStackEnabled()) {
                player.sendSystemMessage(Component.literal(
                    "Dimension stack feature is not enabled"
                ));
                return;
            }
            
            MinecraftServer server = player.getServer();
            
            updateDimStack(server, dimStackInfo);
            
            player.displayClientMessage(
                Component.translatable("imm_ptl.dim_stack_established"),
                false
            );
        }
        
        public static void serverRemoveDimStack(
            ServerPlayer player
        ) {
            if (!player.hasPermissions(2)) {
                Helper.err("one player without permission tries to change dimension stack");
                return;
            }
            
            MinecraftServer server = player.getServer();
            
            clearDimStackPortals(server);
            
            player.displayClientMessage(
                Component.translatable("imm_ptl.dim_stack_removed"),
                false
            );
            
            // on dedicated server, the preset should be consistent with the current dimension stack
            // because it will try to apply dimension stack preset when initializing the server
            if (O_O.isDedicatedServer()) {
                setDimStackPreset(null);
            }
        }
    }
    
    private static void updateDimStack(MinecraftServer server, DimStackInfo dimStackInfo) {
        DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT.invoker()
            .run(server, dimStackInfo);
        
        clearDimStackPortals(server);
        
        dimStackInfo.apply(server);
        
        // on dedicated server, the preset should be consistent with the current dimension stack
        // because it will try to apply dimension stack preset when initializing the server
        if (O_O.isDedicatedServer()) {
            setDimStackPreset(dimStackInfo);
        }
    }
    
    public static boolean hasDimStackPortal(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            GlobalPortalStorage gps = GlobalPortalStorage.get(world);
            if (Helper.indexOf(gps.data, p -> p instanceof VerticalConnectingPortal) != -1) {
                return true;
            }
        }
        return false;
    }
    
    private static void clearDimStackPortals(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            GlobalPortalStorage gps = GlobalPortalStorage.get(world);
            gps.data.removeIf(p -> p instanceof VerticalConnectingPortal);
            gps.bedrockReplacement = null;
            gps.onDataChanged();
        }
        
        updateBedrockReplacementFromStorage(server);
    }
    
    @Nullable
    public static DimStackInfo getDimStackPreset() {
        JsonObject json = IPConfig.getConfig().dimStackPreset;
        if (json == null) {
            return null;
        }
        
        try {
            DimStackInfo dimStackInfo = IPGlobal.gson.fromJson(json, DimStackInfo.class);
            return dimStackInfo;
        }
        catch (Exception e) {
            LOGGER.error("Cannot parse dimension stack preset JSON {}", json, e);
            return null;
        }
    }
    
    public static void setDimStackPreset(@Nullable DimStackInfo preset) {
        JsonObject json = null;
        if (preset != null) {
            json = IPGlobal.gson.toJsonTree(preset).getAsJsonObject();
        }
        IPConfig config = IPConfig.getConfig();
        config.dimStackPreset = json;
        config.saveConfigFile();
    }
}
