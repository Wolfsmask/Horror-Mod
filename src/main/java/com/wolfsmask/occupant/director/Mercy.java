package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * It does not let them die. Not like that.
 * <ul>
 *   <li>A fall that would kill them (or the void): black, and they wake at their bed, or where
 *   they first came into the world, with a note. It doesn't like that.</li>
 *   <li>A monster about to kill them: it is there, behind the monster, and one of its long legs
 *   goes through it and lifts it off the ground. It dies up there, it is gone, and then it says
 *   why. With more than one, they are all lifted and all die at once.</li>
 * </ul>
 * Only once the story has begun, and the monsters only once in a while: otherwise, monsters kill
 * as monsters do.
 */
public final class Mercy {
	/** Not saved from monsters twice within this long, in ticks of play. */
	private static final long MONSTERS_AGAIN_AFTER = 20L * 60 * 10;
	/**
	 * From falls, every time: only not twice within this long, in ticks of the server, which
	 * would mean the catch itself had failed (somewhere to stand that was not safe after all).
	 */
	private static final long FALLS_AGAIN_AFTER = 20L * 3;
	private static final String RESCUE = "mercy";
	/** Caught falling, and brought home. */
	private static final String BROUGHT = "brought";
	private static final String[] FALL_NOTES = {"It doesn't like that.", "Not like that.",
			"You don't get to leave that way.", "It caught you. It will always catch you."};
	private static final String[] ONE = {"I can't have you dying like that.", "Not to that. Never to that.",
			"You're not theirs to take.", "Only I get to watch you."};
	private static final String[] MANY = {"They don't get to have you.", "No one else touches you.",
			"You're mine. Not theirs.", "Nothing else is allowed near you."};

	private Mercy() {
	}

	/**
	 * Called as a player is about to die. False if it stopped them dying (and it has already
	 * begun whatever happens instead); true to let them die.
	 */
	public static boolean allowDeath(ServerPlayer player, DamageSource source) {
		OccupantConfig cfg = OccupantConfig.get();
		Director director = Director.get();
		if (director == null || !cfg.enabled || !cfg.itSavesYou) return true;
		if (player.isCreative() && !cfg.hauntCreative || player.isSpectator() || !Haunt.worldAllowed(player)) return true;
		// /kill is their own choice, and always works, even in the middle of it saving them.
		if (source.is(DamageTypes.GENERIC_KILL)) return true;
		// Nothing saves them from it: that is what it saved them from everything else for.
		if (source.getEntity() instanceof OccupantEntity || com.wolfsmask.occupant.director.events.Strike.isStriking(player)) return true;
		Haunt h = director.haunt(player);
		HauntData d = h.data;
		if (!d.introduced || d.paused || d.ending == LastNightEnding.FOUND) return true;
		// A totem saves them by itself; it lets the totem do it. Not from the void, though, which no
		// totem stops: that one is its to catch.
		if ((player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING))
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;

		// While it is here, in the middle of saving them, nothing else gets them either (an arrow
		// from further off than it froze, say).
		// Nor while it has them for the last time, before the end has begun; nor while it is holding
		// them up at all (nothing but its own scenes takes a player's weight away), carried past
		// whatever they might choke in.
		if (RESCUE.equals(h.activeId) || BROUGHT.equals(h.activeId) || TakenEnding.ID.equals(h.activeId) || h.taking
				|| h.pinned || player.isNoGravity()) {
			player.setHealth(Math.max(player.getHealth(), 2.0f));
			return false;
		}
		boolean fell = source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
		if (fell) {
			long now = Compat.level(player).getServer().getTickCount();
			if (h.mercyFallAt >= 0 && now - h.mercyFallAt < FALLS_AGAIN_AFTER) return true;
			h.mercyFallAt = now;
			caughtFalling(player, director, h);
			return false;
		}
		// A monster: not the great ones (a dragon, a wither, a warden), which it leaves alone.
		if (!(source.getEntity() instanceof Mob attacker) || !(attacker instanceof Enemy) || attacker.getMaxHealth() > 100) return true;
		if (h.mercyMobAt >= 0 && d.playTicks - h.mercyMobAt < MONSTERS_AGAIN_AFTER) return true;
		h.mercyMobAt = d.playTicks;
		// Those after them: whatever has them as its target, and anything close enough to be in it.
		// Not every monster about the place (the one behind a wall that has not noticed them).
		List<Mob> monsters = new ArrayList<>(Compat.level(player).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0),
				m -> m.isAlive() && m instanceof Enemy && !(m instanceof OccupantEntity) && m.getMaxHealth() <= 100
						&& (m.getTarget() == player || m.distanceToSqr(player) < 3.5 * 3.5)));
		if (attacker.isAlive() && !monsters.contains(attacker)) monsters.add(attacker);
		player.setHealth(Math.max(4.0f, player.getMaxHealth() * 0.3f));
		if (monsters.isEmpty()) {
			// Nothing left to take it out on (it blew itself up): only the words.
			say(player, ONE[player.getRandom().nextInt(ONE.length)]);
			return false;
		}
		director.beginNow(player, RESCUE, new Rescue(h, player, monsters, attacker));
		return false;
	}

	/** For the tests: as if it had been a long while since it last saved them from anything. */
	public static void allowAgain(ServerPlayer player) {
		Director director = Director.get();
		if (director == null) return;
		Haunt h = director.haunt(player);
		h.mercyMobAt = -1;
		h.mercyFallAt = -1;
	}

	/**
	 * Black, and back at their bed: and then, in flashes, what it did (see {@link Brought}). If it
	 * cannot be there for it (sound only, or it is out for someone else near), as if they had only
	 * been asleep: whole, with a note.
	 */
	private static void caughtFalling(ServerPlayer player, Director director, Haunt h) {
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		Cues.effectOnly(player, ScreenEffectPayload.BLACKOUT, 40, 1f);
		Cues.effectOnly(player, ScreenEffectPayload.SILENCE, 0, 1f);
		sendHome(player);
		if (!OccupantConfig.get().soundOnly) {
			// Barely alive: what it gives them brings them back.
			player.setHealth(Math.min(player.getMaxHealth(), 3.0f));
			director.beginNow(player, BROUGHT, new Brought(h));
			return;
		}
		wholeAgain(player);
	}

	/** Whole, fed, and a note in their pocket. */
	private static void wholeAgain(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0f);
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		String note = FALL_NOTES[player.getRandom().nextInt(FALL_NOTES.length)];
		ItemStack book = Compat.writtenBook("A note", "?", List.of(note));
		if (!player.getInventory().add(book)) {
			ServerLevel here = Compat.level(player);
			here.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(here, player.getX(), player.getY(), player.getZ(), book));
		}
		Occupant.LOGGER.info("{} would have died falling; it caught them", player.getName().getString());
	}

	/** Back at their bed (or where the world began, if they have none), standing on the ground. */
	public static void sendHome(ServerPlayer player) {
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		ServerLevel overworld = Compat.level(player).getServer().overworld();
		// Their bed only if it is in the overworld, where they are going (an anchor is not).
		BlockPos bed = player.level().dimension() == Level.OVERWORLD ? Compat.respawnPos(player) : null;
		BlockPos centre = bed != null ? bed : Compat.spawnPos(overworld);
		// Loaded first: far from them, it is not, and there the ground reads as the very bottom of
		// the world, inside the bedrock.
		for (int cx = (centre.getX() - 3) >> 4; cx <= (centre.getX() + 3) >> 4; cx++) {
			for (int cz = (centre.getZ() - 3) >> 4; cz <= (centre.getZ() + 3) >> 4; cz++) overworld.getChunk(cx, cz);
		}
		BlockPos feet = standNear(overworld, centre);
		if (feet == null) {
			int top = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
			feet = new BlockPos(centre.getX(), top, centre.getZ());
		}
		teleport(player, feet);
	}

	@Nullable
	private static BlockPos standNear(ServerLevel world, BlockPos centre) {
		for (int r = 1; r <= 3; r++) {
			for (int[] o : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
				BlockPos feet = Spots.groundNear(world, centre.getX() + o[0], centre.getY(), centre.getZ() + o[1], 3);
				if (feet != null) return feet;
			}
		}
		return null;
	}

	/** By the game's own command, the same on every version, into the overworld. */
	private static void teleport(ServerPlayer player, BlockPos feet) {
		MinecraftServer server = Compat.level(player).getServer();
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.2f %d %.2f",
							player.getStringUUID(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
		} catch (RuntimeException e) {
			Occupant.LOGGER.warn("Could not bring {} back", player.getName().getString(), e);
		}
	}

	private static void say(ServerPlayer player, String line) {
		if (OccupantConfig.get().screenWhispers) {
			Cues.whisper(player, line, 110);
		} else {
			player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/**
	 * It comes for what was about to kill them, and they watch: their view is drawn round to it,
	 * nothing they press moves them, and the picture narrows to a band, until it is over.
	 * <ol>
	 *   <li>It is there, behind the one that nearly had them, and their eyes are drawn to it.</li>
	 *   <li>Any it cannot reach, it does not need to: their eyes are drawn to each in turn, and one
	 *   by one they are simply gone.</li>
	 *   <li>Its legs go into the rest, one after another, in at the back and out through the front
	 *   (eight at most: it stands on the other two). Each flinches, and is lifted off the ground,
	 *   slowly, hanging loose on the leg, its head going down.</li>
	 *   <li>They die up there and are gone. Then it is gone. Then it says why.</li>
	 * </ol>
	 * In sound-only mode it is never seen, even now: the monsters are lifted by nothing, and their
	 * view stays their own.
	 */
	private static final class Rescue implements Sequence {
		/** Legs it can put through something: it has ten, and stands on two. */
		private static final int LEGS_FREE = 8;
		/** How far from where it stands its legs go into something, along the ground. */
		private static final double REACH = 3.2;
		/** It is there. */
		private static final int APPEAR = 2;
		/** Long enough for their view to come round to it. */
		private static final int LOOK = 22;
		/** From a leg going in to the lift beginning; and the lift itself. */
		private static final int LIFT_AFTER = 5;
		private static final int LIFT_FOR = 26;
		/** Held up, still alive, a moment before they die. */
		private static final int HANG = 14;
		/** The game takes twenty ticks to lay the dead down and take them away; then it goes. */
		private static final int AFTER_DEATH = 28;
		/** At most this many turns at being looked at and gone: in a crowd, the rest go all together, last. */
		private static final int VANISH_TURNS = 10;
		/** How near anyone else may come to it while it is busy, before it puts them down. */
		private static final double TOO_CLOSE = 3.2;
		/** How long putting them down takes; and how long before it will do it to the same one again. */
		private static final int PUSH_FOR = 18;
		private static final int PUSH_AGAIN = 45;
		private static final String[] WAIT = {"Wait your turn.", "I said wait.", "Your turn is coming."};

		private final ServerPlayer player;
		/** Where the monsters are (and stay, whatever happens to them: through a portal, say). */
		private final ServerLevel level;
		private final Haunt haunt;
		private final List<Mob> monsters;
		private final List<Vec3> from = new ArrayList<>();
		private final List<Double> lift = new ArrayList<>();
		/** Those its legs go into, nearest it first, and when each goes in. */
		private final List<Mob> stabbed = new ArrayList<>();
		private final List<Integer> stabAt = new ArrayList<>();
		/** Those it cannot reach, gone one by one. */
		private final List<Mob> vanishing = new ArrayList<>();
		private final List<Mob> holding = new ArrayList<>();
		private final int vanishStart;
		private final int vanishEvery;
		private final int dies;
		private final int gone;
		private final int say;
		private final int over;
		/** Whether they are watching it as a scene (and so must be let go of at the end). */
		private final boolean scene;
		@Nullable
		private OccupantEntity entity;
		private int age;
		/** Whoever it is putting down now, and since when; when it last did it to each, and how often. */
		@Nullable
		private java.util.UUID pushing;
		private int pushStart;
		private final java.util.Map<java.util.UUID, Integer> pushedAt = new java.util.HashMap<>();
		private final java.util.Map<java.util.UUID, Integer> pushes = new java.util.HashMap<>();

		Rescue(Haunt haunt, ServerPlayer player, List<Mob> monsters, Mob attacker) {
			this.player = player;
			this.haunt = haunt;
			this.monsters = monsters;
			Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
			ServerLevel world = Compat.level(player);
			this.level = world;
			MinecraftServer server = world.getServer();
			double want = monsters.size() == 1 ? 1.8 : 1.5;
			for (Mob m : monsters) {
				// Off whatever it rides (a jockey's chicken, a spider), or it would be held down on it.
				if (m.isPassenger()) m.stopRiding();
				from.add(m.position());
				lift.add(Math.min(want, room(world, m)));
				// After no one: a zombie hurt while it has someone to go for calls others to help.
				m.setTarget(null);
				m.setNoAi(true);
				m.setNoGravity(true);
				m.setDeltaMovement(Vec3.ZERO);
				// A creeper already hissing would still go off while it is held: its fuse is put out,
				// by the game's own command, the same on every version.
				if (com.wolfsmask.occupant.util.Kinds.is(m, "creeper")) {
					try {
						server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
								"data merge entity " + m.getStringUUID() + " {Fuse:32767s,ignited:0b}");
					} catch (RuntimeException e) {
						Occupant.LOGGER.debug("Could not put out a creeper's fuse", e);
					}
				}
			}
			// It stands behind the one that nearly had them (or, if that one is gone, the nearest),
			// on the far side from them.
			// If there is nowhere behind that one (its back to a wall), behind the next nearest them.
			List<Mob> behind = new ArrayList<>(monsters);
			behind.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
			if (monsters.contains(attacker)) {
				behind.remove(attacker);
				behind.add(0, attacker);
			}
			Mob first = behind.get(0);
			// Close enough that a leg reaches it easily, far enough that the leg is seen to reach; and
			// on the same side of any wall as the monster, so the leg never goes through one.
			// Straight behind it, or a little to either side if that is no good (a drop, a wall,
			// water, leaves overhead): wherever its legs reach the most of them, straight behind if
			// that is as good as any.
			BlockPos feet = null;
			Mob by = first;
			for (Mob m : behind.subList(0, Math.min(4, behind.size()))) {
				Vec3 out = m.position().subtract(player.position());
				out = new Vec3(out.x, 0, out.z);
				out = out.lengthSqr() < 1.0E-4 ? Sight.flatLook(player) : out.normalize();
				int most = -1;   // any good place at all, before one that reaches more
				for (double turn : new double[]{0.0, 30.0, -30.0, 60.0, -60.0}) {
					Vec3 way = Sight.rotateY(out, turn);
					for (double d : monsters.size() == 1 ? new double[]{2.4, 1.8, 3.0, 1.3} : new double[]{3.0, 2.2, 1.5}) {
						Vec3 aim = m.position().add(way.scale(d));
						BlockPos at = Spots.groundNear(world, Mth.floor(aim.x), m.getBlockY(), Mth.floor(aim.z), 2);
						if (at == null || !clear(world, m, Vec3.atBottomCenterOf(at).add(0.0, 1.2, 0.0))) continue;
						int reached = 0;
						for (Mob o : monsters) if (reach(o, Vec3.atBottomCenterOf(at))) reached++;
						if (reached > most) {
							most = reached;
							feet = at;
							by = m;
						}
						if (monsters.size() == 1) break;   // alone, the first good place will do
					}
					if (feet != null && monsters.size() == 1) break;
				}
				if (feet != null) break;
			}
			if (feet != null && !OccupantConfig.get().soundOnly) {
				entity = haunt.spawnOccupant(player, feet, OccupantEntity.Mode.AMBUSH, OccupantEntity.Form.REVEALED);
				if (entity != null) {
					entity.setFootsteps(false);
					entity.setGazeLocked(true);
					entity.setUnmoved(true);
				}
			}
			if (entity == null) {
				if (!OccupantConfig.get().soundOnly) {
					Occupant.LOGGER.info("It could not come for {}: {}", player.getName().getString(),
							feet == null ? "nowhere to stand" : "it could not be put at " + feet);
				}
				// Lifted by nothing: all of them, together.
				stabbed.addAll(monsters);
			} else {
				// The one that nearly had them first (if a leg reaches it from where it had to stand;
				// else the one it stands behind); then whatever else is in reach, nearest first, as
				// long as it has legs to spare. The rest it does not need to touch.
				Vec3 stands = Vec3.atBottomCenterOf(feet);
				Mob lead = reach(first, stands) ? first : by;
				stabbed.add(lead);
				List<Mob> rest = new ArrayList<>(monsters);
				rest.remove(lead);
				rest.sort((a, b) -> Double.compare(flat(a.position(), stands), flat(b.position(), stands)));
				for (Mob m : rest) {
					if (reach(m, stands) && stabbed.size() < LEGS_FREE) stabbed.add(m);
					else vanishing.add(m);
				}
				Occupant.LOGGER.info("It came for {}: {} after them, {} on its legs, {} gone without it (it stands {} from the one that hit them)",
						player.getName().getString(), monsters.size(), stabbed.size(), vanishing.size(),
						String.format(Locale.ROOT, "%.1f", flat(first.position(), stands)));
			}
			// The scene, laid out.
			int t = APPEAR + LOOK;
			this.vanishStart = t;
			int turns = Math.min(vanishing.size(), VANISH_TURNS);
			this.vanishEvery = vanishing.isEmpty() ? 0 : Mth.clamp(110 / turns, 8, 16);
			if (!vanishing.isEmpty()) t += turns * vanishEvery + 14;   // and their eyes come back to it
			for (int i = 0; i < stabbed.size(); i++) stabAt.add(t + 4 * i);
			int lifted = t + 4 * (stabbed.size() - 1) + LIFT_AFTER + LIFT_FOR;
			this.dies = lifted + HANG;
			this.gone = dies + AFTER_DEATH;
			this.say = gone + 8;
			this.over = say + 14;
			this.scene = entity != null;
			// It is ended from here when it is over (however slow the server is running); the
			// length only lets them go by themselves if that word never comes.
			if (scene) Cues.effect(player, ScreenEffectPayload.CUTSCENE, over + 100, 1f);
		}

		@Override
		public boolean tick(ServerPlayer p) {
			age++;
			if (entity != null) {
				if (entity.hasVanished()) entity = null;
				else entity.keepAlive();
			}
			// Anyone else coming up to it while it is busy is put down, and it goes on.
			if (entity != null && age > APPEAR && age < gone) keepBack(p);
			// 1. It is there, already facing them.
			if (age == APPEAR && entity != null && entity.isConcealed()) {
				entity.faceTowards(p.getEyePosition());
				entity.setConcealed(false);
			}
			// 2. The ones it does not need to touch: looked at, one by one, and gone.
			for (int i = 0; i < vanishing.size(); i++) {
				Mob m = vanishing.get(i);
				int at = vanishStart + Math.min(i, VANISH_TURNS - 1) * vanishEvery;
				// (A crowd going together: their eyes on the first of them, and one sound for them all.)
				boolean first = i < VANISH_TURNS;
				if (age == at && entity != null && m.isAlive() && first) entity.setFocus(m);
				if (age == at + vanishEvery * 3 / 4 && m.isAlive()) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(),
							10, 0.25, 0.4, 0.25, 0.01);
					if (first) Cues.sound(p, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, m.position(), 0.6f, 0.5f);
					m.discard();
				}
			}
			if (!vanishing.isEmpty() && age == vanishStart + Math.min(vanishing.size(), VANISH_TURNS) * vanishEvery && entity != null) {
				entity.setFocus(null);
			}
			// 3. Its legs go in, one after another, and each one flinches.
			for (int i = 0; i < stabbed.size(); i++) {
				if (age != stabAt.get(i) || entity == null) continue;
				Mob m = stabbed.get(i);
				if (!m.isAlive()) continue;
				holding.add(m);
				entity.setHeld(holding);
				Cues.sound(p, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, m.position(), 1.0f, 0.55f + 0.08f * i);
				m.hurtServer(level, sourceFor(m, m.damageSources().generic()), 0.5f);
			}
			// Held where they were, then lifted off the ground on the legs, slowly. The leg going in
			// shoves each one forward a little, and it swings back; its head goes down.
			if (age <= dies) {
				for (int i = 0; i < monsters.size(); i++) {
					Mob m = monsters.get(i);
					if (!m.isAlive()) continue;
					Vec3 a = from.get(i);
					int s = stabbed.indexOf(m);
					double up = 0.0, push = 0.0;
					Vec3 shove = Vec3.ZERO;
					if (s >= 0) {
						int since = age - stabAt.get(s);
						float t = Mth.clamp((since - LIFT_AFTER) / (float) LIFT_FOR, 0.0f, 1.0f);
						up = lift.get(i) * (t * t * (3 - 2 * t));
						if (since > 0 && entity != null) {
							push = 0.25 * Math.exp(-since / 3.0);
							Vec3 d = a.subtract(entity.position());
							d = new Vec3(d.x, 0, d.z);
							if (d.lengthSqr() > 1.0E-4) shove = d.normalize();
							float x = m.getXRot();
							m.setXRot(x + Math.min(6.0f, Math.max(-6.0f, 70.0f - x)));
						}
					}
					m.setPos(a.x + shove.x * push, a.y + up, a.z + shove.z * push);
					m.setDeltaMovement(Vec3.ZERO);
				}
			}
			// 4. They die, up there.
			if (age == dies) {
				for (Mob m : stabbed) {
					if (!m.isAlive()) continue;
					level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 12, 0.2, 0.3, 0.2, 0.01);
					// A slime that died would only come apart into more of them, and go for them again.
					if (com.wolfsmask.occupant.util.Kinds.is(m, "slime") || com.wolfsmask.occupant.util.Kinds.is(m, "magma_cube")) m.discard();
					else m.hurtServer(level, sourceFor(m, m.damageSources().genericKill()), Float.MAX_VALUE);
				}
			}
			// 5. It is gone.
			if (age == gone && entity != null) {
				entity.vanish();
				entity = null;
			}
			// 6. And it says why; and they are whole again, as if none of it had touched them.
			if (age == say) {
				String[] lines = monsters.size() == 1 ? ONE : MANY;
				say(p, lines[p.getRandom().nextInt(lines.length)]);
				p.setHealth(p.getMaxHealth());
				p.getFoodData().setFoodLevel(20);
				p.getFoodData().setSaturation(5.0f);
			}
			return age < over;
		}

		/**
		 * Someone else, coming up to it while it saves them. Without letting go of anything it holds,
		 * a spare leg comes down on them: they are thrown back onto the ground, and it tells them to
		 * wait their turn. Its eyes go to them and back, and it carries on as if they had never come.
		 */
		private void keepBack(ServerPlayer saved) {
			OccupantEntity it = entity;
			if (it == null) return;
			if (pushing == null) {
				for (ServerPlayer o : Party.others(saved, 24.0)) {
					if (flat(o.position(), it.position()) > TOO_CLOSE || Math.abs(o.getY() - it.getY()) > 3.0) continue;
					Integer last = pushedAt.get(o.getUUID());
					if (last != null && age - last < PUSH_AGAIN) continue;
					pushing = o.getUUID();
					pushStart = age;
					pushedAt.put(o.getUUID(), age);
					break;
				}
				if (pushing == null) return;
			}
			ServerPlayer o = level.getServer().getPlayerList().getPlayer(pushing);
			int k = age - pushStart;
			if (o == null || !o.isAlive() || o.level() != level || k > PUSH_FOR) {
				// Done with them: what it holds, it holds as before, and its eyes go back to the one it is saving.
				it.setHeld(holding);
				it.setGazeLocked(true);
				pushing = null;
				return;
			}
			if (k == 0) {
				// A spare leg, as well as the ones it has through them: it lets go of nothing.
				List<net.minecraft.world.entity.Entity> legs = new ArrayList<>(holding);
				legs.add(o);
				it.setHeld(legs);
				it.setGazeLocked(false);
				Cues.sound(saved, ModSounds.BREATH, SoundSource.HOSTILE, it.getEyePosition(), 0.8f, 0.55f);
			}
			if (k < PUSH_FOR - 4) it.getLookControl().setLookAt(o, 40.0f, 40.0f);
			if (k == 4) {
				// Down: back off its feet, away from it, and onto the ground.
				Vec3 away = o.position().subtract(it.position());
				away = new Vec3(away.x, 0.0, away.z);
				away = away.lengthSqr() < 1.0E-4 ? Sight.flatLook(o).scale(-1.0) : away.normalize();
				o.setDeltaMovement(away.x * 0.85, 0.32, away.z * 0.85);
				o.hurtMarked = true;
				Cues.sound(saved, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, o.position(), 0.9f, 0.45f);
				Cues.sound(saved, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, o.position(), 1.0f, 0.6f);
				Cues.effectOnly(o, ScreenEffectPayload.STATIC, 8, 0.5f);
			}
			if (k == 7) {
				// Slammed down onto the ground: it means it.
				o.setDeltaMovement(o.getDeltaMovement().x * 0.4, -0.9, o.getDeltaMovement().z * 0.4);
				o.hurtMarked = true;
				o.resetFallDistance();
			}
			if (k == 9) {
				int n = pushes.merge(o.getUUID(), 1, Integer::sum);
				Cues.whisper(o, WAIT[Math.min(n, WAIT.length) - 1], 70);
			}
		}

		private static double flat(Vec3 a, Vec3 b) {
			double dx = a.x - b.x, dz = a.z - b.z;
			return Math.sqrt(dx * dx + dz * dz);
		}

		/**
		 * What the leg does to it, as the game counts it: from nothing in particular, except to an
		 * enderman, which hurt by nothing alive leaps somewhere else, and so is hurt by it.
		 */
		private DamageSource sourceFor(Mob m, DamageSource otherwise) {
			return entity != null && com.wolfsmask.occupant.util.Kinds.is(m, "enderman") ? m.damageSources().mobAttack(entity) : otherwise;
		}

		/** Whether a leg reaches {@code m} from where it stands. */
		private static boolean reach(Mob m, Vec3 stands) {
			return flat(m.position(), stands) <= REACH && Math.abs(m.getY() - stands.y) <= 2.5;
		}

		/** Nothing solid between the middle of {@code m} and {@code to}. */
		private static boolean clear(ServerLevel world, Mob m, Vec3 to) {
			Vec3 middle = m.position().add(0.0, m.getBbHeight() * 0.5, 0.0);
			return world.clip(new net.minecraft.world.level.ClipContext(middle, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, m)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
		}

		/** Clear space over its head, in blocks, up to two: indoors, it is not lifted into the ceiling. */
		private static double room(ServerLevel world, Mob m) {
			double top = m.getY() + m.getBbHeight();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (double y = top; y < top + 2.0; y += 0.25) {
				p.set(Mth.floor(m.getX()), Mth.floor(y), Mth.floor(m.getZ()));
				var shape = world.getBlockState(p).getCollisionShape(world, p);
				if (!shape.isEmpty() && y - p.getY() >= shape.min(net.minecraft.core.Direction.Axis.Y) - 1.0E-3) {
					return Math.max(0.0, p.getY() + shape.min(net.minecraft.core.Direction.Axis.Y) - top - 0.05);
				}
			}
			return 2.0;
		}

		@Override
		public void end() {
			for (Mob m : monsters) {
				if (!m.isAlive()) continue;
				m.setNoAi(false);
				m.setNoGravity(false);
			}
			if (entity != null) entity.vanish();
			// Their view is their own again.
			if (scene) {
				try {
					Cues.effect(player, ScreenEffectPayload.CUTSCENE, 0, 0f);
				} catch (RuntimeException e) {
					Occupant.LOGGER.debug("Could not end the scene for {}", player.getName().getString(), e);
				}
			}
		}

		@Nullable
		@Override
		public OccupantEntity occupant() {
			return entity;
		}
	}

	/**
	 * Caught falling, they see what it does instead of letting them die, in flashes, with the dark
	 * between: dragged along the ground towards their bed on a leg through them; its face over
	 * them, a bottle held to their mouth on the end of a leg, and they drink; dragged again; the
	 * bottle again; dragged the last of the way. Then black, and they are at their bed, whole, and it
	 * is gone, and there is a note. Anyone near sees it all happen.
	 * <p>
	 * If it cannot be there for it (somewhere it cannot stand, or out already for someone else
	 * near), the flashes are only the dark, and they wake whole, the same.
	 */
	private static final class Brought implements Sequence {
		/** Each flash, and the dark between them. */
		private static final int SHOT = 30;
		private static final int CUT = 5;
		/** The flashes, in order: dragged (true), or the bottle (false). */
		private static final boolean[] DRAGGED = {true, false, true, false, true};
		/** In the black first, while it is made ready; the first flash comes up out of it. */
		private static final int FIRST = 10;
		private static final int END = FIRST + DRAGGED.length * (SHOT + CUT);
		private static final int HOME = END + 8;
		private static final int OVER = END + 34;
		/** How far, at most, it drags them, and how far ahead of them it walks. */
		private static final int FURTHEST = 9;
		private static final double AHEAD = 1.7;
		/** How far off it stands while it holds the bottle to their mouth. */
		private static final double BOTTLE_FROM = 2.4;
		/** Ticks into a bottle flash when they drink. */
		private static final int DRINK = 18;

		private final Haunt haunt;
		@Nullable
		private MinecraftServer server;
		@Nullable
		private java.util.UUID who;
		@Nullable
		private OccupantEntity entity;
		@Nullable
		private net.minecraft.world.entity.item.ItemEntity bottle;
		/** Where the dragging begins, and where it ends: at their bed. */
		private Vec3 from = Vec3.ZERO;
		private Vec3 to = Vec3.ZERO;
		/** How far along the way they have been dragged, 0 to 1. */
		private double along;
		private int t;

		Brought(Haunt haunt) {
			this.haunt = haunt;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			t++;
			p.resetFallDistance();
			if (t == 1) {
				server = Compat.level(p).getServer();
				who = p.getUUID();
				// Theirs alone to watch: the others near see it in the world.
				Cues.effectOnly(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
				Cues.effectOnly(p, ScreenEffectPayload.BLACKOUT, FIRST + 4, 1f);
			}
			// A tick on, once they are really home: the way it brings them, and it.
			if (t == 2) ready(p);
			if (entity != null) {
				if (entity.hasVanished()) entity = null;
				else entity.keepAlive();
			}
			if (t >= FIRST && t < END) {
				int k = t - FIRST;
				int shot = k / (SHOT + CUT);
				int in = k % (SHOT + CUT);
				if (in < SHOT) {
					if (DRAGGED[shot]) dragged(p, shot, in);
					else bottle(p, in);
				} else if (in == SHOT) {
					// In the dark between: everything moved to where the next flash finds it.
					cut(p);
					if (shot + 1 < DRAGGED.length) setUp(p, DRAGGED[shot + 1]);
				}
			}
			if (t == END) {
				Cues.effectOnly(p, ScreenEffectPayload.BLACKOUT, OVER - END + 10, 1f);
				lookAway(p);
				gone();
			}
			if (t == HOME) {
				// At their bed, standing, whole; and its note.
				sendHome(p);
				wholeAgain(p);
			}
			if (t == OVER) Cues.effectOnly(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
			return t < OVER;
		}

		/** The way back to their bed it drags them along (the longest level run of ground, up to nine blocks), and it, ahead. */
		private void ready(ServerPlayer p) {
			ServerLevel level = Compat.level(p);
			BlockPos home = p.blockPosition();
			to = p.position();
			Vec3 best = null;
			int bestLen = 0;
			float turn = p.getRandom().nextFloat() * 45.0f;
			for (int k = 0; k < 8; k++) {
				Vec3 dir = Sight.rotateY(new Vec3(0.0, 0.0, 1.0), turn + k * 45.0f);
				int len = 0;
				for (int i = 1; i <= FURTHEST; i++) {
					BlockPos at = Spots.groundNear(level, Mth.floor(to.x + dir.x * i), home.getY(), Mth.floor(to.z + dir.z * i), 0);
					if (at == null) break;
					len = i;
				}
				if (len > bestLen) {
					bestLen = len;
					best = dir;
				}
			}
			if (best == null || bestLen < 3) {
				// Nowhere to drag them: it is there, with them, where they are.
				best = Sight.flatLook(p);
				bestLen = 0;
			}
			from = to.add(best.scale(bestLen));
			if (bestLen > 0) teleport(p, BlockPos.containing(from));
			Vec3 ahead = from.add(best.scale(-AHEAD));
			BlockPos feet = Spots.groundNear(level, Mth.floor(ahead.x), home.getY(), Mth.floor(ahead.z), 2);
			if (feet == null) feet = Spots.groundNear(level, Mth.floor(from.x + best.x * AHEAD), home.getY(), Mth.floor(from.z + best.z * AHEAD), 2);
			if (feet != null) entity = haunt.spawnOccupant(p, feet, OccupantEntity.Mode.AMBUSH, OccupantEntity.Form.REVEALED);
			if (entity != null) {
				// They are in the black: it is simply there when they can see.
				entity.setConcealed(false);
				entity.setGazeLocked(true);
				entity.setUnmoved(true);
				entity.setFootsteps(true);
			} else if (bestLen > 0) {
				// It cannot be there for it: they stay where they woke, and the flashes are only the dark.
				teleport(p, home);
				from = to;
			}
			// Still in the black: the first flash set up.
			setUp(p, DRAGGED[0]);
		}

		/** Where it, and what it holds, are when the next flash comes up (done in the dark, never seen to jump). */
		private void setUp(ServerPlayer p, boolean drag) {
			OccupantEntity it = entity;
			if (it == null) return;
			Vec3 way = to.subtract(from);
			way = way.lengthSqr() < 1.0E-4 ? Sight.flatLook(p) : new Vec3(way.x, 0.0, way.z).normalize();
			if (drag) {
				it.setFocus(null);
				it.setHeld(List.of(p));
				Vec3 at = p.position().add(way.scale(AHEAD));
				it.setPos(at.x, it.getY(), at.z);
				it.setDeltaMovement(Vec3.ZERO);
				Cues.sound(p, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, p.getEyePosition(), 0.7f, 0.5f);
				return;
			}
			// Over them, a bottle on the end of a leg at their mouth: far enough off that its face
			// and the bottle are both in what they see.
			Vec3 over = p.position().add(way.scale(BOTTLE_FROM));
			it.setPos(over.x, it.getY(), over.z);
			it.setDeltaMovement(Vec3.ZERO);
			Vec3 eye = p.getEyePosition();
			Vec3 mouth = eye.add(way.scale(0.55)).add(0.0, -0.12, 0.0);
			ServerLevel level = Compat.level(p);
			net.minecraft.world.entity.item.ItemEntity b = new net.minecraft.world.entity.item.ItemEntity(level, mouth.x, mouth.y, mouth.z, Compat.healingPotion());
			b.setNoGravity(true);
			b.setNeverPickUp();
			b.setUnlimitedLifetime();
			b.setDeltaMovement(Vec3.ZERO);
			if (level.addFreshEntity(b)) {
				bottle = b;
				it.setHeld(List.of(b));
			}
			// Their eyes half way between its face, up over them, and the bottle at their mouth: the
			// face at the top of what they see, the bottle at the bottom.
			double face = it.getY() + Sight.drawnBlocks(BOTTLE_FROM, it.getAct()) * 0.86 - eye.y;
			double up = (Math.atan2(face, BOTTLE_FROM) + Math.atan2(mouth.y - eye.y, 0.55)) / 2.0;
			Cues.lookAt(p, eye.add(way.scale(2.0)).add(0.0, 2.0 * Math.tan(up), 0.0));
		}

		/**
		 * Dragged: along the ground towards their bed, on a leg through them, it walking backwards
		 * ahead of them, watching them come.
		 */
		private void dragged(ServerPlayer p, int shot, int in) {
			int drags = 0, before = 0;
			for (int i = 0; i < DRAGGED.length; i++) {
				if (!DRAGGED[i]) continue;
				if (i < shot) before++;
				drags++;
			}
			OccupantEntity it = entity;
			if (it == null) return;
			double f = Mth.clamp((before + in / (double) SHOT) / drags, 0.0, 1.0);
			along = Math.max(along, f);
			Vec3 want = from.lerp(to, along);
			Vec3 v = new Vec3(want.x - p.getX(), 0.0, want.z - p.getZ());
			if (v.length() > 0.5) v = v.normalize().scale(0.5);
			p.setDeltaMovement(v.x * 0.8, Math.min(p.getDeltaMovement().y, 0.0), v.z * 0.8);
			p.hurtMarked = true;
			// It walks backwards ahead of them towards their bed, watching them come.
			Vec3 way = to.subtract(from);
			way = way.lengthSqr() < 1.0E-4 ? Sight.flatLook(p) : new Vec3(way.x, 0.0, way.z).normalize();
			Vec3 at = p.position().add(way.scale(AHEAD));
			it.setPos(at.x, it.getY() + Mth.clamp(p.getY() - it.getY(), -0.2, 0.2), at.z);
			it.setDeltaMovement(Vec3.ZERO);
			if (in % 7 == 3) {
				BlockPos under = p.blockPosition().below();
				Cues.sound(p, Compat.level(p).getBlockState(under).getSoundType().getStepSound(), SoundSource.PLAYERS,
						p.position(), 0.6f, 0.6f);
			}
		}

		/** Its face, over them, and a bottle on the end of a leg, held to their mouth; and they drink. */
		private void bottle(ServerPlayer p, int in) {
			OccupantEntity it = entity;
			if (it == null) return;
			p.setDeltaMovement(0.0, Math.min(p.getDeltaMovement().y, 0.0), 0.0);
			p.hurtMarked = true;
			if (in == 0) Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, it.getEyePosition(), 0.8f, 0.6f);
			net.minecraft.world.entity.item.ItemEntity b = bottle;
			if (b != null && !b.isRemoved()) b.setDeltaMovement(Vec3.ZERO);
			if (in == DRINK) {
				Cues.sound(p, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, p.getEyePosition(), 1.0f, 0.9f);
				if (b != null) b.discard();
				bottle = null;
				it.setHeld(List.of());
				float health = Math.min(p.getMaxHealth(), p.getHealth() + p.getMaxHealth() * 0.45f);
				p.setHealth(health);
				p.getFoodData().setFoodLevel(Math.min(20, p.getFoodData().getFoodLevel() + 8));
				// Seen by anyone watching; under where they are looking themselves.
				Compat.level(p).sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 0.9, p.getZ(), 3, 0.35, 0.2, 0.35, 0.02);
			}
			if (in == SHOT - 1) lookAway(p);
		}

		/** The dark between two flashes: a stutter of the light, and the static. */
		private void cut(ServerPlayer p) {
			Cues.effectOnly(p, ScreenEffectPayload.FLICKER, CUT + 1, 1f);
			Cues.effectOnly(p, ScreenEffectPayload.STATIC, CUT, 0.45f);
		}

		private void lookAway(ServerPlayer p) {
			Cues.lookFree(p);
		}

		/** It, and its bottle, gone. */
		private void gone() {
			if (bottle != null) bottle.discard();
			bottle = null;
			if (entity != null) entity.vanish();
			entity = null;
		}

		@Override
		public void end() {
			gone();
			// Cut short: home, whole, and their view their own again.
			if (t >= HOME || server == null || who == null) return;
			ServerPlayer p = server.getPlayerList().getPlayer(who);
			if (p == null) return;
			try {
				if (p.isAlive()) {
					sendHome(p);
					wholeAgain(p);
				}
				Cues.lookFree(p);
				Cues.effectOnly(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
			} catch (RuntimeException e) {
				Occupant.LOGGER.warn("Could not bring {} home", p.getName().getString(), e);
			}
		}

		@Override
		@Nullable
		public OccupantEntity occupant() {
			return entity;
		}
	}
}
