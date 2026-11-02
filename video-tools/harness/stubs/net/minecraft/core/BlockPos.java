package net.minecraft.core;
import net.minecraft.world.phys.Vec3;
public class BlockPos {
	protected int x, y, z;
	public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
	public int getX() { return x; } public int getY() { return y; } public int getZ() { return z; }
	public static BlockPos containing(double a, double b, double c) { return new BlockPos((int) Math.floor(a), (int) Math.floor(b), (int) Math.floor(c)); }
	public static BlockPos containing(Vec3 v) { return containing(v.x, v.y, v.z); }
	public BlockPos above(int n) { return new BlockPos(x, y + n, z); }
	public BlockPos above() { return above(1); }
	public BlockPos below() { return above(-1); }
	public BlockPos offset(int a, int b, int c) { return new BlockPos(x + a, y + b, z + c); }
	public static class MutableBlockPos extends BlockPos {
		public MutableBlockPos() { super(0, 0, 0); }
		public MutableBlockPos set(int a, int b, int c) { x = a; y = b; z = c; return this; }
	}
}
