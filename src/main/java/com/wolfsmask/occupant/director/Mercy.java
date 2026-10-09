package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
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
		if (RESCUE.equals(h.activeId)) {
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

	/** Black, and back at their bed, whole, as if they had only been asleep, with a note. */
	private static void caughtFalling(ServerPlayer player, Director director, Haunt h) {
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0f);
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		Cues.effect(player, ScreenEffectPayload.BLACKOUT, 70, 1f);
		Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
		sendHome(player);
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
}
