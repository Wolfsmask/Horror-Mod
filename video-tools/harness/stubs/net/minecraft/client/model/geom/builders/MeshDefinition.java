package net.minecraft.client.model.geom.builders;
import net.minecraft.client.model.geom.PartPose;
public class MeshDefinition {
	private final PartDefinition root = new PartDefinition("root", CubeListBuilder.create(), PartPose.ZERO);
	public PartDefinition getRoot() { return root; }
}
