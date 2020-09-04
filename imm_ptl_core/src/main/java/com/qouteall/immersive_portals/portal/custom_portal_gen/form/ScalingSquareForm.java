package com.qouteall.immersive_portals.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qouteall.immersive_portals.portal.custom_portal_gen.PortalGenInfo;
import com.qouteall.immersive_portals.portal.nether_portal.BlockPortalShape;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;

import java.util.function.Function;
import java.util.function.Predicate;

public class ScalingSquareForm extends NetherPortalLikeForm {
    public static final Codec<ScalingSquareForm> codec = RecordCodecBuilder.create(instance -> {
        return instance.group(
            Registry.BLOCK.fieldOf("from_frame_block").forGetter(o -> o.fromFrameBlock),
            Registry.BLOCK.fieldOf("area_block").forGetter(o -> o.areaBlock),
            Registry.BLOCK.fieldOf("to_frame_block").forGetter(o -> o.toFrameBlock),
            Codec.INT.fieldOf("from_length").forGetter(o -> o.fromLength),
            Codec.INT.fieldOf("to_length").forGetter(o -> o.toLength),
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound)
        ).apply(instance, instance.stable(ScalingSquareForm::new));
    });
    
    public final Block fromFrameBlock;
    public final Block areaBlock;
    public final Block toFrameBlock;
    public final int fromLength;
    public final int toLength;
    
    public ScalingSquareForm(
        Block fromFrameBlock, Block areaBlock, Block toFrameBlock,
        int fromLength, int toLength, boolean generateFrameIfNotFound
    ) {
        super(generateFrameIfNotFound);
        this.fromFrameBlock = fromFrameBlock;
        this.areaBlock = areaBlock;
        this.toFrameBlock = toFrameBlock;
        this.fromLength = fromLength;
        this.toLength = toLength;
    }
    
    @Override
    public Codec<? extends PortalGenForm> getCodec() {
        return codec;
    }
    
    @Override
    public PortalGenForm getReverse() {
        return new ScalingSquareForm(
            toFrameBlock, areaBlock, fromFrameBlock,
            toLength, fromLength, generateFrameIfNotFound
        );
    }
    
    @Override
    public boolean testThisSideShape(ServerWorld fromWorld, BlockPortalShape fromShape) {
        boolean isSquareShape = BlockPortalShape.isSquareShape(fromShape, fromLength);
        return isSquareShape;
    }
    
    @Override
    public Function<ChunkRegion, Function<BlockPos.Mutable, PortalGenInfo>> getFrameMatchingFunc(
        ServerWorld fromWorld, ServerWorld toWorld, BlockPortalShape fromShape
    ) {
        BlockPortalShape template = getTemplateToShape(fromShape);
        
        Predicate<BlockState> areaPredicate = getAreaPredicate();
        Predicate<BlockState> otherSideFramePredicate = getOtherSideFramePredicate();
        BlockPos.Mutable temp2 = new BlockPos.Mutable();
        return (region) -> (blockPos) -> {
            BlockPortalShape result = template.matchShapeWithMovedFirstFramePos(
                pos -> areaPredicate.test(region.getBlockState(pos)),
                pos -> otherSideFramePredicate.test(region.getBlockState(pos)),
                blockPos,
                temp2
            );
            if (result != null) {
                if (fromWorld != toWorld || template.anchor != result.anchor) {
                    return new PortalGenInfo(
                        fromWorld.getRegistryKey(),
                        toWorld.getRegistryKey(),
                        template, result,
                        null,
                        ((double) toLength) / fromLength
                    );
                }
            }
            return null;
        };
    }
    
    private BlockPortalShape getTemplateToShape(BlockPortalShape fromShape) {
        return BlockPortalShape.getSquareShapeTemplate(
            fromShape.axis,
            toLength
        );
    }
    
    @Override
    public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
        for (BlockPos blockPos : toShape.frameAreaWithCorner) {
            toWorld.setBlockState(blockPos, toFrameBlock.getDefaultState());
        }
    }
    
    @Override
    public BlockPortalShape getNewPortalPlacement(ServerWorld toWorld, BlockPos toPos, BlockPortalShape fromShape) {
        return super.getNewPortalPlacement(toWorld, toPos, getTemplateToShape(fromShape));
    }
    
    @Override
    public Predicate<BlockState> getOtherSideFramePredicate() {
        return s -> s.getBlock() == toFrameBlock;
    }
    
    @Override
    public Predicate<BlockState> getThisSideFramePredicate() {
        return s -> s.getBlock() == fromFrameBlock;
    }
    
    @Override
    public Predicate<BlockState> getAreaPredicate() {
        return s -> s.getBlock() == areaBlock;
    }
}
