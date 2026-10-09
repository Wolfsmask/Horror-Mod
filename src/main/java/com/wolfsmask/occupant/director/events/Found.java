package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.Hiding;
import com.wolfsmask.occupant.director.Mercy;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * It found them hiding, after it told them not to. Everything alive round them drops dead, and
 * the dark comes in. Then, depending on where they hid:
 * <ul>
 *   <li>in a house, it is outside, and it takes the roof off, all of it, while they watch, and
 *   looks in at them;</li>
 *   <li>in a hole near the top, it smashes its way down through what is over them and looks
 *   down in at them;</li>
 *   <li>anywhere deeper, it hunts them through the dark.</li>
 * </ul>
 * When it has them: black, and they wake where they sleep (or where the world began) with some of
 * what they had gone, and the words. The second time it does not let them wake.
 */
public final class Found {
	public static final String ID = "found";
	/** Kept in the story's cooldowns for good: it has found them hiding once already. */
	public static final String CAUGHT = "caught_hiding";

	private Found() {
	}

	/** What happens now they have been found, where they are; never null. */
	public static Sequence begin(Haunt h, ServerPlayer p, Hiding.Where where) {
		ServerLevel level = Compat.level(p);
		killEverything(p, level);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		Cues.soundAtEars(p, ModSounds.DRONE, SoundSource.AMBIENT, 1.0f, 0.7f);
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0, false, false));
		Sequence s = switch (where) {
			case HOUSE -> RoofOff.create(h, p);
			case HOLE -> DugOut.create(h, p);
			default -> null;
		};
		if (s == null) s = hunt(h, p);
		return s != null ? s : new Taken(h);
	}

	/**
	 * Everything alive round them, dead where it stands: the monsters, the animals, the village.
	 * Not their own animals, nor anything they have named, nor the great ones.
	 */
	private static void killEverything(ServerPlayer p, ServerLevel level) {
		List<Mob> all = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48.0),
				x -> x.isAlive() && !(x instanceof OccupantEntity) && x.getMaxHealth() <= 100.0f && !x.hasCustomName()
						&& !(x instanceof TamableAnimal t && t.isTame()));
		for (Mob m : all) m.hurtServer(level, m.damageSources().generic(), 1000.0f);
		if (!all.isEmpty()) Cues.sound(p, ModSounds.STATIC, SoundSource.HOSTILE, p.getEyePosition(), 0.6f, 0.5f);
	}

	/** Deep down, or anywhere it cannot do the rest: it hunts them, and the dark comes in. */
	@Nullable
	private static Sequence hunt(Haunt h, ServerPlayer p) {
		boolean open = Compat.level(p).canSeeSky(p.blockPosition().above());
		BlockPos spot = Spots.aroundPlayer(p, p.getRandom(), 10, 22, 60, 180, open, 50,
				pos -> Math.abs(pos.getY() - p.getBlockY()) <= 6 && Sight.isHidden(p, pos.above()));
		if (spot == null) {
			spot = Spots.aroundPlayer(p, p.getRandom(), 6, 14, 90, 180, open, 50, pos -> Math.abs(pos.getY() - p.getBlockY()) <= 6);
		}
		if (spot == null) return null;
		OccupantEntity e = h.spawnOccupant(p, spot, OccupantEntity.Mode.CHASE, OccupantEntity.Form.REVEALED);
		if (e == null) return null;
		HuntEvent.Hunt hunt = new HuntEvent.Hunt(h, e, 0, false, (who, it, since) -> caught(h, who, since));
		hunt.runAtOnce();
		Cues.whisper(p, "Found you.", 60);
		return hunt;
	}

	/** It has reached them, in the dark: a breath, black, and then what comes of it. */
	private static boolean caught(Haunt h, ServerPlayer p, int since) {
		if (since == 0) {
			Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, p.getEyePosition(), 1.0f, 0.6f);
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
		}
		if (since == 12) punish(h, p);
		return since < 30;
	}

	/**
	 * After the black: the first time, they wake where they sleep with some of their things gone,
	 * and are told why; the second time, they do not wake.
	 */
	static void punish(Haunt h, ServerPlayer p) {
		HauntData d = h.data;
		if (d.cooldowns.containsKey(CAUGHT)) {
			d.cooldowns.remove(CAUGHT);
			Cues.whisper(p, "It told you.", 100);
			Strike.hurt(p, 1000.0f);
			return;
		}
		d.cooldowns.put(CAUGHT, Long.MAX_VALUE);
		takeThings(p);
		Mercy.sendHome(p);
		Cues.whisper(p, "It doesn't want you to do that.", 160);
		Cues.message(p, Component.literal("It doesn't want you to do that.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** Two or three of whatever they carry, gone. */
	private static void takeThings(ServerPlayer p) {
		Inventory inv = p.getInventory();
		List<Integer> full = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) full.add(i);
		int n = Math.min(full.size(), 2 + p.getRandom().nextInt(2));
		for (int k = 0; k < n; k++) inv.setItem(full.remove(p.getRandom().nextInt(full.size())), ItemStack.EMPTY);
	}

	/** Whether anything is in the way of a block being torn out (bedrock, a portal frame, the like). */
	private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0f;
	}

	/** Somewhere it stands, round {@code centre} at {@code radius}, on the ground near {@code y}, first try towards {@code toward}. */
	@Nullable
	private static BlockPos standRound(ServerLevel level, Vec3 centre, double radius, int y, Vec3 toward, Set<Long> notIn) {
		for (int i = 0; i < 16; i++) {
			double angle = (i % 2 == 0 ? 1 : -1) * (i / 2) * 22.5;
			Vec3 dir = Sight.rotateY(toward, angle);
			for (double r = radius; r <= radius + 3.0; r += 1.0) {
				BlockPos feet = Spots.groundNear(level, Mth.floor(centre.x + dir.x * r), y, Mth.floor(centre.z + dir.z * r), 4);
				if (feet != null && !notIn.contains(BlockPos.asLong(feet.getX(), 0, feet.getZ()))) return feet;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the roof off

	/**
	 * In a house: it is outside, as tall as the house. Three blows on the roof, then the roof comes
	 * off, all of it, piece after piece, while they watch, and it looks in at them. Then black.
	 */
	static final class RoofOff implements Sequence {
		private static final int TEAR = 40;
		private static final int TORN = 82;
		private static final int BLACK = 112;
		private static final int PUNISH = 120;
		private static final int OVER = 132;

		private final Haunt haunt;
		private final OccupantEntity entity;
		private final ServerLevel level;
		private final List<BlockPos> roof;
		private int t;
		private int next;

		private RoofOff(Haunt haunt, OccupantEntity entity, ServerLevel level, List<BlockPos> roof) {
			this.haunt = haunt;
			this.entity = entity;
			this.level = level;
			this.roof = roof;
			entity.setGazeLocked(true);
		}

		@Nullable
		static Sequence create(Haunt h, ServerPlayer p) {
			ServerLevel level = Compat.level(p);
			BlockPos head = p.blockPosition().above();
			// The room: the open space round them at the height of their head, out to its walls.
			Set<Long> room = new HashSet<>();
			ArrayDeque<BlockPos> open = new ArrayDeque<>();
			open.add(head);
			room.add(BlockPos.asLong(head.getX(), 0, head.getZ()));
			List<BlockPos> cells = new ArrayList<>();
			while (!open.isEmpty() && cells.size() < 500) {
				BlockPos c = open.poll();
				cells.add(c);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = c.relative(d);
					long key = BlockPos.asLong(n.getX(), 0, n.getZ());
					if (room.contains(key) || Math.abs(n.getX() - head.getX()) > 12 || Math.abs(n.getZ() - head.getZ()) > 12) continue;
					if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty() || level.canSeeSky(n)) continue;
					room.add(key);
					open.add(n);
				}
			}
			// The ceiling: the lowest thing over the room.
			int ceiling = Integer.MAX_VALUE;
			for (BlockPos c : cells) {
				for (int up = 1; up <= 8; up++) {
					BlockPos at = c.above(up);
					if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
						ceiling = Math.min(ceiling, at.getY());
						break;
					}
				}
			}
			if (ceiling == Integer.MAX_VALUE) return null;
			// The roof: everything from the ceiling up, over the room and the top of the walls round it.
			Set<Long> columns = new HashSet<>();
			for (BlockPos c : cells) {
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) columns.add(BlockPos.asLong(c.getX() + dx, 0, c.getZ() + dz));
			}
			List<BlockPos> roof = new ArrayList<>();
			for (long col : columns) {
				int x = BlockPos.getX(col), z = BlockPos.getZ(col);
				int gap = 0;
				boolean any = false;
				for (int y = ceiling; y <= ceiling + 10 && roof.size() < 900; y++) {
					BlockPos at = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(at);
					if (state.isAir()) {
						if (any && ++gap >= 2) break;
						continue;
					}
					gap = 0;
					any = true;
					if (breakable(level, at, state)) roof.add(at);
				}
			}
			if (roof.size() < 4) return null;
			// Where it stands: outside, on the ground, a step or two out from the walls.
			Vec3 centre = Vec3.ZERO;
			for (BlockPos c : cells) centre = centre.add(c.getX() + 0.5, 0.0, c.getZ() + 0.5);
			centre = centre.scale(1.0 / cells.size());
			double radius = 0.0;
			for (BlockPos c : cells) radius = Math.max(radius, Math.hypot(c.getX() + 0.5 - centre.x, c.getZ() + 0.5 - centre.z));
			BlockPos feet = standRound(level, centre, radius + 2.5, p.getBlockY(), Sight.flatLook(p), columns);
			if (feet == null) return null;
			OccupantEntity e = h.spawnOccupant(p, feet, OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
			if (e == null) return null;
			// Torn off from its side first.
			Vec3 from = e.position();
			roof.sort(Comparator.comparingDouble(b -> Vec3.atCenterOf(b).distanceToSqr(from.x, b.getY(), from.z)));
			return new RoofOff(h, e, level, roof);
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (entity.hasVanished()) return false;
			entity.keepAlive();
			if (t == 1) {
				Cues.effect(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
				Cues.whisper(p, "Found you.", 60);
			}
			if (t == 10 || t == 22 || t == 34) {
				// Something very heavy, on the roof.
				Vec3 over = Vec3.atCenterOf(roof.get(0));
				Cues.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, over, 1.0f, 0.5f);
				Cues.effect(p, ScreenEffectPayload.STATIC, 5, 0.3f);
				for (int k = 0; k < 3 && next < roof.size(); k++) tear(roof.get(next++), true);
			}
			if (t >= TEAR && t < TORN) {
				int per = Math.max(1, Mth.ceil((roof.size() - next) / (double) (TORN - t)));
				for (int k = 0; k < per && next < roof.size(); k++) tear(roof.get(next++), k < 3);
				if (t % 6 == 0) Cues.sound(p, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, Vec3.atCenterOf(roof.get(Math.max(0, next - 1))), 0.8f, 0.6f);
			}
			if (t == TORN) {
				while (next < roof.size()) tear(roof.get(next++), false);
				entity.faceTowards(p.getEyePosition());
				Cues.whisper(p, "There you are.", 70);
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 0.6f);
			}
			if (t > TORN) entity.faceTowards(p.getEyePosition());
			if (t == BLACK) {
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
				Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
			}
			if (t == PUNISH) {
				Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
				punish(haunt, p);
			}
			return t < OVER;
		}

		private void tear(BlockPos pos, boolean shown) {
			BlockState state = level.getBlockState(pos);
			if (!breakable(level, pos, state)) return;
			if (shown) level.destroyBlock(pos, false, entity, 512);
			else level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		}

		@Override
		public void end() {
			entity.vanish();
		}

		@Override
		@Nullable
		public OccupantEntity occupant() {
			return entity;
		}
	}

	// ------------------------------------------------------------------ dug out

	/**
	 * In a hole, near the top: it is up there, beside it. Three blows, each one taking away more of
	 * what is over them, until there is nothing between them, and it looks down in at them. Black.
	 */
	static final class DugOut implements Sequence {
		private static final int[] BLOWS = {14, 30, 46};
		private static final int OPEN = 50;
		private static final int BLACK = 84;
		private static final int PUNISH = 92;
		private static final int OVER = 104;

		private final Haunt haunt;
		private final OccupantEntity entity;
		private final ServerLevel level;
		/** What is over them, a layer at a time from the top down. */
		private final List<List<BlockPos>> layers;
		private int t;
		private int next;

		private DugOut(Haunt haunt, OccupantEntity entity, ServerLevel level, List<List<BlockPos>> layers) {
			this.haunt = haunt;
			this.entity = entity;
			this.level = level;
			this.layers = layers;
			entity.setGazeLocked(true);
		}

		@Nullable
		static Sequence create(Haunt h, ServerPlayer p) {
			ServerLevel level = Compat.level(p);
			BlockPos head = p.blockPosition().above();
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
			if (top - 1 - head.getY() > Hiding.SHALLOW + 1) return null;
			List<List<BlockPos>> layers = new ArrayList<>();
			for (int y = top - 1; y > head.getY(); y--) {
				List<BlockPos> layer = new ArrayList<>();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos at = new BlockPos(head.getX() + dx, y, head.getZ() + dz);
						if (breakable(level, at, level.getBlockState(at))) layer.add(at);
					}
				}
				if (!layer.isEmpty()) layers.add(layer);
			}
			if (layers.isEmpty()) return null;
			// It is up there on the ground, right beside the hole.
			Set<Long> hole = new HashSet<>();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) hole.add(BlockPos.asLong(head.getX() + dx, 0, head.getZ() + dz));
			BlockPos feet = standRound(level, Vec3.atBottomCenterOf(head), 2.5, top, Sight.flatLook(p), hole);
			if (feet == null || feet.getY() < top - 2) return null;
			OccupantEntity e = h.spawnOccupant(p, feet, OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
			if (e == null) return null;
			return new DugOut(h, e, level, layers);
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (entity.hasVanished()) return false;
			entity.keepAlive();
			entity.faceTowards(p.getEyePosition());
			if (t == 1) {
				Cues.effect(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
				Cues.whisper(p, "Found you.", 60);
			}
			for (int b = 0; b < BLOWS.length; b++) {
				if (t != BLOWS[b]) continue;
				// Each blow takes its share of what is over them, from the top down.
				int upTo = b == BLOWS.length - 1 ? layers.size() : Mth.ceil(layers.size() * (b + 1) / (double) BLOWS.length);
				Vec3 at = p.getEyePosition().add(0.0, 2.0, 0.0);
				Cues.sound(p, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, at, 1.0f, 0.45f);
				Cues.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, at, 1.0f, 0.4f);
				Cues.effect(p, ScreenEffectPayload.STATIC, 6, 0.35f + 0.1f * b);
				for (; next < upTo; next++) {
					int shown = 0;
					for (BlockPos pos : layers.get(next)) {
						BlockState state = level.getBlockState(pos);
						if (!breakable(level, pos, state)) continue;
						if (shown++ < 4) level.destroyBlock(pos, false, entity, 512);
						else level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
			if (t == OPEN) {
				Cues.whisper(p, "There you are.", 70);
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 0.6f);
			}
			if (t == BLACK) {
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
				Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
			}
			if (t == PUNISH) {
				Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
				punish(haunt, p);
			}
			return t < OVER;
		}

		@Override
		public void end() {
			entity.vanish();
		}

		@Override
		@Nullable
		public OccupantEntity occupant() {
			return entity;
		}
	}

	// ------------------------------------------------------------------ nowhere for it

	/** Nowhere for it to be seen: only its breath, black, and what comes of it. */
	static final class Taken implements Sequence {
		private final Haunt haunt;
		private int t;

		Taken(Haunt haunt) {
			this.haunt = haunt;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (t == 1) {
				Cues.soundAtEars(p, ModSounds.BREATH, SoundSource.HOSTILE, 1.0f, 0.6f);
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
			}
			if (t == 12) punish(haunt, p);
			return t < 30;
		}
	}
}
