package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Everything needed to draw one Occupant for one frame. */
public class OccupantRenderState extends HumanoidRenderState {
	public OccupantEntity.Mode mode = OccupantEntity.Mode.IDLE;
	public OccupantEntity.Form form = OccupantEntity.Form.VEILED;
	/** Different for each appearance, so no two of its twitches are the same. */
	public int seed;
	/** Clear blocks above its feet, so it does not stand up through a ceiling. */
	public float headroom = 4.0f;
}
