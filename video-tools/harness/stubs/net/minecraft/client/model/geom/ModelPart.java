package net.minecraft.client.model.geom;
import java.util.*;
public class ModelPart {
	public float x, y, z, xRot, yRot, zRot, xScale = 1, yScale = 1, zScale = 1;
	public boolean visible = true;
	public final Map<String, ModelPart> children = new LinkedHashMap<>();
	public final List<float[]> cubes;   // x, y, z, w, h, d
	public final String name;
	private PartPose initial = PartPose.ZERO;
	public ModelPart(String name, List<float[]> cubes) { this.name = name; this.cubes = cubes; }
	public ModelPart getChild(String n) { ModelPart p = children.get(n); if (p == null) throw new NoSuchElementException(n); return p; }
	public void setInitialPose(PartPose p) { initial = p; resetPose(); }
	public void resetPose() { x = initial.x; y = initial.y; z = initial.z; xRot = initial.xRot; yRot = initial.yRot; zRot = initial.zRot; xScale = yScale = zScale = 1; }
	public void resetAll() { resetPose(); for (ModelPart c : children.values()) c.resetAll(); }
}
