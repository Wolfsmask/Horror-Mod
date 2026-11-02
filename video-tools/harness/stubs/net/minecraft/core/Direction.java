package net.minecraft.core;
public enum Direction {
	DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);
	private final int sx, sy, sz;
	Direction(int x, int y, int z) { sx = x; sy = y; sz = z; }
	public int getStepX() { return sx; } public int getStepY() { return sy; } public int getStepZ() { return sz; }
	public enum Axis { X, Y, Z }
}
