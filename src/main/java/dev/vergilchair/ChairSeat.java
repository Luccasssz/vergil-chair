package dev.vergilchair;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ChairSeat extends Entity {
    private static final ChairMotion.Action[] ACTIONS = ChairMotion.Action.values();
    private static final EntityDataAccessor<Direction> FACING = data(EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Byte> ACTION = data(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> STARTED_AT = data(EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Direction> FALL_DIRECTION = data(EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Float> FALL_FLOOR = data(EntityDataSerializers.FLOAT);
    private static final double SLIDE_DISTANCE = 0.65;
    private static final double SIDE_LANDING_ADVANCE = 0.82;
    private static final double FALL_DISTANCE = 1.27;

    private Vec3 mountingPosition = Vec3.ZERO;

    public ChairSeat(EntityType<? extends ChairSeat> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    private static <T> EntityDataAccessor<T> data(EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(ChairSeat.class, serializer);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACING, Direction.NORTH);
        builder.define(ACTION, (byte) ChairMotion.Action.SIT.ordinal());
        builder.define(STARTED_AT, 0L);
        builder.define(FALL_DIRECTION, Direction.EAST);
        builder.define(FALL_FLOOR, 0.0F);
    }

    public void place(BlockPos pos, Direction facing, Vec3 mountingPosition) {
        Vec3 anchor = Vec3.atBottomCenterOf(pos);
        setPos(anchor.x, anchor.y, anchor.z);
        entityData.set(FACING, facing);
        this.mountingPosition = mountingPosition;
    }

    public Direction facing() {
        return entityData.get(FACING);
    }

    public float ridingYaw(float vanillaYaw, float partialTick) {
        float facingYaw = facing().toYRot();
        return action().falling()
                ? Mth.rotLerp(ChairMotion.turnToView(time(partialTick)), facingYaw, vanillaYaw)
                : facingYaw;
    }

    private ChairMotion.Action action() {
        return ACTIONS[entityData.get(ACTION)];
    }

    private float time(float partialTick) {
        return Math.max(0, level().getGameTime() - entityData.get(STARTED_AT) + partialTick - 1.0F);
    }

    public ChairMotion.Frame frame(float partialTick) {
        ChairMotion.Action action = action();
        float time = time(partialTick);
        if (action.falling()) {
            Direction exit = entityData.get(FALL_DIRECTION);
            boolean left = exit == facing().getCounterClockWise();
            ChairMotion.Frame pose = ChairMotion.sample(action, time, left);
            // Turn the tilt into a forward pitch once the player starts slipping.
            if (exit == facing()) {
                return pose.forwardFall(ChairMotion.travel(time));
            }
            return left ? pose.mirrored() : pose;
        }
        return ChairMotion.sample(action, time, false);
    }

    public void onAction(ServerPlayer player, boolean fall) {
        ChairMotion.Action current = action();
        float time = time(1.0F);
        if (current.falling() || !player.isAlive() || !hasPassenger(player)
                || current == ChairMotion.Action.SLUMP && time < ChairMotion.SLUMP_TICKS
                || current == ChairMotion.Action.SIT_UP && time < ChairMotion.SIT_UP_TICKS) {
            return;
        }
        boolean slumped = current == ChairMotion.Action.SLUMP;
        if (!fall) {
            begin(slumped ? ChairMotion.Action.SIT_UP : ChairMotion.Action.SLUMP);
            return;
        }
        Direction[] exits = { facing().getClockWise(), facing().getCounterClockWise(), facing() };
        for (Direction exit : exits) {
            Vec3 target = fallTarget(exit, getY());
            Vec3 floor = safeStand(player, BlockPos.containing(target));
            if (floor == null || Math.abs(floor.y - getY()) > 0.6) {
                continue;
            }
            target = new Vec3(target.x, floor.y, target.z);
            if (clearLanding(player, exit, target) && clearApproach(player, exit, target)) {
                entityData.set(FALL_DIRECTION, exit);
                entityData.set(FALL_FLOOR, (float) (target.y - getY()));
                begin(slumped ? ChairMotion.Action.FALL_FROM_SLUMP : ChairMotion.Action.FALL);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.vergil_chair.no_landing"), true);
    }

    private void begin(ChairMotion.Action next) {
        entityData.set(ACTION, (byte) next.ordinal());
        entityData.set(STARTED_AT, level().getGameTime());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof ServerPlayer && !isVehicle();
    }

    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        Vec3 anchor = position();
        if (action().falling()) {
            float time = time(1.0F);
            Direction exit = entityData.get(FALL_DIRECTION);
            Vec3 target = fallTarget(exit, getY() + entityData.get(FALL_FLOOR));
            anchor = anchor.lerp(slideEnd(), ChairMotion.slide(time)).lerp(target, ChairMotion.travel(time));
        }
        move.accept(rider, anchor.x, anchor.y, anchor.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            discard();
            return;
        }
        BlockState state = level().getBlockState(blockPosition());
        if (!player.isAlive() || !state.is(VergilChair.CHAIR.get())
                || state.getValue(ChairBlock.HALF) != DoubleBlockHalf.LOWER || state.getValue(ChairBlock.FACING) != facing()) {
            release(player);
            return;
        }
        ChairMotion.Action current = action();
        float elapsed = time(1.0F);
        if (current == ChairMotion.Action.SLUMP && elapsed == ChairMotion.SLUMP_SIGH_TICK) {
            level().playSound(null, getX(), getY() + 1.3, getZ(), VergilChair.SLUMP_SIGH.get(),
                    SoundSource.PLAYERS, 0.75F, 1.0F);
        } else if (current.falling()) {
            Direction exit = entityData.get(FALL_DIRECTION);
            Vec3 target = fallTarget(exit, getY() + entityData.get(FALL_FLOOR));
            if (elapsed >= ChairMotion.FALL_TICKS || !clearLanding(player, exit, target)
                    || elapsed < ChairMotion.IMPACT_TICK && !clearApproach(player, exit, target)) {
                release(player);
            } else if (elapsed == ChairMotion.IMPACT_TICK) {
                level().playSound(null, target.x, target.y, target.z, SoundEvents.PLAYER_SMALL_FALL,
                        SoundSource.PLAYERS, 0.5F, 0.95F);
            }
        }
    }

    private void release(ServerPlayer player) {
        player.stopRiding();
        if (!hasPassenger(player)) {
            discard();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        if (action().falling() && time(1.0F) >= ChairMotion.IMPACT_TICK) {
            Direction exit = entityData.get(FALL_DIRECTION);
            Vec3 target = fallTarget(exit, getY() + entityData.get(FALL_FLOOR));
            if (clearLanding(rider, exit, target)) {
                return target;
            }
        }
        Direction[] order = { facing(), facing().getClockWise(), facing().getCounterClockWise(), facing().getOpposite() };
        for (Direction direction : order) {
            Vec3 exit = safeStand(rider, blockPosition().relative(direction));
            if (exit != null) {
                return exit;
            }
        }
        Vec3 above = safeStand(rider, blockPosition().above());
        if (above != null) {
            return above;
        }
        return level().noCollision(rider, standingBox(rider, mountingPosition))
                ? mountingPosition : super.getDismountLocationForPassenger(rider);
    }

    @Nullable
    private Vec3 safeStand(LivingEntity rider, BlockPos pos) {
        if (!level().hasChunkAt(pos)) {
            return null;
        }
        Vec3 candidate = DismountHelper.findSafeDismountLocation(rider.getType(), level(), pos, true);
        return candidate != null && level().noCollision(rider, standingBox(rider, candidate)) ? candidate : null;
    }

    private Vec3 slideEnd() {
        return position().add(Vec3.atLowerCornerOf(facing().getNormal()).scale(SLIDE_DISTANCE));
    }

    private Vec3 fallTarget(Direction exit, double y) {
        Vec3 target = position().add(Vec3.atLowerCornerOf(exit.getNormal()).scale(FALL_DISTANCE));
        if (exit != facing()) {
            target = target.add(Vec3.atLowerCornerOf(facing().getNormal()).scale(SIDE_LANDING_ADVANCE));
        }
        return new Vec3(target.x, y, target.z);
    }

    private boolean clearApproach(LivingEntity rider, Direction exit, Vec3 target) {
        Vec3 forward = Vec3.atLowerCornerOf(facing().getNormal());
        AABB approach = standingBox(rider, slideEnd())
                .minmax(standingBox(rider, new Vec3(target.x, getY(), target.z)))
                .expandTowards(forward.scale(.24));
        // During the slide the rider still overlaps the seat. Check the part of the
        // approach outside the chair's column, including the corner on a side fall.
        return level().noCollision(rider, outsideChair(approach, facing()))
                && (exit == facing() || level().noCollision(rider, outsideChair(approach, exit)));
    }

    private AABB outsideChair(AABB box, Direction side) {
        return switch (side) {
            case NORTH -> box.setMaxZ(blockPosition().getZ());
            case SOUTH -> box.setMinZ(blockPosition().getZ() + 1);
            case WEST -> box.setMaxX(blockPosition().getX());
            case EAST -> box.setMinX(blockPosition().getX() + 1);
            default -> throw new IllegalArgumentException("Chair must face horizontally");
        };
    }

    private boolean clearLanding(LivingEntity rider, Direction exit, Vec3 target) {
        Vec3 outward = Vec3.atLowerCornerOf(exit.getNormal());
        Vec3 far = target.add(outward.scale(1.02));
        if (!level().hasChunkAt(BlockPos.containing(far)) || !level().getWorldBorder().isWithinBounds(far.x, far.z)) {
            return false;
        }
        BlockPos onFloor = BlockPos.containing(target.x, getY(), target.z);
        Vec3 support = DismountHelper.findSafeDismountLocation(rider.getType(), level(), onFloor, true);
        if (support == null || !Mth.equal(support.y, target.y)) {
            return false;
        }
        AABB landing = standingBox(rider, target);
        AABB room = landing.expandTowards(outward.scale(.83)).expandTowards(outward.scale(-.34));
        return level().noCollision(rider, room)
                && level().getBlockCollisions(rider, landing.move(0, -.05, 0)).iterator().hasNext();
    }

    private static AABB standingBox(LivingEntity rider, Vec3 position) {
        EntityDimensions size = rider.getDimensions(Pose.STANDING);
        return size.makeBoundingBox(position).deflate(1.0E-5);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
