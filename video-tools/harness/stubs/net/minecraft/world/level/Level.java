package net.minecraft.world.level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
public class Level {
	public java.util.function.Predicate<int[]> solid = p -> p[1] < 64;
	public java.util.Map<Integer, Entity> entities = new java.util.HashMap<>();
	public BlockState getBlockState(BlockPos p) { return solid.test(new int[]{p.getX(), p.getY(), p.getZ()}) ? BlockState.SOLID : BlockState.AIR; }
	public Entity getEntity(int id) { return entities.get(id); }
}
