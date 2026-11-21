package dev.vergilchair;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ChairBlock extends DoublePlantBlock {
    private static final MapCodec<ChairBlock> CODEC = simpleCodec(ChairBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final double MODEL_GROWTH = 1.58 / 1.16;
    private static final Map<Direction, VoxelShape> SEAT_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> BACK_SHAPES = new EnumMap<>(Direction.class);

    static {
        makeShapes();
    }

    public ChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public MapCodec<? extends DoublePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        state = state.setValue(FACING, context.getHorizontalDirection().getOpposite());
        CollisionContext collision = context.getPlayer() == null
                ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        BlockState back = state.setValue(HALF, DoubleBlockHalf.UPPER);
        return context.getLevel().isUnobstructed(back, context.getClickedPos().above(), collision) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // Like other placeable blocks, a chair can be used in builds without a solid block below.
        return state.getValue(HALF) == DoubleBlockHalf.LOWER || super.canSurvive(state, level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(HALF) == DoubleBlockHalf.LOWER ? SEAT_SHAPES : BACK_SHAPES;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger() || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockState base = level.getBlockState(basePos);
        if (!base.is(this) || base.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            boolean occupied = !level.getEntitiesOfClass(ChairSeat.class, new AABB(basePos), ChairSeat::isVehicle).isEmpty();
            if (occupied) {
                player.displayClientMessage(Component.translatable("message.vergil_chair.occupied"), true);
                return InteractionResult.CONSUME;
            }
            ChairSeat seat = new ChairSeat(VergilChair.SEAT.get(), level);
            seat.place(basePos, base.getValue(FACING), serverPlayer.position());
            if (!seat.allowMount(serverPlayer)) {
                return InteractionResult.CONSUME;
            }
            if (level.addFreshEntity(seat)) {
                if (serverPlayer.startRiding(seat, true)) {
                    seat.positionRider(serverPlayer);
                } else {
                    seat.discard();
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void makeShapes() {
        VoxelShape north = Shapes.or(
                scaledBox(3.2, 7.2, 3.0, 12.8, 8.0, 11.9),
                scaledBox(3.2, 0, 3.0, 4.9, 7.4, 4.7),
                scaledBox(11.1, 0, 3.0, 12.8, 7.4, 4.7),
                scaledBox(3.2, 0, 11.0, 4.9, 7.4, 12.7),
                scaledBox(11.1, 0, 11.0, 12.8, 7.4, 12.7),
                scaledBox(4.0, 8.0, 10.55, 12.0, 13.7, 11.8),
                scaledBox(4.6, 13.7, 11.15, 11.4, 15.5, 12.55),
                scaledBox(5.6, 15.5, 12.0, 10.4, 15.92, 12.9),
                scaledBox(3.1, 11.4, 4.1, 4.45, 12.65, 10.85),
                scaledBox(11.55, 11.4, 4.1, 12.9, 12.65, 10.85),
                scaledBox(3.25, 7.8, 4.05, 4.65, 11.4, 5.55),
                scaledBox(11.35, 7.8, 4.05, 12.75, 11.4, 5.55));
        VoxelShape topCell = Shapes.block().move(0, 1, 0);
        VoxelShape shape = north;
        for (Direction direction = Direction.NORTH; ; direction = direction.getClockWise()) {
            SEAT_SHAPES.put(direction, Shapes.join(shape, Shapes.block(), BooleanOp.AND));
            BACK_SHAPES.put(direction, Shapes.join(shape, topCell, BooleanOp.AND).move(0, -1, 0));
            if (direction == Direction.WEST) {
                break;
            }
            VoxelShape turned = Shapes.empty();
            for (AABB part : shape.toAabbs()) {
                turned = Shapes.or(turned, Shapes.box(
                        1 - part.maxZ, part.minY, part.minX, 1 - part.minZ, part.maxY, part.maxX));
            }
            shape = turned;
        }
    }

    private static VoxelShape scaledBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        return box(scaleFromCenter(x1), y1 * MODEL_GROWTH, scaleFromCenter(z1),
                scaleFromCenter(x2), y2 * MODEL_GROWTH, scaleFromCenter(z2));
    }

    private static double scaleFromCenter(double value) {
        return 8 + (value - 8) * MODEL_GROWTH;
    }
}
