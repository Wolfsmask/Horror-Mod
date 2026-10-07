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
 *   goes through it and lifts it off the ground. Then it says why, and walks away into the fog.
 *   With more than one, they all die at once.</li>
 * </ul>
 * Only once the story has begun, and the monsters only once in a while: otherwise, monsters kill
 * as monsters do.
 */
public final class Mercy {
	/** Not saved from monsters twice within this long, in ticks of play. */
	private static final long MONSTERS_AGAIN_AFTER = 20L * 60 * 10;
	/** Nor from falls (a guard against any loop), in ticks of play. */
	private static final long FALLS_AGAIN_AFTER = 20L * 30;
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
		Haunt h = director.haunt(player);
		HauntData d = h.data;
		if (!d.introduced || d.paused || d.ending == LastNightEnding.FOUND) return true;
		// A totem saves them by itself; it lets the totem do it.
		if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return true;

		boolean fell = source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
		if (fell) {
			if (h.mercyFallAt >= 0 && d.playTicks - h.mercyFallAt < FALLS_AGAIN_AFTER) return true;
			h.mercyFallAt = d.playTicks;
			caughtFalling(player, director, h);
			return false;
		}
		// A monster: not the great ones (a dragon, a wither, a warden), which it leaves alone.
		if (!(source.getEntity() instanceof Mob attacker) || !(attacker instanceof Enemy) || attacker.getMaxHealth() > 100) return true;
		if (h.mercyMobAt >= 0 && d.playTicks - h.mercyMobAt < MONSTERS_AGAIN_AFTER) return true;
		h.mercyMobAt = d.playTicks;
		List<Mob> monsters = new ArrayList<>(Compat.level(player).getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0),
				m -> m.isAlive() && m instanceof Enemy && !(m instanceof OccupantEntity) && m.getMaxHealth() <= 100));
		if (attacker.isAlive() && !monsters.contains(attacker)) monsters.add(attacker);
		player.setHealth(Math.max(4.0f, player.getMaxHealth() * 0.3f));
		if (monsters.isEmpty()) {
			// Nothing left to take it out on (it blew itself up): only the words.
			say(player, ONE[player.getRandom().nextInt(ONE.length)]);
			return false;
		}
		director.beginNow(player, "mercy", new Rescue(h, player, monsters));
		return false;
	}

	/** Black, and back at their bed, with a note. */
	private static void caughtFalling(ServerPlayer player, Director director, Haunt h) {
		player.setHealth(Math.max(2.0f, player.getMaxHealth() * 0.25f));
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		Cues.effect(player, ScreenEffectPayload.BLACKOUT, 70, 1f);
		Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
		ServerLevel overworld = Compat.level(player).getServer().overworld();
		// Their bed only if it is in the overworld, where they are going (an anchor is not).
		BlockPos bed = player.level().dimension() == Level.OVERWORLD ? Compat.respawnPos(player) : null;
		BlockPos centre = bed != null ? bed : Compat.spawnPos(overworld);
		BlockPos feet = standNear(overworld, centre);
		if (feet == null) {
			int top = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
			feet = new BlockPos(centre.getX(), top, centre.getZ());
		}
		teleport(player, feet);
		String note = FALL_NOTES[player.getRandom().nextInt(FALL_NOTES.length)];
		ItemStack book = Compat.writtenBook("A note", "?", List.of(note));
		if (!player.getInventory().add(book)) {
			ServerLevel here = Compat.level(player);
			here.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(here, player.getX(), player.getY(), player.getZ(), book));
		}
		Occupant.LOGGER.info("{} would have died falling; it caught them", player.getName().getString());
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
	 * It comes for what was about to kill them. The light stutters, and in the dark it is there,
	 * behind the monster; one leg through it, lifting it off the ground; then it is dead, and it
	 * says why, and goes. With more than one, they all die together.
	 */
	private static final class Rescue implements Sequence {
		private static final int ARRIVE = 2;
		private static final int LIFTED = 16;
		private static final int KILL = 22;
		private final Haunt haunt;
		private final List<Mob> monsters;
		private final List<Vec3> from = new ArrayList<>();
		@Nullable
		private OccupantEntity entity;
		private int age;
		private boolean killed;
		@Nullable
		private Vec3 away;

		Rescue(Haunt haunt, ServerPlayer player, List<Mob> monsters) {
			this.haunt = haunt;
			this.monsters = monsters;
			Cues.effect(player, ScreenEffectPayload.FLICKER, 30, 1f);
			Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
			for (Mob m : monsters) {
				from.add(m.position());
				m.setNoAi(true);
				m.setNoGravity(true);
				m.setDeltaMovement(Vec3.ZERO);
			}
			// It stands behind the one that nearly had them, on the far side from them.
			Mob first = monsters.get(0);
			for (Mob m : monsters) if (m.distanceToSqr(player) < first.distanceToSqr(player)) first = m;
			Vec3 out = first.position().subtract(player.position());
			out = new Vec3(out.x, 0, out.z);
			out = out.lengthSqr() < 1.0E-4 ? Sight.flatLook(player) : out.normalize();
			Vec3 want = first.position().add(out.scale(monsters.size() == 1 ? 1.6 : 4.0));
			ServerLevel world = Compat.level(player);
			BlockPos feet = Spots.groundNear(world, Mth.floor(want.x), first.getBlockY(), Mth.floor(want.z), 3);
			if (feet != null) {
				entity = haunt.spawnOccupant(player, feet, OccupantEntity.Mode.AMBUSH, OccupantEntity.Form.REVEALED);
				if (entity != null) {
					entity.setFootsteps(false);
					entity.setGazeLocked(true);
				}
			}
		}

		@Override
		public boolean tick(ServerPlayer player) {
			age++;
			if (entity != null) {
				if (entity.hasVanished()) entity = null;
				else entity.keepAlive();
			}
			// It arrives in the dark between flashes, never in front of them.
			if (age == ARRIVE && entity != null && entity.isConcealed()) {
				entity.faceTowards(player.getEyePosition());
				entity.setConcealed(false);
			}
			// A creeper is not held: it would still go off. It simply dies, at once.
			if (age == ARRIVE) {
				ServerLevel level = Compat.level(player);
				for (Mob e : monsters) {
					if (e.isAlive() && com.wolfsmask.occupant.util.Kinds.is(e, "creeper")) {
						e.hurtServer(level, e.damageSources().genericKill(), Float.MAX_VALUE);
					}
				}
			}
			if (age == ARRIVE + 1) {
				Cues.sound(player, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE,
						monsters.get(0).position(), 0.9f, 0.6f);
			}
			// Lifted, slowly and evenly, off the ground: one off its feet, many a little way.
			if (!killed && age >= ARRIVE && age <= LIFTED) {
				float t = (age - ARRIVE) / (float) (LIFTED - ARRIVE);
				float ease = t * t * (3 - 2 * t);
				double height = monsters.size() == 1 ? 1.8 : 0.6;
				for (int i = 0; i < monsters.size(); i++) {
					Mob m = monsters.get(i);
					if (!m.isAlive()) continue;
					Vec3 a = from.get(i);
					m.setPos(a.x, a.y + height * ease, a.z);
					m.setDeltaMovement(Vec3.ZERO);
				}
			}
			if (!killed && age == KILL) {
				killed = true;
				ServerLevel level = Compat.level(player);
				for (Mob e : monsters) {
					if (!e.isAlive()) continue;
					e.setNoGravity(false);
					level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 12, 0.2, 0.3, 0.2, 0.01);
					e.hurtServer(level, e.damageSources().genericKill(), Float.MAX_VALUE);
				}
				String[] lines = monsters.size() == 1 ? ONE : MANY;
				say(player, lines[player.getRandom().nextInt(lines.length)]);
			}
			// Then it turns its back and walks off into the fog.
			if (age == KILL + 20 && entity != null) {
				Vec3 out = entity.position().subtract(player.position());
				out = new Vec3(out.x, 0, out.z);
				out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
				away = entity.position().add(out.scale(28));
				entity.setGazeLocked(false);
				entity.setMode(OccupantEntity.Mode.STALK);
				entity.walkTo(away, 0.7);
			}
			if (entity == null) return age < KILL + 30;
			// Gone once they are not looking, or once it is out in the fog.
			if (age > KILL + 30 && (!Sight.isOnScreen(player, entity) || entity.distanceTo(player) > Fog.seenUpTo(player, haunt))) {
				return false;
			}
			return age < KILL + 400;
		}

		@Override
		public void end() {
			for (Mob m : monsters) {
				if (!m.isAlive()) continue;
				m.setNoAi(false);
				m.setNoGravity(false);
			}
			if (entity != null) entity.vanish();
		}

		@Nullable
		@Override
		public OccupantEntity occupant() {
			return entity;
		}
	}
}
