package net.minecraft.client.model.geom.builders;
import net.minecraft.client.model.geom.ModelPart;
public class LayerDefinition {
	private final MeshDefinition mesh;
	private LayerDefinition(MeshDefinition m) { mesh = m; }
	public static LayerDefinition create(MeshDefinition m, int w, int h) { return new LayerDefinition(m); }
	public ModelPart bakeRoot() { return mesh.getRoot().bake(); }
}
