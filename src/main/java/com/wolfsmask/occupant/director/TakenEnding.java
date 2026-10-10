package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.events.DoppelChatEvent;
import com.wolfsmask.occupant.director.events.Found;
import com.wolfsmask.occupant.director.events.Strike;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.story.Achievements;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.world.Lairs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The end of the story, for the ones it takes. It does not kill them. It never wanted to.
 * <p>
 * Held up on its legs, at its face, the last time: black. In the black, something very heavy
 * walking a long way, and their own heart slowing. Then a light: candles, deep under the ground,
 * in its lair, and in front of them a wall of names. Everyone who ever lived in this world is on
 * it: the survivor whose pages they read, the miner, the lighthouse keeper, the others. One sign
 * at the middle of the wall is blank, and while they watch, their own name is written on it, and
 * how many days they lasted. It is standing beside the wall. It comes close, and says it knows how
 * to be them now; and it goes, up into the world, wearing them.
 * <p>
 * Then the credits, in the dark: what they lasted, how often they saw it. And they wake at home,
 * the fog gone, the story begun again, quieter; and in the chat, in their own name, someone says
 * they are home.
 * <p>
 * It only ever comes in the last act, once they have played long enough, seen it with their own
 * eyes, and been let go of once already (see {@link #ready}). Until then it always lets them go.
 */
public final class TakenEnding implements Sequence {
	public static final String ID = "taken";
	/** How the story ended, in {@link HauntData#ending}: it took them. */
	public static final int TAKEN = 3;
	/**
	 * Kept (as a cooldown that never runs out, so it is saved) from the moment it takes them until
	 * they are home: if they leave the world in the middle of it, they come back home, not to its lair.
	 */
	public static final String UNDER = "taken_under";

	// ---- the dark: carried
	private static final int LINE_1 = 40;
	private static final int LINE_2 = 140;
	private static final int BUILD = 175;
	private static final int ARRIVE = 200;
	// ---- its lair: the wall of names
	private static final int LIGHT = 250;
	private static final int PAN_FROM = 268;
	private static final int PAN_TO = 395;
	private static final int SUB_KEEPS = 285;
	private static final int SUB_ALL = 362;
	private static final int TO_SIGN = 405;
	private static final int WRITE_FROM = 420;
	/** A letter of their name every this many ticks. */
	private static final int WRITE_EVERY = 3;
	private static final int SUB_LEARNED = 505;
	/** The candles go out, one after another, until there is only their name. */
	private static final int[] CANDLES_OUT = {520, 534, 548, 562};
	/** In the dark, it is right in front of them; and they turn to it. */
	private static final int CLOSE = 570;
	private static final int TURN = 578;
	private static final int SPEAKS = 600;
	/** A candle catches again by itself, for a moment: its face, a hand's width from theirs. */
	private static final int FLARE = 650;
	private static final int FLARE_OUT = 668;
	private static final int LEAVES = 680;
	private static final int SUB_WEARING = 705;
	// ---- the credits
	private static final int BLACK = 775;
	private static final int HOME = BLACK + 340;
	private static final int WAKE = BLACK + Credits.LENGTH;
	private static final int STING = WAKE + 60;
	private static final int OVER = STING + 10;
	/** No fog at all for a quarter of an hour after. */
	private static final long LIFTED_FOR = 20L * 60 * 15;

	/**
	 * Who else is on the wall, top row first, left to right; two lines each. The middle of the
	 * middle row is theirs, and blank until it is written.
	 */
	private static final String[][] NAMES = {
			{"the survivor", "day 19"}, {"Steve", "day 2"}, {"the keeper", "night 40"}, {"the miner", "shift 9"},
			{"", ""}, {"the watchman", "day 7"}, {"the family", "day 4"},
			{"Alex", "day 31"}, {"the priest", "day 12"}, {"IT LIED", ""}, {null, null},
			{"the radio man", "day 23"}, {"the hunter", "day 9"}, {"who?", "day ?"},
			{"the gravedigger", "day 27"}, {"don't look", "at it"}, {"the shepherd", "day 11"}, {"the first", "day 1"},
			{"the builder", "day 50"}, {"", ""}, {"the boy", "day 6"}};
	private static final int COLUMNS = 7;
	private static final int ROWS = 3;
	/** Where the candles stand at the foot of the wall, across it: the order they go out in. */
	private static final int[] CANDLES = {-3, 3, -2, 2};

	private final Haunt haunt;
	private int t;
	@Nullable
	private MinecraftServer server;
	/** Where they stand in its lair, at their feet, facing the wall (east of them); null if there is nowhere. */
	@Nullable
	private BlockPos origin;
	@Nullable
	private OccupantEntity entity;
	private String name = "";
	private String days = "";
	private long dayCount;
	private int seen;

	public TakenEnding(Haunt haunt) {
		this.haunt = haunt;
	}

	/**
	 * Whether it takes them now, instead of letting them go: only in the last act, once they have
	 * played long enough (fifty minutes at the story's own pace: thirty-five on relentless), seen
	 * it with their own eyes three times, and been let go of once already.
	 */
	public static boolean ready(HauntData d) {
		double minutes = 50.0 * Pacing.storyPace(OccupantConfig.get());
		return d.act >= HauntData.MAX_ACT && d.cooldowns.containsKey(Strike.SPARED) && d.sightings >= 3
				&& d.playTicks >= minutes * 1200.0;
	}

	/** Taken now: the ending begins the moment whatever has them is over. */
	public static void begin(Haunt h) {
		h.queue(ID, new TakenEnding(h));
	}

	@Override
	public boolean tick(ServerPlayer p) {
		t++;
		if (t == 1) start(p);
		if (t < ARRIVE) carried(p);
		if (t == LINE_1) Cues.title(p, "It didn't kill you.", 95, false);
		if (t == LINE_2) Cues.title(p, "It never wanted to.", 95, false);
		if (t == BUILD) {
			try {
				origin = build(p);
			} catch (RuntimeException e) {
				Occupant.LOGGER.warn("Could not make its lair ready for the end", e);
				origin = null;
			}
		}
		if (t == ARRIVE) {
			if (origin != null) arrive(p);
			else t = BLACK - 1;           // nowhere to take them: straight to the end of it
		}
		if (t > ARRIVE && t < BLACK) lair(p);
		if (t == BLACK) black(p);
		credits(p);
		if (t == HOME) home(p);
		if (t == WAKE) wake(p);
		if (t == STING) Cues.message(p, DoppelChatEvent.chat(name, "I'm home."));
		return t < OVER;
	}

	// ------------------------------------------------------------------ the dark

	private void start(ServerPlayer p) {
		server = Compat.level(p).getServer();
		HauntData d = haunt.data;
		name = p.getName().getString();
		dayCount = Credits.days(p);
		days = "day " + dayCount;
		seen = d.sightings;
		d.ending = TAKEN;
		d.cooldowns.put(UNDER, Long.MAX_VALUE);
		// Black from here until the candles: the strike's own black runs straight on into it.
		Cues.effect(p, ScreenEffectPayload.BLACKOUT, LIGHT, 1f);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		Cues.effect(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
		Cues.lookFree(p);
		haunt.releaseFog();
		haunt.liftFog(server.getTickCount() + LIFTED_FOR + OVER);
		p.removeEffect(MobEffects.DARKNESS);
		p.setNoGravity(false);
		p.resetFallDistance();
		Director.debug("{} was taken", name);
	}

	/** In the black: something very heavy walking, a long way; their own heart, slowing. */
	private void carried(ServerPlayer p) {
		p.resetFallDistance();
		if (t >= 18 && t <= 182 && t % 15 == 3) {
			Vec3 side = p.getLookAngle().cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0e-4) side = new Vec3(1, 0, 0);
			Vec3 at = p.getEyePosition().add(side.normalize().scale((t / 15) % 2 == 0 ? 1.6 : -1.6)).add(0.0, -1.2, 0.0);
			Cues.sound(p, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, at, 0.8f, 0.45f + p.getRandom().nextFloat() * 0.08f);
		}
		if (t >= 10 && t % 27 == 10) {
			float slower = Math.max(0.25f, 0.65f - t / 400f);
			Cues.sound(p, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, p.getEyePosition(), slower, 0.7f);
		}
		if (t == 120) Cues.sound(p, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, p.getEyePosition().add(0, -3, 0), 0.8f, 0.6f);
		if (t == ARRIVE - 12) Cues.soundAtEars(p, ModSounds.BREATH, SoundSource.HOSTILE, 0.8f, 0.55f);
	}

	// ------------------------------------------------------------------ its lair

	/**
	 * Its lair, made ready: the hollow it dug nearest their home, or, if it never dug one, a hollow
	 * under their home now. Then the wall of names, the candles. Returns where they will stand.
	 */
	@Nullable
	private BlockPos build(ServerPlayer p) {
		ServerLevel world = server.overworld();
		BlockPos home = p.level() == world ? Compat.respawnPos(p) : null;
		if (home == null) home = p.level() == world ? p.blockPosition() : Compat.spawnPos(world);
		BlockPos hollow = Lairs.nearest(home, 4000.0);
		if (hollow == null) hollow = Lairs.newest();
		BlockPos o;
		if (hollow != null) {
			// Beside the foot of its ladder, which comes down the middle.
			o = hollow.offset(2, -1, 0);
			load(world, o);
		} else {
			load(world, home);
			int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ());
			o = new BlockPos(home.getX(), Math.max(-48, top - 26), home.getZ());
			load(world, o);
			dig(world, o);
		}
		wall(world, o);
		// Nothing else down here with them.
		for (Mob m : world.getEntitiesOfClass(Mob.class, new AABB(o).inflate(28.0), m -> m instanceof Enemy && !(m instanceof OccupantEntity) && !m.hasCustomName())) {
			m.discard();
		}
		return o;
	}

	private static void load(ServerLevel world, BlockPos at) {
		for (int cx = (at.getX() - 10) >> 4; cx <= (at.getX() + 10) >> 4; cx++) {
			for (int cz = (at.getZ() - 10) >> 4; cz <= (at.getZ() + 10) >> 4; cz++) world.getChunk(cx, cz);
		}
	}

	/** A hollow of its own, low and wide, with them at the east of it: for when it never dug one. */
	private static void dig(ServerLevel world, BlockPos o) {
		BlockPos c = o.offset(-2, 0, 0);
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				for (int dy = -1; dy <= 4; dy++) {
					double d = (dx * dx + dz * dz) / 49.0 + Math.pow((dy - 1.5) / 3.2, 2);
					if (d > 1.0) continue;
					BlockPos at = c.offset(dx, dy, dz);
					set(world, at, dy < 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		seal(world, c.offset(-8, -2, -8), c.offset(8, 5, 8));
	}

	/**
	 * The wall of names: seven signs across, three high, on a face of deep stone a few steps east of
	 * where they stand, room cleared in front of it, and candles at its foot.
	 */
	private static void wall(ServerLevel world, BlockPos o) {
		for (int x = -1; x <= 3; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = 0; y <= 3; y++) set(world, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
				BlockPos floor = o.offset(x, -1, z);
				if (world.getBlockState(floor).getCollisionShape(world, floor).isEmpty()) set(world, floor, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
			}
		}
		for (int z = -4; z <= 4; z++) {
			for (int y = -1; y <= 3; y++) {
				BlockPos back = o.offset(4, y, z);
				if (!world.getBlockState(back).isFaceSturdy(world, back, Direction.WEST)) set(world, back, Blocks.DEEPSLATE.defaultBlockState());
			}
		}
		// Wide of it as well: where it goes when it leaves, and the hollow round them, so no lava
		// lights the dark when the candles are out.
		seal(world, o.offset(-12, -3, -9), o.offset(6, 7, 9));
		int k = 0;
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++, k++) {
				BlockPos at = signAt(o, row, col);
				world.setBlock(at, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 3);
				String[] who = NAMES[k];
				if (who[0] == null) continue;      // theirs: written later
				if (world.getBlockEntity(at) instanceof SignBlockEntity sign) Compat.writeSign(sign, new String[]{"", who[0], who[1], ""});
			}
		}
		for (int z : CANDLES) {
			BlockPos at = o.offset(2, 0, z);
			world.setBlock(at, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 2 + Math.abs(z) % 2 * 2)
					.setValue(BlockStateProperties.LIT, true), 3);
		}
		for (int z : new int[]{-4, 4}) {
			world.setBlock(o.offset(3, 3, z), Blocks.COBWEB.defaultBlockState(), 3);
			world.setBlock(o.offset(2, 0, z), Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, z > 0 ? 6 : 10), 3);
		}
	}

	/** Where a sign on the wall is: row 0 the top. */
	private static BlockPos signAt(BlockPos o, int row, int col) {
		return o.offset(3, 2 - row, col - COLUMNS / 2);
	}

	/** Theirs: the middle of the middle row. */
	private static BlockPos theirs(BlockPos o) {
		return signAt(o, ROWS / 2, COLUMNS / 2);
	}

	/** The face of a sign, where the eye goes to read it. */
	private static Vec3 face(BlockPos sign) {
		return new Vec3(sign.getX() + 0.86, sign.getY() + 0.55, sign.getZ() + 0.5);
	}

	/** Made into this, unless it is bedrock and its like, or holds anything (a chest someone left down here). */
	private static void set(ServerLevel world, BlockPos at, BlockState state) {
		BlockState was = world.getBlockState(at);
		if (was == state || was.getDestroySpeed(world, at) < 0.0f) return;
		if (world.getBlockEntity(at) instanceof BlockEntity) return;
		world.setBlock(at, state, 2);
	}

	/** No water or lava coming in round the edges of it. */
	private static void seal(ServerLevel world, BlockPos from, BlockPos to) {
		for (BlockPos at : BlockPos.betweenClosed(from, to)) {
			if (!world.getFluidState(at).isEmpty()) set(world, at.immutable(), Blocks.STONE.defaultBlockState());
		}
	}

	/** Set down in it, facing the wall, the candles lit; it is beside the wall. */
	private void arrive(ServerPlayer p) {
		BlockPos o = origin;
		if (o == null) return;
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.2f %d %.2f -90 0",
							p.getStringUUID(), o.getX() + 0.5, o.getY(), o.getZ() + 0.5));
		} catch (RuntimeException e) {
			Occupant.LOGGER.warn("Could not take {} to its lair", name, e);
		}
		p.setHealth(p.getMaxHealth());
		p.resetFallDistance();
		Cues.lookAt(p, face(signAt(o, ROWS / 2, 0)));
	}

	/** One tick in its lair. */
	private void lair(ServerPlayer p) {
		BlockPos o = origin;
		if (o == null) return;
		p.resetFallDistance();
		if (t == ARRIVE + 4) {
			for (int[] at : new int[][]{{1, 3}, {1, -3}, {0, 3}}) {
				entity = haunt.spawnOccupant(p, o.offset(at[0], 0, at[1]), OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
				if (entity != null) break;
			}
			if (entity != null) {
				entity.setConcealed(false);
				// As it holds them when it has them: and without the static of its stare, so the names can be read.
				entity.setMode(OccupantEntity.Mode.AMBUSH);
				entity.setGazeLocked(true);
				entity.faceTowards(p.getEyePosition());
			}
		}
		if (entity != null) {
			if (entity.hasVanished()) entity = null;
			else entity.keepAlive();
		}
		if (t >= PAN_FROM && t <= PAN_TO && (t - PAN_FROM) % 3 == 0) {
			// Along the names, left to right, slowly.
			double f = (t - PAN_FROM) / (double) (PAN_TO - PAN_FROM);
			f = f * f * (3.0 - 2.0 * f);
			Vec3 left = face(signAt(o, ROWS / 2, 0)), right = face(signAt(o, ROWS / 2, COLUMNS - 1));
			Cues.lookAt(p, new Vec3(left.x, Mth.lerp(f, left.y, right.y), Mth.lerp(f, left.z, right.z)));
		}
		if (t == SUB_KEEPS) Cues.title(p, "It keeps everyone it takes.", 90, true);
		if (t == SUB_ALL) Cues.title(p, "Everyone who ever lived in this world is here.", 100, true);
		if (t == TO_SIGN) Cues.lookAt(p, face(theirs(o)));
		if (t >= WRITE_FROM && (t - WRITE_FROM) % WRITE_EVERY == 0) write(p, o, (t - WRITE_FROM) / WRITE_EVERY + 1);
		if (t == SUB_LEARNED) {
			Cues.title(p, seen >= 8 ? "Every time you looked at it, it learned more of you."
					: "You hardly ever looked at it. It never needed you to.", 75, true);
		}
		for (int k = 0; k < CANDLES_OUT.length; k++) {
			if (t == CANDLES_OUT[k]) candle(p, o, CANDLES[k], false);
		}
		// With the last of them, the dark: real dark, whatever their brightness is set to, but for
		// the pale of its face, and the two specks in the black of its eyes.
		if (t == CANDLES_OUT[CANDLES_OUT.length - 1]) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FLARE - t + 4, 0, false, false));
		if (t == CLOSE && entity != null) {
			// While the dark is complete: right in front of them, between them and the wall.
			Vec3 at = new Vec3(o.getX() + 1.7, o.getY(), o.getZ() + 0.5);
			float yaw = com.wolfsmask.occupant.util.Sight.yawBetween(at, p.position());
			entity.halt();
			entity.snapTo(at.x, at.y, at.z, yaw, 0.0f);
			entity.setYHeadRot(yaw);
			entity.setYBodyRot(yaw);
		}
		if (t == TURN) {
			// Its eyes are all there is to turn to.
			Cues.lookFree(p);
			Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, p.getEyePosition().add(p.getLookAngle().scale(1.5)), 0.7f, 0.5f);
		}
		if (t == SPEAKS) {
			Cues.title(p, "Now I know how to be you.", 100, false);
			if (entity != null) Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 0.6f);
		}
		if (t == FLARE) {
			p.removeEffect(MobEffects.DARKNESS);
			candle(p, o, -2, true);
			candle(p, o, 2, true);
			Cues.effect(p, ScreenEffectPayload.STATIC, 8, 0.45f);
			Cues.soundAtEars(p, ModSounds.STATIC, SoundSource.HOSTILE, 0.8f, 0.6f);
		}
		if (t == FLARE_OUT) {
			candle(p, o, -2, false);
			candle(p, o, 2, false);
			// And it goes, in the dark.
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BLACK - t + 10, 0, false, false));
		}
		if (t >= TURN && t < LEAVES && entity != null && !entity.isPathing()) entity.faceTowards(p.getEyePosition());
		if (t == LEAVES && entity != null) {
			// Away into the dark at the far side, behind them, and up into the world.
			entity.setGazeLocked(false);
			entity.walkTo(Vec3.atBottomCenterOf(o.offset(-7, 0, 0)), 0.5);
		}
		if (t == SUB_WEARING) Cues.title(p, "It went up into the world, wearing you.", 80, true);
	}

	/** A candle at the foot of the wall lit (brightly, all four wicks) or put out, with its sound. */
	private void candle(ServerPlayer p, BlockPos o, int z, boolean lit) {
		ServerLevel world = server.overworld();
		BlockPos at = o.offset(2, 0, z);
		BlockState state = world.getBlockState(at);
		if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(BlockStateProperties.CANDLES)) return;
		world.setBlock(at, lit ? state.setValue(BlockStateProperties.LIT, true).setValue(BlockStateProperties.CANDLES, 4)
				: state.setValue(BlockStateProperties.LIT, false), 3);
		Cues.sound(p, lit ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
				Vec3.atCenterOf(at), lit ? 0.6f : 0.35f, lit ? 0.6f : 1.4f);
	}

	/** Their name, a letter at a time, and how many days they lasted, glowing on the blank sign. */
	private void write(ServerPlayer p, BlockPos o, int letters) {
		int total = name.length() + days.length();
		if (letters > total) return;
		ServerLevel world = server.overworld();
		if (!(world.getBlockEntity(theirs(o)) instanceof SignBlockEntity sign)) return;
		String first = name.substring(0, Math.min(name.length(), letters));
		String second = letters > name.length() ? days.substring(0, letters - name.length()) : "";
		Compat.writeSign(sign, new String[]{"", first, second, ""}, true);
		Cues.sound(p, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, face(theirs(o)), 0.25f, 1.7f + p.getRandom().nextFloat() * 0.3f);
	}

	// ------------------------------------------------------------------ the end of it

	private void black(ServerPlayer p) {
		p.removeEffect(MobEffects.DARKNESS);
		Cues.effect(p, ScreenEffectPayload.BLACKOUT, WAKE - BLACK, 1f);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		if (entity != null) entity.vanish();
		entity = null;
	}

	private void credits(ServerPlayer p) {
		if (t >= BLACK) Credits.roll(p, t - BLACK, name, dayCount, seen, TAKEN);
	}

	/** Home, whole, the story begun again from the start of it; nothing following them for a while. */
	private void home(ServerPlayer p) {
		HauntData d = haunt.data;
		Mercy.sendHome(p);
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(5.0f);
		p.setNoGravity(false);
		p.resetFallDistance();
		d.lastNight = true;
		d.ending = TAKEN;
		d.dread = 0f;
		d.cooldowns.remove(Strike.SPARED);
		d.cooldowns.remove(Found.CAUGHT);
		d.cooldowns.remove(Director.ATTACK_DUE);
		d.cooldowns.remove(UNDER);
		d.setAct(1);
		haunt.nextEventIn = 20 * 60 * 3;
		if (server != null) haunt.liftFog(server.getTickCount() + LIFTED_FOR);
		Achievements.grant(p, Achievements.LAST_NIGHT);
		Achievements.grant(p, Achievements.ENDING_TAKEN);
		Director director = Director.get();
		if (director != null) director.markDirty();
	}

	/**
	 * Back in the world, having left it in the middle of the end (it could not bring them home
	 * while they were gone): home now, and the story begun again, as if they had seen it through.
	 */
	static void recover(Haunt h, ServerPlayer p) {
		if (!h.data.cooldowns.containsKey(UNDER) || !p.isAlive()) return;
		TakenEnding e = new TakenEnding(h);
		e.server = Compat.level(p).getServer();
		e.home(p);
		Director.debug("{} came back from the end, and was sent home", p.getName().getString());
	}

	private void wake(ServerPlayer p) {
		Cues.lookFree(p);
		Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
	}

	@Override
	public void end() {
		if (entity != null) entity.vanish();
		entity = null;
		// Cut short (a command, or something going wrong): home, and their view their own again.
		if (t < HOME && server != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(haunt.uuid);
			if (p != null && p.isAlive()) {
				try {
					home(p);
					wake(p);
				} catch (RuntimeException e) {
					Occupant.LOGGER.warn("Could not bring {} home from the end", name, e);
				}
			}
		}
	}

	@Override
	@Nullable
	public OccupantEntity occupant() {
		return entity;
	}
}
