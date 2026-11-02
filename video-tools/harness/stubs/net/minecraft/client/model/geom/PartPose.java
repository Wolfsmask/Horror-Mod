package net.minecraft.client.model.geom;
public class PartPose {
	public static final PartPose ZERO = new PartPose(0, 0, 0, 0, 0, 0);
	public final float x, y, z, xRot, yRot, zRot;
	private PartPose(float x, float y, float z, float a, float b, float c) { this.x = x; this.y = y; this.z = z; xRot = a; yRot = b; zRot = c; }
	public static PartPose offset(float x, float y, float z) { return new PartPose(x, y, z, 0, 0, 0); }
	public static PartPose offsetAndRotation(float x, float y, float z, float a, float b, float c) { return new PartPose(x, y, z, a, b, c); }
}
