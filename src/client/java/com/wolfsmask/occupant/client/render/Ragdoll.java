package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Whatever its legs are through hangs off them loose, like something already dead. Each arm, each
 * leg and the head swings on its own, a weight on a joint: set going by the leg going in (the arms
 * that were reaching for you drop), by the shove and by the lift starting and stopping, settling
 * slowly, and never quite still. The body is held up by the leg; everything else just hangs.
 * <p>
 * Client-side, and only the look of it: the swinging is worked out every tick from how the held
 * thing is actually moving, and laid over whatever pose the game gave it.
 */
public final class Ragdoll {
	private static final int HEAD_X = 0, HEAD_Z = 1, RIGHT_ARM_X = 2, RIGHT_ARM_Z = 3, LEFT_ARM_X = 4, LEFT_ARM_Z = 5;
	private static final int RIGHT_LEG_X = 6, RIGHT_LEG_Z = 7, LEFT_LEG_X = 8, LEFT_LEG_Z = 9;
	private static final int JOINTS = 10;
	/** Where each hangs when it has stopped: the head down on the chest, arms and legs straight down, a little apart. */
	private static final float[] REST = {1.05f, 0.0f, 0.16f, 0.10f, 0.10f, -0.10f, 0.08f, 0.05f, -0.05f, -0.05f};
	/** How quickly each swings back (a head is quicker than a leg), and how much each swing loses. */
	private static final float[] STIFF = {0.11f, 0.07f, 0.045f, 0.06f, 0.05f, 0.065f, 0.035f, 0.05f, 0.04f, 0.055f};
	private static final float DAMP = 0.055f;

	/** One held body: each joint's angle now, a tick ago, and how fast it is swinging. */
	private static final class Body {
		final float[] angle = new float[JOINTS];
		final float[] before = new float[JOINTS];
		final float[] speed = new float[JOINTS];
		final Random random;
		@Nullable
		Vec3 last;
		Vec3 velocity = Vec3.ZERO;

		Body(int seed) {
			random = new Random(seed * 0x9E3779B97F4A7C15L);
			// As the leg goes in: arms still up from reaching for them, head up, legs mid-stride;
			// and the jolt of it, a different way for each.
			System.arraycopy(REST, 0, angle, 0, JOINTS);
			angle[HEAD_X] = 0.0f;
			angle[RIGHT_ARM_X] = -1.2f;
			angle[LEFT_ARM_X] = -1.0f;
			angle[RIGHT_LEG_X] = 0.3f;
			angle[LEFT_LEG_X] = -0.3f;
			for (int j = 0; j < JOINTS; j++) speed[j] = (random.nextFloat() - 0.5f) * 0.3f;
			System.arraycopy(angle, 0, before, 0, JOINTS);
		}

		void step(LivingEntity e) {
			System.arraycopy(angle, 0, before, 0, JOINTS);
			Vec3 pos = e.position();
			Vec3 moved = last == null ? Vec3.ZERO : pos.subtract(last);
			Vec3 jolt = moved.subtract(velocity);
			last = pos;
			velocity = moved;
			// The jolt in its own frame: forward, and to its right.
			double yaw = Math.toRadians(e.yBodyRot);
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double forward = jolt.x * fx + jolt.z * fz;
			double right = -jolt.x * fz + jolt.z * fx;
			// A weight swings quicker while what holds it is pulled up, slower as it drops.
			float lift = (float) Mth.clamp(1.0 + jolt.y * 8.0, 0.4, 2.5);
			for (int j = 0; j < JOINTS; j++) {
				boolean pitch = j % 2 == 0;
				// Shoved one way, what hangs swings the other.
				double push = (pitch ? -forward : right) * 5.0;
				speed[j] += (float) (-STIFF[j] * lift * (angle[j] - REST[j]) - DAMP * speed[j] + push);
				speed[j] += (random.nextFloat() - 0.5f) * 0.006f;               // never quite still
				angle[j] = Mth.clamp(angle[j] + speed[j], -1.7f, 1.7f);
			}
		}

		float[] pose(float partial) {
			float[] out = new float[JOINTS];
			for (int j = 0; j < JOINTS; j++) out[j] = Mth.lerp(partial, before[j], angle[j]);
			return out;
		}
	}

	private static final Map<Integer, Body> HELD = new HashMap<>();
	/**
	 * For the newer games, where a model is posed from a copy of the entity: that copy's pose (read
	 * wherever the game poses its models, which need not be the thread that noted it).
	 */
	private static final Map<Object, float[]> STATES = java.util.Collections.synchronizedMap(new WeakHashMap<>());

	private Ragdoll() {
	}

	/** Every tick: what is held, and how it swings. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			HELD.clear();
			return;
		}
		if (mc.isPaused()) return;
		Set<Integer> held = new HashSet<>();
		for (OccupantEntity o : mc.level.getEntitiesOfClass(OccupantEntity.class, mc.player.getBoundingBox().inflate(96.0),
				x -> !x.isRemoved())) {
			for (int id : o.getHeld()) held.add(id);
		}
		HELD.keySet().removeIf(id -> !held.contains(id));
		for (int id : held) {
			Entity e = mc.level.getEntity(id);
			if (!(e instanceof LivingEntity living) || e.isRemoved()) continue;
			HELD.computeIfAbsent(id, Body::new).step(living);
		}
	}

	/** Its pose this frame if something has it on a leg, or null. */
	@Nullable
	public static float[] forEntity(LivingEntity e, float ageInTicks) {
		Body b = HELD.get(e.getId());
		return b == null ? null : b.pose(Mth.clamp(ageInTicks - e.tickCount, 0.0f, 1.0f));
	}

	/** For the newer games: noted when the game copies what it needs from the entity to draw it. */
	public static void mark(LivingEntity e, Object state, float partial) {
		Body b = HELD.get(e.getId());
		if (b == null) STATES.remove(state);
		else STATES.put(state, b.pose(partial));
	}

	@Nullable
	public static float[] forState(Object state) {
		return STATES.get(state);
	}

	/** Lays the hanging pose over whatever pose the game gave it. */
	public static void apply(HumanoidModel<?> m, float[] p) {
		m.head.xRot = p[HEAD_X];
		m.head.yRot = 0.0f;
		m.head.zRot = p[HEAD_Z];
		m.rightArm.xRot = p[RIGHT_ARM_X];
		m.rightArm.yRot = 0.0f;
		m.rightArm.zRot = p[RIGHT_ARM_Z];
		m.leftArm.xRot = p[LEFT_ARM_X];
		m.leftArm.yRot = 0.0f;
		m.leftArm.zRot = p[LEFT_ARM_Z];
		m.rightLeg.xRot = p[RIGHT_LEG_X];
		m.rightLeg.yRot = 0.0f;
		m.rightLeg.zRot = p[RIGHT_LEG_Z];
		m.leftLeg.xRot = p[LEFT_LEG_X];
		m.leftLeg.yRot = 0.0f;
		m.leftLeg.zRot = p[LEFT_LEG_Z];
	}
}
