package net.minecraft.world.phys.shapes;
import net.minecraft.core.Direction;
public class VoxelShape {
	public static final VoxelShape EMPTY = new VoxelShape(true), BLOCK = new VoxelShape(false);
	private final boolean empty;
	private VoxelShape(boolean e) { empty = e; }
	public boolean isEmpty() { return empty; }
	public double min(Direction.Axis a) { return 0.0; }
	public double max(Direction.Axis a) { return 1.0; }
}
