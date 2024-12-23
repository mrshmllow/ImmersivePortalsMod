package qouteall.imm_ptl.core.portal;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Mesh2D;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PortalManipulation {
    // its inverse is itself
    public static final DQuaternion flipAxisW = DQuaternion.rotationByDegrees(
        new Vec3(0, 1, 0), 180
    ).fixFloatingPointErrorAccumulation();
    
    public static void setPortalTransformation(
        Portal portal,
        ResourceKey<Level> destDim,
        Vec3 destPos,
        @Nullable DQuaternion rotation,
        double scale
    ) {
        portal.setDestDim(destDim);
        portal.setDestination(destPos);
        portal.setRotation(rotation);
        portal.setScaling(scale);
        portal.updateCache();
    }
    
    public static void removeConnectedPortals(Portal portal, Consumer<Portal> removalInformer) {
        removeOverlappedPortals(
            portal.level(),
            portal.getOriginPos(),
            portal.getNormal().scale(-1),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
        ServerLevel toWorld = MiscHelper.getServer().getLevel(portal.getDestDim());
        removeOverlappedPortals(
            toWorld,
            portal.getDestPos(),
            portal.transformLocalVecNonScale(portal.getNormal().scale(-1)),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
        removeOverlappedPortals(
            toWorld,
            portal.getDestPos(),
            portal.transformLocalVecNonScale(portal.getNormal()),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
    }
    
    public static Portal completeBiWayPortal(Portal portal, EntityType<? extends Portal> entityType) {
        Portal newPortal = createReversePortal(portal, entityType);
        
        McHelper.spawnServerEntity(newPortal);
        
        return newPortal;
    }
    
    // can also be used in client
    public static <T extends Portal> T createReversePortal(Portal portal, EntityType<T> entityType) {
        Level world = portal.getDestinationWorld();
        
        T newPortal = entityType.create(world, EntitySpawnReason.LOAD);
        assert newPortal != null;
        newPortal.setDestDim(portal.level().dimension());
        newPortal.setPos(portal.getDestPos().x, portal.getDestPos().y, portal.getDestPos().z);
        newPortal.setDestination(portal.getOriginPos());
        newPortal.specificPlayerId = portal.specificPlayerId;
        
        newPortal.setWidth(portal.getWidth() * portal.getScaling());
        newPortal.setHeight(portal.getHeight() * portal.getScaling());
        newPortal.setThickness(portal.getThickness() * portal.getScaling());
        newPortal.setAxisW(portal.getAxisW().scale(-1));
        newPortal.setAxisH(portal.getAxisH());
        
        newPortal.setPortalShape(portal.getPortalShape().getReverse());
        
        if (portal.getRotation() != null) {
            rotatePortalBody(newPortal, portal.getRotation());
            
            newPortal.setRotation(portal.getRotation().getConjugated());
        }
        
        newPortal.setScaling(1.0 / portal.getScaling());
        
        copyAdditionalProperties(newPortal, portal);
        
        return newPortal;
    }
    
    public static void rotatePortalBody(Portal portal, DQuaternion rotation) {
        portal.setAxisW(rotation.rotate(portal.getAxisW()));
        portal.setAxisH(rotation.rotate(portal.getAxisH()));
    }
    
    public static Portal completeBiFacedPortal(Portal portal, EntityType<Portal> entityType) {
        Portal newPortal = createFlippedPortal(portal, entityType);
        
        McHelper.spawnServerEntity(newPortal);
        
        return newPortal;
    }
    
    public static <T extends Portal> T createFlippedPortal(Portal portal, EntityType<T> entityType) {
        Level world = portal.level();
        T newPortal = entityType.create(world, EntitySpawnReason.LOAD);
        assert newPortal != null;
        newPortal.setDestDim(portal.getDestDim());
        newPortal.setPos(portal.getX(), portal.getY(), portal.getZ());
        newPortal.setDestination(portal.getDestPos());
        newPortal.specificPlayerId = portal.specificPlayerId;
        
        newPortal.setWidth(portal.getWidth());
        newPortal.setHeight(portal.getHeight());
        newPortal.setThickness(portal.getThickness());
        newPortal.setAxisW(portal.getAxisW().scale(-1));
        newPortal.setAxisH(portal.getAxisH());
        
        newPortal.setPortalShape(portal.getPortalShape().getFlipped());
        
        newPortal.setRotation(portal.getRotation());
        
        newPortal.setScaling(portal.getScaling());
        
        copyAdditionalProperties(newPortal, portal);
        
        return newPortal;
    }
    
    //the new portal will not be added into world
    public static Portal copyPortal(Portal portal, EntityType<Portal> entityType) {
        Level world = portal.level();
        Portal newPortal = entityType.create(world, EntitySpawnReason.LOAD);
        newPortal.setDestDim(portal.getDestDim());
        newPortal.setPos(portal.getX(), portal.getY(), portal.getZ());
        newPortal.setDestination(portal.getDestPos());
        newPortal.specificPlayerId = portal.specificPlayerId;
        
        newPortal.setWidth(portal.getWidth());
        newPortal.setHeight(portal.getHeight());
        newPortal.setAxisW(portal.getAxisW());
        newPortal.setAxisH(portal.getAxisH());
        
        newPortal.setPortalShape(portal.getPortalShape().cloneIfNecessary());
        
        newPortal.setRotation(portal.getRotation());
        
        newPortal.setScaling(portal.getScaling());
        
        copyAdditionalProperties(newPortal, portal);
        
        return newPortal;
    }
    
    public static void completeBiWayBiFacedPortal(
        Portal portal, Consumer<Portal> removalInformer,
        Consumer<Portal> addingInformer, EntityType<Portal> entityType
    ) {
        removeOverlappedPortals(
            ((ServerLevel) portal.level()),
            portal.getOriginPos(),
            portal.getNormal().scale(-1),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
        
        Portal oppositeFacedPortal = completeBiFacedPortal(portal, entityType);
        removeOverlappedPortals(
            MiscHelper.getServer().getLevel(portal.getDestDim()),
            portal.getDestPos(),
            portal.transformLocalVecNonScale(portal.getNormal().scale(-1)),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
        
        Portal r1 = completeBiWayPortal(portal, entityType);
        removeOverlappedPortals(
            MiscHelper.getServer().getLevel(oppositeFacedPortal.getDestDim()),
            oppositeFacedPortal.getDestPos(),
            oppositeFacedPortal.transformLocalVecNonScale(oppositeFacedPortal.getNormal().scale(-1)),
            p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
            removalInformer
        );
        
        Portal r2 = completeBiWayPortal(oppositeFacedPortal, entityType);
        addingInformer.accept(oppositeFacedPortal);
        addingInformer.accept(r1);
        addingInformer.accept(r2);
    }
    
    public static void removeOverlappedPortals(
        Level world,
        Vec3 pos,
        Vec3 normal,
        Predicate<Portal> predicate,
        Consumer<Portal> informer
    ) {
        getPortalCluster(world, pos, normal, predicate).forEach(e -> {
            e.remove(Entity.RemovalReason.KILLED);
            informer.accept(e);
        });
    }
    
    public static List<Portal> getPortalCluster(
        Level world,
        Vec3 pos,
        Vec3 normal,
        Predicate<Portal> predicate
    ) {
        return McHelper.findEntitiesByBox(
            Portal.class,
            world,
            new AABB(
                pos.add(0.1, 0.1, 0.1),
                pos.subtract(0.1, 0.1, 0.1)
            ),
            IPGlobal.maxNormalPortalRadius,
            p -> p.getNormal().dot(normal) > 0.5 && predicate.test(p)
        );
    }
    
    public static <T extends Portal> T createOrthodoxPortal(
        EntityType<T> entityType,
        ServerLevel fromWorld, ServerLevel toWorld,
        Direction facing, AABB portalArea,
        Vec3 destination
    ) {
        T portal = entityType.create(fromWorld, EntitySpawnReason.LOAD);
        
        PortalAPI.setPortalOrthodoxShape(portal, facing, portalArea);
        
        portal.setDestination(destination);
        portal.setDestDim(toWorld.dimension());
        
        return portal;
    }
    
    public static void copyAdditionalProperties(Portal to, Portal from) {
        copyAdditionalProperties(to, from, true);
    }
    
    public static void copyAdditionalProperties(Portal to, Portal from, boolean includeSpecialProperties) {
        to.setTeleportable(from.isTeleportable());
        to.setTeleportChangesScale(from.isTeleportChangesScale());
        to.teleportChangesGravity = from.teleportChangesGravity;
        to.specificPlayerId = from.specificPlayerId;
        PortalExtension.get(to).motionAffinity = PortalExtension.get(from).motionAffinity;
        PortalExtension.get(to).adjustPositionAfterTeleport = PortalExtension.get(from).adjustPositionAfterTeleport;
        to.setCrossPortalCollisionEnabled(from.isCrossPortalCollisionEnabled());
        PortalExtension.get(to).bindCluster = PortalExtension.get(from).bindCluster;
        to.animation.defaultAnimation = from.animation.defaultAnimation.copy();
        to.setIsVisible(from.isVisible());
        
        if (includeSpecialProperties) {
            to.portalTag = from.portalTag;
            to.setCommandsOnTeleported(from.getCommandsOnTeleported());
        }
    }
    
    /**
     * It creates the old scale box which is formed by 6 portals for one side.
     * It's recommended to use the new box portal shape, except for the case of wrapping an existing region.
     */
    @Deprecated
    public static void createScaledBoxView(
        ServerLevel areaWorld, AABB area,
        ServerLevel boxWorld, Vec3 boxBottomCenter,
        double scale,
        boolean biWay,
        boolean teleportChangesScale,
        boolean outerFuseView,
        boolean outerRenderingMergable,
        boolean innerRenderingMergable,
        boolean hasCrossPortalCollision
    ) {
        Vec3 viewBoxSize = Helper.getBoxSize(area).scale(1.0 / scale);
        AABB viewBox = Helper.getBoxByBottomPosAndSize(boxBottomCenter, viewBoxSize);
        for (Direction direction : Direction.values()) {
            Portal portal = createOrthodoxPortal(
                Portal.ENTITY_TYPE,
                boxWorld, areaWorld,
                direction, Helper.getBoxSurface(viewBox, direction),
                Helper.getBoxSurface(area, direction).getCenter()
            );
            portal.setScaling(scale);
            portal.setTeleportChangesScale(teleportChangesScale);
            portal.setFuseView(outerFuseView);
            portal.renderingMergable = outerRenderingMergable;
            portal.setCrossPortalCollisionEnabled(hasCrossPortalCollision);
            portal.portalTag = "imm_ptl:scale_box";
            
            McHelper.spawnServerEntity(portal);
            
            if (biWay) {
                Portal reversePortal = createReversePortal(portal, Portal.ENTITY_TYPE);
                
                reversePortal.renderingMergable = innerRenderingMergable;
                
                McHelper.spawnServerEntity(reversePortal);
                
            }
        }
    }
    
    /**
     * Places a portal based on {@code entity}'s looking direction. Does not set the portal destination or add it to the
     * world, you will have to do that yourself.
     *
     * @param width  The width of the portal.
     * @param height The height of the portal.
     * @param entity The entity to place this portal as.
     * @return The placed portal, with no destination set.
     * @author LoganDark
     */
    public static Portal placePortal(double width, double height, Entity entity) {
        Vec3 playerLook = entity.getLookAngle();
        
        Tuple<BlockHitResult, List<Portal>> rayTrace =
            IPMcHelper.rayTrace(
                entity.level(),
                new ClipContext(
                    entity.getEyePosition(1.0f),
                    entity.getEyePosition(1.0f).add(playerLook.scale(100.0)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    entity
                ),
                true
            );
        
        BlockHitResult hitResult = rayTrace.getA();
        List<Portal> hitPortals = rayTrace.getB();
        
        if (IPMcHelper.hitResultIsMissedOrNull(hitResult)) {
            return null;
        }
        
        for (Portal hitPortal : hitPortals) {
            playerLook = hitPortal.transformLocalVecNonScale(playerLook);
        }
        
        Direction lookingDirection = Helper.getFacingExcludingAxis(
            playerLook,
            hitResult.getDirection().getAxis()
        );
        
        // this should never happen...
        if (lookingDirection == null) {
            return null;
        }
        
        Vec3 axisH = Vec3.atLowerCornerOf(hitResult.getDirection().getUnitVec3i());
        Vec3 axisW = axisH.cross(Vec3.atLowerCornerOf(lookingDirection.getOpposite().getUnitVec3i()));
        Vec3 pos = Vec3.atCenterOf(hitResult.getBlockPos())
            .add(axisH.scale(0.5 + height / 2));
        
        Level world = hitPortals.isEmpty()
            ? entity.level()
            : hitPortals.get(hitPortals.size() - 1).getDestinationWorld();
        
        Portal portal = new Portal(Portal.ENTITY_TYPE, world);
        
        portal.setPosRaw(pos.x, pos.y, pos.z);
        
        portal.setAxisW(axisW);
        portal.setAxisH(axisH);
        
        portal.setWidth(width);
        portal.setHeight(height);
        
        return portal;
    }
    
    public static DQuaternion getPortalOrientationQuaternion(
        Vec3 axisW, Vec3 axisH
    ) {
        Vec3 normal = axisW.cross(axisH);
        
        return DQuaternion.matrixToQuaternion(axisW, axisH, normal);
    }
    
    public static void setPortalOrientationQuaternion(
        Portal portal, DQuaternion quaternion
    ) {
        portal.setOrientationRotation(quaternion);
    }
    
    public static void adjustRotationToConnect(Portal portalA, Portal portalB) {
        DQuaternion a = PortalAPI.getPortalOrientationQuaternion(portalA);
        DQuaternion b = PortalAPI.getPortalOrientationQuaternion(portalB);
        
        DQuaternion delta = b.hamiltonProduct(a.getConjugated());
        
        DQuaternion flip = DQuaternion.rotationByDegrees(
            portalB.getAxisH(), 180
        );
        DQuaternion aRot = flip.hamiltonProduct(delta);
        
        portalA.setRotation(aRot);
        portalB.setRotation(aRot.getConjugated());
        
    }
    
    public static boolean isOtherSideBoxInside(AABB transformedBoundingBox, Portal renderingPortal) {
        boolean intersects = Arrays.stream(Helper.eightVerticesOf(transformedBoundingBox))
            .anyMatch(p -> renderingPortal.isOnDestinationSide(p, 0));
        return intersects;
    }
    
    @Nullable
    public static Portal findParallelPortal(Portal portal) {
        return Helper.getFirstNullable(McHelper.findEntitiesRough(
            Portal.class,
            portal.getDestinationWorld(),
            portal.getDestPos(),
            0,
            p1 -> p1.getOriginPos().subtract(portal.getDestPos()).lengthSqr() < 0.01 &&
                p1.getDestPos().subtract(portal.getOriginPos()).lengthSqr() < 0.01 &&
                p1.getNormal().dot(portal.getContentDirection()) < -0.9 &&
                p1.getContentDirection().dot(portal.getNormal()) < -0.9 &&
                !(p1 instanceof Mirror) &&
                p1 != portal
        ));
    }
    
    @Nullable
    public static Portal findReversePortal(Portal portal) {
        return Helper.getFirstNullable(McHelper.findEntitiesRough(
            Portal.class,
            portal.getDestinationWorld(),
            portal.getDestPos(),
            0,
            p1 -> Portal.isReversePortal(portal, p1)
        ));
    }
    
    @Nullable
    public static Portal findFlippedPortal(Portal portal) {
        return Helper.getFirstNullable(McHelper.findEntitiesRough(
            Portal.class,
            portal.getOriginWorld(),
            portal.getOriginPos(),
            0,
            p1 -> p1.getOriginPos().subtract(portal.getOriginPos()).lengthSqr() < 0.01 &&
                p1.getNormal().dot(portal.getNormal()) < -0.9 &&
                p1.getDestPos().distanceToSqr(portal.getDestPos()) < 0.01 &&
                !(p1 instanceof Mirror) &&
                p1 != portal
        ));
    }
    
    /**
     * Use {@link PortalUtils#raytracePortals(Level, Vec3, Vec3, boolean, Predicate)}
     */
    @Deprecated
    public static Optional<Pair<Portal, Vec3>> raytracePortals(
        Level world, Vec3 from, Vec3 to, boolean includeGlobalPortal
    ) {
        return PortalCommand.raytracePortals(world, from, to, includeGlobalPortal);
    }
    
    /**
     * Given this side orientation and other side orientation,
     * compute the rotation transformation of the portal.
     */
    public static DQuaternion computeDeltaTransformation(
        DQuaternion thisSideOrientation, DQuaternion otherSideOrientation
    ) {
        // otherSideOrientation * axis = rotation * thisSideOrientation * flipAxisW * axis
        // otherSideOrientation = rotation * thisSideOrientation * flipAxisW
        // rotation = otherSideOrientation * flipAxisW^-1 * thisSideOrientation^-1
        return otherSideOrientation
            .hamiltonProduct(flipAxisW)
            .hamiltonProduct(thisSideOrientation.getConjugated());
    }
    
    public static void makePortalRound(Portal portal, int triangleNum) {
        Mesh2D mesh2D = new Mesh2D();
        
        for (int i = 0; i < triangleNum; i++) {
            mesh2D.addTriangle(
                0, 0,
                Math.cos(Math.PI * 2 * ((double) i) / triangleNum),
                Math.sin(Math.PI * 2 * ((double) i) / triangleNum),
                Math.cos(Math.PI * 2 * ((double) i + 1) / triangleNum),
                Math.sin(Math.PI * 2 * ((double) i + 1) / triangleNum)
            );
        }
        
        portal.setPortalShape(new SpecialFlatPortalShape(mesh2D));
    }
}
