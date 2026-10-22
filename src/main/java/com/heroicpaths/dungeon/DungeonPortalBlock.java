package com.heroicpaths.dungeon;

import com.heroicpaths.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.Collection;

/**
 * Портал данжа: в деревне ведёт в данж, в данже — обратно к вратам.
 * Проём 3×4 рисуется одной воронкой: каждый блок знает свою клетку ({@link #PART} = столбец + 3 × ряд),
 * одиночные блоки и проёмы другой формы получают {@link #LONE} — повторяющийся узор.
 */
public class DungeonPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final int WIDTH = 3, HEIGHT = 4, LONE = WIDTH * HEIGHT;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, LONE);
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.78f, 0.3f), 0.8f);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.35f, 1f, 0.9f), 0.9f);

    public DungeonPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(PART, LONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PART);
    }

    /**
     * Заливает проём порталом. Если клетки образуют ровный проём 3×4, каждая получает свою часть воронки
     * (столбец считается вдоль оси проёма, ряд — снизу вверх).
     */
    public static void fill(Level level, Collection<BlockPos> cells, Direction.Axis axis) {
        int minAlong = Integer.MAX_VALUE, maxAlong = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            int along = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        boolean whole = cells.size() == LONE && maxAlong - minAlong == WIDTH - 1 && maxY - minY == HEIGHT - 1;
        BlockState base = ModBlocks.DUNGEON_PORTAL.get().defaultBlockState().setValue(AXIS, axis);
        for (BlockPos pos : cells) {
            int along = (axis == Direction.Axis.X ? pos.getX() : pos.getZ()) - minAlong;
            int part = whole ? along + WIDTH * (pos.getY() - minY) : LONE;
            level.setBlock(pos, base.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && !player.isPassenger()) {
            DungeonManager.onPortalContact(player, pos);
        }
    }

    /** Искры втягиваются в центр воронки, по краям проёма поднимаются золотые и бирюзовые огоньки. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int part = state.getValue(PART);
        boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        // Центр воронки в координатах проёма (у одиночного блока — сам блок).
        double cAlong = part == LONE ? 0 : 1 - part % WIDTH;
        double cUp = part == LONE ? 0 : 1.5 - part / WIDTH;
        for (int i = 0; i < 2; i++) {
            double along = random.nextDouble() - 0.5, up = random.nextDouble() - 0.5, depth = (random.nextDouble() - 0.5) * 0.4;
            double px = x + (alongX ? along : depth), pz = z + (alongX ? depth : along), py = y + up;
            double toAlong = (cAlong - along) * 0.6, toUp = (cUp - up) * 0.6;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, alongX ? toAlong * 0.1 : 0, toUp * 0.1, alongX ? 0 : toAlong * 0.1);
        }
        boolean edge = part == LONE || part % WIDTH != 1 || part / WIDTH == HEIGHT - 1;
        if (edge && random.nextInt(3) == 0) {
            double along = (random.nextDouble() - 0.5), side = (random.nextBoolean() ? 1 : -1) * 0.25;
            level.addParticle(random.nextBoolean() ? GOLD : TEAL, x + (alongX ? along : side), y + random.nextDouble() - 0.5,
                    z + (alongX ? side : along), 0, 0.02, 0);
        }
        if (random.nextInt(40) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, Mth.nextDouble(random, -0.02, 0.02), 0.03, Mth.nextDouble(random, -0.02, 0.02));
        }
        if (random.nextInt(160) == 0) {
            level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS,
                    0.25f, random.nextFloat() * 0.4f + 0.8f, false);
        }
    }
}
