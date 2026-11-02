package net.minecraft.client.model.geom.builders;
import java.util.*;
public class CubeListBuilder {
	public final List<float[]> cubes = new ArrayList<>();
	public static CubeListBuilder create() { return new CubeListBuilder(); }
	public CubeListBuilder texOffs(int u, int v) { return this; }
	public CubeListBuilder addBox(float x, float y, float z, float w, float h, float d) { cubes.add(new float[]{x, y, z, w, h, d}); return this; }
}
