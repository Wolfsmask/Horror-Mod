package net.minecraft.client.model.geom.builders;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import java.util.*;
public class PartDefinition {
	final String name; final CubeListBuilder cubes; final PartPose pose;
	final Map<String, PartDefinition> children = new LinkedHashMap<>();
	PartDefinition(String n, CubeListBuilder c, PartPose p) { name = n; cubes = c; pose = p; }
	public PartDefinition addOrReplaceChild(String n, CubeListBuilder c, PartPose p) { PartDefinition d = new PartDefinition(n, c, p); children.put(n, d); return d; }
	ModelPart bake() { ModelPart m = new ModelPart(name, cubes.cubes); m.setInitialPose(pose); for (PartDefinition c : children.values()) m.children.put(c.name, c.bake()); return m; }
}
