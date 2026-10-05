package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;

/**
 * Everything needed to draw one Occupant for one frame. 1.21.1 has no render states of its own,
 * so this copy carries the few things the later versions' state gives it as well.
 */
public class OccupantRenderState {
	public double x;
	public double y;
	public double z;
	public float ageInTicks;
	public float bodyRot;
	public double distanceToCameraSq;

	public OccupantEntity.Mode mode = OccupantEntity.Mode.IDLE;
	public OccupantEntity.Form form = OccupantEntity.Form.VEILED;
	/** Different for each appearance, so no two of its twitches are the same. */
	public int seed;
	/** Clear blocks above its feet, so it does not stand up through a ceiling. */
	public float headroom = 4.0f;
	/** 0 standing up, 1 folded as low as it goes, for cramped spaces. */
	public float crouch;
	/** Which way its last shove carried it, in its own frame, roughly -0.5 to 0.5. */
	public float leanForward;
	public float leanSide;
	/** How far the drawn body trails the real one, in blocks. */
	public double offsetX;
	public double offsetZ;
	/** Where each planted leg's point is, in model pixels (x, y, z per leg). */
	public final float[] legTarget = new float[OccupantGeometry.LEGS * 3];
	/** Whether each leg is planted on something, or hanging free. */
	public final boolean[] legPlanted = new boolean[OccupantGeometry.LEGS];
	/** Which way each knee bends to stay out of the blocks, in model space; used where set. */
	public final float[] legBend = new float[OccupantGeometry.LEGS * 3];
	public final boolean[] legBendSet = new boolean[OccupantGeometry.LEGS];
	/** Model pixels to blocks, worked out once per frame. */
	public float occupantScale = 1.0f;
}
