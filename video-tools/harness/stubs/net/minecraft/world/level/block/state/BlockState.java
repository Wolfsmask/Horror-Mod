package net.minecraft.world.level.block.state;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
public class BlockState {
	public static final BlockState AIR = new BlockState(false), SOLID = new BlockState(true);
	private final boolean solid;
	private BlockState(boolean s) { solid = s; }
	public VoxelShape getCollisionShape(Level level, BlockPos pos) { return solid ? VoxelShape.BLOCK : VoxelShape.EMPTY; }
	public boolean isAir() { return !solid; }
}
