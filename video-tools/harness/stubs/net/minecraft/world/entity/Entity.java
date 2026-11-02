package net.minecraft.world.entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
public class Entity {
	private static int ids = 1;
	public int id = ids++;
	public Vec3 pos = Vec3.ZERO, old = Vec3.ZERO;
	public int tickCount;
	public float yHeadRot, xRot, eyeHeight = 1.62f, bbHeight = 1.8f, bbWidth = 0.6f;
	public Level level;
	public boolean removed;
	public int getId() { return id; }
	public Vec3 position() { return pos; }
	public Vec3 getPosition(float partial) { return old.lerp(pos, partial); }
	public double getX() { return pos.x; } public double getY() { return pos.y; } public double getZ() { return pos.z; }
	public float getEyeHeight() { return eyeHeight; }
	public Vec3 getEyePosition() { return pos.add(0, eyeHeight, 0); }
	public Vec3 getEyePosition(float p) { return getPosition(p).add(0, eyeHeight, 0); }
	public float getYHeadRot() { return yHeadRot; }
	public float getXRot() { return xRot; }
	public float getBbHeight() { return bbHeight; } public float getBbWidth() { return bbWidth; }
	public boolean isRemoved() { return removed; }
	public Level level() { return level; }
	public Vec3 view = new Vec3(0, 0, 1);
	public Vec3 getViewVector(float p) { return view; }
}
