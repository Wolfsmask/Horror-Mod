package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Everything needed to draw one Occupant for one frame. */
public class OccupantRenderState extends HumanoidRenderState {
	public OccupantEntity.Mode mode = OccupantEntity.Mode.IDLE;
	public OccupantEntity.Form form = OccupantEntity.Form.MIRROR;
	public Identifier texture = OccupantRenderer.HOLLOW;
	public boolean slimArms;
}
