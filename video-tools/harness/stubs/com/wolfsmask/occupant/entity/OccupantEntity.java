package com.wolfsmask.occupant.entity;
import net.minecraft.world.entity.Entity;
public class OccupantEntity extends Entity {
	public enum Mode { IDLE, STARE, STALK, CHASE, AMBUSH }
	public enum Form { VEILED, REVEALED }
	public Mode mode = Mode.STARE;
	public Form form = Form.REVEALED;
	public float coil;
	public int[] held = new int[0];
	public Mode getMode() { return mode; }
	public Form getForm() { return form; }
	public float getCoil() { return coil; }
	public int[] getHeld() { return held; }
	public int getAct() { return 4; }
	public boolean isConcealed() { return false; }
}
