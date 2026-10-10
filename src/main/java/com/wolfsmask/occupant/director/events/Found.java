package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.Hiding;
import com.wolfsmask.occupant.director.Mercy;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.TakenEnding;
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
import net.minecraft.server.MinecraftServer;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * It found them hiding, after it told them not to. Everything alive round them drops dead. Then,
 * depending on where they hid:
 * <ul>
 *   <li>in a house, it is outside, and it takes the roof off, all of it, while they watch, and
 *   looks in at them;</li>
 *   <li>in a hole near the top, it smashes its way down through what is over them and looks
 *   down in at them;</li>
 *   <li>anywhere deeper, it hunts them through the dark.</li>
 * </ul>
 * When it has them it reaches down, its leg through them, and lifts them up out of where they hid
 * to its face. Then black, and they wake where they sleep (or where the world began) with some of
 * what they had gone, and the words. Caught again at the very end of the story, it takes them
 * (see {@link TakenEnding}).
 * <p>
 * Hidden there together, it comes once, for all of them: whoever is in the same room, or down the
 * same hole, is lifted out with the one it came for, a leg through each, and wakes at home with
 * them; anyone hidden somewhere else near watches it, and is let off for a while.
 */
public final class Found {
	public static final String ID = "found";
	/** Kept in the story's cooldowns for good: it has found them hiding once already. */
	public static final String CAUGHT = "caught_hiding";

	private Found() {
	}

	/** What happens now they have been found, where they are; never null. */
	public static Sequence begin(Haunt h, ServerPlayer p, Hiding.Where where) {
		return begin(h, p, where, List.of());
	}

	/**
	 * What happens now they have been found, where they are, with {@code with} hidden near them
	 * (those in the very same place go the same way as them); never null.
	 */
	public static Sequence begin(Haunt h, ServerPlayer p, Hiding.Where where, List<ServerPlayer> with) {
		ServerLevel level = Compat.level(p);
		killEverything(p, level);
		List<ServerPlayer> all = new ArrayList<>(with);
		all.add(p);
		Cues.effectFor(all, ScreenEffectPayload.SILENCE, 0, 1f);
		Cues.soundAtEars(p, ModSounds.DRONE, SoundSource.AMBIENT, 1.0f, 0.7f);
		Sequence s = switch (where) {
			case HOUSE -> RoofOff.create(h, p, with);
			case HOLE -> DugOut.create(h, p, with);
			default -> null;
		};
		if (s == null) s = hunt(h, p, with);
		return s != null ? s : new Taken(h, ids(with));
	}

	private static List<UUID> ids(List<ServerPlayer> players) {
		List<UUID> out = new ArrayList<>();
		for (ServerPlayer o : players) out.add(o.getUUID());
		return out;
	}

	/** Those of {@code with} still here (in the world with {@code p}, alive, within {@code range} of them). */
	private static List<ServerPlayer> stillHere(ServerPlayer p, List<UUID> with, double range) {
		MinecraftServer server = Compat.level(p).getServer();
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : with) {
			ServerPlayer o = server.getPlayerList().getPlayer(u);
			if (o != null && o != p && o.isAlive() && o.level() == p.level() && o.distanceTo(p) <= range) out.add(o);
		}
		return out;
	}

	/** {@code o}'s own story, to be punished in. */
	@Nullable
	private static Haunt hauntOf(ServerPlayer o) {
		Director director = Director.get();
		return director == null ? null : director.haunt(o);
	}

	/**
	 * Everything alive round them, dead where it stands: the monsters, the animals, the village.
	 * Not their own animals (tamed, on a lead, ridden), nor anything they have named, nor the great ones.
	 */
	private static void killEverything(ServerPlayer p, ServerLevel level) {
		List<Mob> all = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48.0),
				x -> x.isAlive() && !(x instanceof OccupantEntity) && x.getMaxHealth() <= 100.0f && !x.hasCustomName()
						&& !x.isLeashed() && !x.isVehicle() && !x.isPassenger()
						&& !(x instanceof TamableAnimal t && t.isTame()));
		for (Mob m : all) m.hurtServer(level, m.damageSources().generic(), 1000.0f);
		if (!all.isEmpty()) Cues.sound(p, ModSounds.STATIC, SoundSource.HOSTILE, p.getEyePosition(), 0.6f, 0.5f);
	}

	/** Deep down, or anywhere it cannot do the rest: it hunts them, and the dark comes in. */
	@Nullable
	private static Sequence hunt(Haunt h, ServerPlayer p, List<ServerPlayer> with) {
		List<UUID> others = ids(with);
		boolean open = Compat.level(p).canSeeSky(p.blockPosition().above());
		BlockPos spot = Spots.aroundPlayer(p, p.getRandom(), 10, 22, 60, 180, open, 50,
				pos -> Math.abs(pos.getY() - p.getBlockY()) <= 6 && Sight.isHidden(p, pos.above()));
		if (spot == null) {
			spot = Spots.aroundPlayer(p, p.getRandom(), 6, 14, 90, 180, open, 50, pos -> Math.abs(pos.getY() - p.getBlockY()) <= 6);
		}
		if (spot == null) return null;
		OccupantEntity e = h.spawnOccupant(p, spot, OccupantEntity.Mode.CHASE, OccupantEntity.Form.REVEALED);
		if (e == null) return null;
		// The dark comes in (where it takes the roof off or digs them out, they are meant to see it).
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0, false, false));
		for (ServerPlayer o : with) o.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0, false, false));
		HuntEvent.Hunt hunt = new HuntEvent.Hunt(h, e, 0, false, (who, it, since) -> caught(h, who, since, others));
		hunt.runAtOnce();
		Cues.whisper(p, "Found you.", 60);
		return hunt;
	}

	/** It has reached them, in the dark: a breath, black, and then what comes of it, for them and those down there with them. */
	private static boolean caught(Haunt h, ServerPlayer p, int since, List<UUID> with) {
		if (since == 0) {
			Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, p.getEyePosition(), 1.0f, 0.6f);
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
			Cues.effectFor(stillHere(p, with, 24.0), ScreenEffectPayload.BLACKOUT, 110, 1f);
		}
		if (since == 12) punishAll(h, p, stillHere(p, with, 24.0), null);
		return since < 30;
	}

	/**
	 * After the black: they wake where they sleep with some of their things gone, and are told why
	 * (more gone, the second time). Caught again at the very end of the story, they do not wake at
	 * home: it takes them. True if it has taken them (the end begins once this is over).
	 */
	static boolean punish(Haunt h, ServerPlayer p) {
		HauntData d = h.data;
		boolean again = d.cooldowns.containsKey(CAUGHT);
		if (again && TakenEnding.ready(d) && !Director.someoneTaken()) {
			d.cooldowns.remove(CAUGHT);
			TakenEnding.begin(h);
			return true;
		}
		d.cooldowns.put(CAUGHT, Long.MAX_VALUE);
		takeThings(p, again ? 4 : 2);
		Mercy.sendHome(p);
		String line = again ? "It told you. It doesn't want you to do that." : "It doesn't want you to do that.";
		Cues.whisperOnly(p, line, 160);
		Cues.messageOnly(p, Component.literal(line).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return false;
	}

	/**
	 * The one it came for and everyone with them, each punished in their own story (and each let go
	 * of, if it has them up on its legs, unless it is taking them). True if it has taken the one it
	 * came for.
	 */
	static boolean punishAll(Haunt h, ServerPlayer p, List<ServerPlayer> with, @Nullable Lift lift) {
		boolean taken = punish(h, p);
		if (!taken && lift != null) lift.release(p);
		for (ServerPlayer o : with) {
			Haunt theirs = hauntOf(o);
			boolean them = theirs != null && punish(theirs, o);
			if (!them && lift != null) lift.release(o);
		}
		return taken;
	}

	/** {@code least} or one more of whatever they carry, gone. */
	private static void takeThings(ServerPlayer p, int least) {
		Inventory inv = p.getInventory();
		List<Integer> full = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) full.add(i);
		int n = Math.min(full.size(), least + p.getRandom().nextInt(2));
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

	/**
	 * A leg down to them, through them, and up they come on it: out of where they hid, up first and
	 * then over, to its face, and held there. It does them no harm: only shows them it can.
	 */
	static final class Lift {
		/** Ticks from the leg going in to their being held at its face. */
		static final int RISE = 26;

		private final Haunt haunt;
		private final OccupantEntity entity;
		/** Everyone it lifts: the one it came for first, then whoever hid there with them. */
		private final List<UUID> who = new ArrayList<>();
		/** Where each of them came up from, and where they are held. */
		private final Map<UUID, Vec3[]> path = new HashMap<>();
		/** Those let go of already. */
		private final Set<UUID> let = new HashSet<>();
		private int t = -1;

		Lift(Haunt haunt, OccupantEntity entity, UUID leader, List<UUID> with) {
			this.haunt = haunt;
			this.entity = entity;
			who.add(leader);
			for (UUID u : with) if (!u.equals(leader)) who.add(u);
		}

		/** Whoever of them is still here to hold (alive, in its world), the one it came for first. */
		List<ServerPlayer> here(ServerPlayer leader) {
			MinecraftServer server = Compat.level(leader).getServer();
			List<ServerPlayer> out = new ArrayList<>();
			for (UUID u : who) {
				if (let.contains(u)) continue;
				ServerPlayer p = u.equals(leader.getUUID()) ? leader : server.getPlayerList().getPlayer(u);
				if (p != null && p.isAlive() && p.level() == entity.level()) out.add(p);
			}
			return out;
		}

		/** Everyone it lifts but the one it came for, as they are now. */
		List<ServerPlayer> others(ServerPlayer leader) {
			List<ServerPlayer> out = new ArrayList<>(here(leader));
			out.remove(leader);
			return out;
		}

		void tick(ServerPlayer leader) {
			t++;
			List<ServerPlayer> them = here(leader);
			if (t == 0) {
				entity.halt();
				entity.setMode(OccupantEntity.Mode.AMBUSH);
				entity.setHeld(them);
				for (int k = 0; k < them.size(); k++) {
					ServerPlayer p = them.get(k);
					path.put(p.getUUID(), new Vec3[]{p.position(), holdPoint(p, k, them.size())});
					Cues.sound(p, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, p.getEyePosition(), 1.0f, 0.55f + 0.04f * k);
				}
				Cues.effect(leader, ScreenEffectPayload.STATIC, 8, 0.5f);
			}
			double f = Math.min(1.0, t / (double) RISE);
			double up = 1.0 - (1.0 - f) * (1.0 - f);
			double g = Mth.clamp((f - 0.3) / 0.7, 0.0, 1.0);
			double over = g * g * (3.0 - 2.0 * g);
			double sway = Math.sin(t * 0.21) * 0.04;
			for (ServerPlayer p : them) {
				Vec3[] way = path.get(p.getUUID());
				if (way == null) continue;
				Vec3 from = way[0], to = way[1];
				Vec3 want = new Vec3(Mth.lerp(over, from.x, to.x) + sway, Mth.lerp(up, from.y, to.y), Mth.lerp(over, from.z, to.z) - sway);
				Vec3 v = want.subtract(p.position());
				if (v.length() > 0.9) v = v.normalize().scale(0.9);
				p.setNoGravity(true);
				p.setDeltaMovement(v.scale(0.6));
				p.hurtMarked = true;
				p.resetFallDistance();
			}
			entity.faceTowards(leader.getEyePosition());
		}

		/** Those it has let go of (not taken), still here: their view is theirs again. */
		List<ServerPlayer> letGo(ServerPlayer leader) {
			MinecraftServer server = Compat.level(leader).getServer();
			List<ServerPlayer> out = new ArrayList<>();
			for (UUID u : who) {
				if (!let.contains(u)) continue;
				ServerPlayer p = u.equals(leader.getUUID()) ? leader : server.getPlayerList().getPlayer(u);
				if (p != null) out.add(p);
			}
			return out;
		}

		/** Its leg out of {@code p}: theirs again. */
		void release(ServerPlayer p) {
			let.add(p.getUUID());
			if (!path.containsKey(p.getUUID())) return;
			p.setNoGravity(false);
			p.resetFallDistance();
		}

		/** Its legs out of all of them (they are somewhere else by now, most likely). */
		void release(MinecraftServer server) {
			entity.setHeld(List.of());
			for (UUID u : who) {
				ServerPlayer p = server.getPlayerList().getPlayer(u);
				if (p == null || !path.containsKey(u)) continue;
				p.setNoGravity(false);
				p.resetFallDistance();
			}
		}

		/**
		 * Just in front of its face, their eyes level with its own, or as high as there is room
		 * for; with others, the one it came for in the middle and the rest either side, a little lower.
		 */
		private Vec3 holdPoint(ServerPlayer p, int k, int of) {
			ServerLevel level = Compat.level(p);
			Vec3 base = entity.position();
			Vec3 toThem = new Vec3(p.getX() - base.x, 0.0, p.getZ() - base.z);
			double flat = toThem.length();
			Vec3 dir = flat < 1.0e-3 ? Sight.flatLook(p).scale(-1.0) : toThem.scale(1.0 / flat);
			double face = Sight.drawnBlocks(Math.max(1.0, flat), entity.getAct()) * 0.86;
			// Side by side across its front: the first in the middle, then right, left, further right...
			double side = k == 0 ? 0.0 : ((k + 1) / 2) * (k % 2 == 1 ? 0.95 : -0.95);
			Vec3 across = new Vec3(-dir.z, 0.0, dir.x).scale(side);
			double reach = Math.min(1.5, Math.max(flat, 1.0));
			Vec3 want = new Vec3(base.x + dir.x * reach + across.x, base.y + face - p.getEyeHeight() - Math.abs(side) * 0.3,
					base.z + dir.z * reach + across.z);
			// No higher than there is room for, over where they will be.
			double top = want.y;
			for (double y = Math.max(p.getY(), base.y); y <= top; y += 0.25) {
				net.minecraft.world.phys.AABB box = p.getBoundingBox().move(want.x - p.getX(), y - p.getY(), want.z - p.getZ());
				if (!level.noCollision(p, box)) {
					top = y - 0.25;
					break;
				}
			}
			return new Vec3(want.x, Math.max(p.getY(), top), want.z);
		}
	}

	// ------------------------------------------------------------------ the roof off

	/**
	 * In a house: it is outside, as tall as the house. Three blows on the roof, then the roof comes
	 * off, all of it, piece after piece, while they watch; then it climbs up onto the top of the
	 * wall and leans over, and looks down in at them. Then black.
	 */
	static final class RoofOff implements Sequence {
		private static final int TEAR = 40;
		private static final int TORN = 82;
		/** How long it takes to climb up onto the wall. */
		private static final int CLIMB = 16;
		private static final int THERE = TORN + CLIMB + 2;
		/** Its leg down into the room, through them, and up they come. */
		private static final int LIFT = THERE + 10;
		private static final int BLACK = LIFT + Lift.RISE + 14;
		private static final int PUNISH = BLACK + 8;
		private static final int OVER = PUNISH + 12;

		private final Haunt haunt;
		private final OccupantEntity entity;
		private final ServerLevel level;
		private final List<BlockPos> roof;
		/** The top of the wall on its side, where it climbs to, and the room it leans over. */
		private final BlockPos wall;
		private final Vec3 inside;
		private final int ceiling;
		@Nullable
		private Vec3 climbFrom;
		@Nullable
		private Vec3 climbTo;
		private final Lift lift;
		private int t;
		private int next;

		private RoofOff(Haunt haunt, OccupantEntity entity, ServerLevel level, List<BlockPos> roof, BlockPos wall, Vec3 inside, int ceiling,
						UUID leader, List<UUID> with) {
			this.haunt = haunt;
			this.entity = entity;
			this.level = level;
			this.roof = roof;
			this.wall = wall;
			this.inside = inside;
			this.ceiling = ceiling;
			this.lift = new Lift(haunt, entity, leader, with);
			entity.setGazeLocked(true);
			entity.setUnmoved(true);
		}

		@Nullable
		static Sequence create(Haunt h, ServerPlayer p, List<ServerPlayer> with) {
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
			// The wall it will climb: the one between it and the nearest of the room.
			BlockPos near = cells.get(0);
			for (BlockPos c : cells) {
				if (Math.hypot(c.getX() + 0.5 - from.x, c.getZ() + 0.5 - from.z) < Math.hypot(near.getX() + 0.5 - from.x, near.getZ() + 0.5 - from.z)) near = c;
			}
			double dx = from.x - (near.getX() + 0.5), dz = from.z - (near.getZ() + 0.5);
			Direction out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			// Whoever is in the same room with them: it lifts them out too.
			List<UUID> inRoom = new ArrayList<>();
			for (ServerPlayer o : with) {
				BlockPos theirs = o.blockPosition().above();
				if (room.contains(BlockPos.asLong(theirs.getX(), 0, theirs.getZ())) && Math.abs(theirs.getY() - head.getY()) <= 2) inRoom.add(o.getUUID());
			}
			return new RoofOff(h, e, level, roof, near.relative(out), Vec3.atBottomCenterOf(near), ceiling, p.getUUID(), inRoom);
		}

		/** On top of what is left of the wall, leaning a little in over the room; null if there is no wall to climb. */
		@Nullable
		private Vec3 perch() {
			for (int y = ceiling + 1; y >= ceiling - 6; y--) {
				BlockPos at = new BlockPos(wall.getX(), y, wall.getZ());
				if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) continue;
				Vec3 top = new Vec3(wall.getX() + 0.5, y + 1.0, wall.getZ() + 0.5);
				Vec3 in = new Vec3(inside.x - top.x, 0.0, inside.z - top.z);
				return in.lengthSqr() > 1.0e-4 ? top.add(in.normalize().scale(0.35)) : top;
			}
			return null;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (entity.hasVanished()) return false;
			entity.keepAlive();
			if (t == 1) {
				// Everyone it is coming for watches it: their view drawn round to it, nothing they press doing anything.
				Cues.effectFor(lift.here(p), ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
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
				// Up onto the wall, over them.
				climbTo = perch();
				if (climbTo != null) {
					climbFrom = entity.position();
					entity.halt();
					entity.setNoGravity(true);
					Cues.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, climbTo, 0.7f, 0.4f);
				}
			}
			if (t > TORN && t <= TORN + CLIMB && climbFrom != null && climbTo != null) {
				// Up first, then over: it does not slide, it hauls itself up.
				double f = (t - TORN) / (double) CLIMB;
				double up = 1.0 - (1.0 - f) * (1.0 - f);
				double over = f * f * (3.0 - 2.0 * f);
				entity.setPos(Mth.lerp(over, climbFrom.x, climbTo.x), Mth.lerp(up, climbFrom.y, climbTo.y), Mth.lerp(over, climbFrom.z, climbTo.z));
				entity.setDeltaMovement(Vec3.ZERO);
			}
			if (t == THERE) {
				Cues.whisper(p, "There you are.", 70);
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 0.6f);
			}
			if (t > TORN && t < LIFT) entity.faceTowards(p.getEyePosition());
			if (t >= LIFT && t < PUNISH) lift.tick(p);
			if (t == LIFT + Lift.RISE) Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 0.5f);
			if (t == BLACK) {
				Cues.effectFor(lift.here(p), ScreenEffectPayload.BLACKOUT, 110, 1f);
				Cues.effectFor(lift.here(p), ScreenEffectPayload.SILENCE, 0, 1f);
			}
			// Every one of them it holds, punished, and let go of. Taken, it does not let go: it is
			// still holding them up when the end begins.
			if (t == PUNISH) {
				punishAll(haunt, p, lift.others(p), lift);
				Cues.effectFor(lift.letGo(p), ScreenEffectPayload.CUTSCENE, 0, 0f);
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
			lift.release(level.getServer());
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
		/** Its leg down the hole, through them, and up they come, out of it. */
		private static final int LIFT = 62;
		private static final int BLACK = LIFT + Lift.RISE + 14;
		private static final int PUNISH = BLACK + 8;
		private static final int OVER = PUNISH + 12;

		private final Haunt haunt;
		private final OccupantEntity entity;
		private final ServerLevel level;
		/** What is over them, a layer at a time from the top down. */
		private final List<List<BlockPos>> layers;
		private final Lift lift;
		private int t;
		private int next;

		private DugOut(Haunt haunt, OccupantEntity entity, ServerLevel level, List<List<BlockPos>> layers, UUID leader, List<UUID> with) {
			this.haunt = haunt;
			this.entity = entity;
			this.level = level;
			this.layers = layers;
			this.lift = new Lift(haunt, entity, leader, with);
			entity.setGazeLocked(true);
			entity.setUnmoved(true);
		}

		@Nullable
		static Sequence create(Haunt h, ServerPlayer p, List<ServerPlayer> with) {
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
			// Whoever is down the same hole with them: it lifts them out too.
			List<UUID> inHole = new ArrayList<>();
			for (ServerPlayer o : with) {
				BlockPos theirs = o.blockPosition().above();
				if (Math.abs(theirs.getX() - head.getX()) <= 1 && Math.abs(theirs.getZ() - head.getZ()) <= 1
						&& Math.abs(theirs.getY() - head.getY()) <= 3) inHole.add(o.getUUID());
			}
			return new DugOut(h, e, level, layers, p.getUUID(), inHole);
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (entity.hasVanished()) return false;
			entity.keepAlive();
			if (t < LIFT) entity.faceTowards(p.getEyePosition());
			if (t == 1) {
				// Everyone it is coming for watches it: their view drawn round to it, nothing they press doing anything.
				Cues.effectFor(lift.here(p), ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
				Cues.whisper(p, "Found you.", 60);
			}
			if (t >= LIFT && t < PUNISH) lift.tick(p);
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
				Cues.effectFor(lift.here(p), ScreenEffectPayload.BLACKOUT, 110, 1f);
				Cues.effectFor(lift.here(p), ScreenEffectPayload.SILENCE, 0, 1f);
			}
			// Every one of them it holds, punished, and let go of. Taken, it does not let go: it is
			// still holding them up when the end begins.
			if (t == PUNISH) {
				punishAll(haunt, p, lift.others(p), lift);
				Cues.effectFor(lift.letGo(p), ScreenEffectPayload.CUTSCENE, 0, 0f);
			}
			return t < OVER;
		}

		@Override
		public void end() {
			lift.release(level.getServer());
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
		/** Whoever hid there with them: what comes of it comes for them too. */
		private final List<UUID> with;
		private int t;

		Taken(Haunt haunt, List<UUID> with) {
			this.haunt = haunt;
			this.with = with;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			if (t == 1) {
				Cues.soundAtEars(p, ModSounds.BREATH, SoundSource.HOSTILE, 1.0f, 0.6f);
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 110, 1f);
				Cues.effectFor(stillHere(p, with, 24.0), ScreenEffectPayload.BLACKOUT, 110, 1f);
			}
			if (t == 12) punishAll(haunt, p, stillHere(p, with, 24.0), null);
			return t < 30;
		}
	}
}
