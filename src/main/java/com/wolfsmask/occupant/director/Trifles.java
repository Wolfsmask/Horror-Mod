package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.events.DoppelChatEvent;
import com.wolfsmask.occupant.director.events.WorldBlocks;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The small things. None of them is an event, none is ever explained, and most players will not
 * be sure they saw them: the words they typed an hour ago, said again in their name; one more
 * footstep after they stop; a name on something in their pack that was not there a moment ago;
 * a skull by the bed; the door open when they come home; their pets, who will not come out
 * after dark; two things in a chest the wrong way round; a fire that will not catch while it is
 * near.
 */
public final class Trifles {
	private static final String[] NAMES = {"it remembers this", "it touched this", "you'll need this", "not yours",
			"{player}'s, for now", "it held this"};

	private Trifles() {
	}

	/** Every tick, for a player who is being haunted. */
	static void tick(ServerPlayer player, Haunt h, OccupantConfig cfg) {
		HauntData d = h.data;
		if (d.act < 1) return;
		ServerLevel world = Compat.level(player);
		long now = world.getServer().getTickCount();
		footsteps(player, h, world, now);
		if (h.renamedUntil > 0 && now >= h.renamedUntil) restoreName(player, h);
		if (player.tickCount % 20 != 3) return;

		// Once a second from here on.
		RandomSource random = player.getRandom();
		echo(player, h, cfg, random);
		if (d.act >= 3 && h.renamedUntil == 0 && now >= h.renameAgainAt && random.nextFloat() < 0.004f) rename(player, h, now, random);
		home(player, h, cfg, world, now, random);
		if (d.act >= 2) pets(player, h, world, random);
		answer(player, h, world, now);
		greyDay(player, h, world, now, random);
		if (cfg.worldChanges) {
			tally(player, h, world);
			if (d.act >= 2 && (d.marks & 1) == 0 && random.nextFloat() < 0.002f) markTrees(player, h, world, random);
		}
	}

	// ------------------------------------------------------------------ something answers

	/** They played a note or rang a bell: now and then, a while later, something out there answers. */
	public static void played(ServerPlayer player, boolean bell) {
		Director director = Director.get();
		if (director == null || !OccupantConfig.get().enabled) return;
		Haunt h = director.haunt(player);
		long now = Compat.level(player).getServer().getTickCount();
		if (h.data.act < 2 || h.answerAt > 0 || now < h.answerAgainAt || player.getRandom().nextFloat() > 0.4f) return;
		h.answerAt = now + 30 + player.getRandom().nextInt(40);
		h.answerBell = bell;
		h.answerAgainAt = now + 20L * 60 * 2;
	}

	private static void answer(ServerPlayer player, Haunt h, ServerLevel world, long now) {
		if (h.answerAt <= 0 || now < h.answerAt) return;
		h.answerAt = 0;
		Vec3 dir = Sight.rotateY(Sight.flatLook(player), 90 + player.getRandom().nextInt(180));
		// Far off, but not so far the game drops it: a sound carries sixteen blocks for each unit of
		// volume, so at this volume it is heard out to forty, faintly from here.
		Vec3 at = player.position().add(dir.scale(18 + player.getRandom().nextInt(7))).add(0, 2, 0);
		if (h.answerBell) {
			Cues.sound(player, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, at, 2.5f, 0.7f);
		} else {
			Cues.sound(player, SoundEvents.NOTE_BLOCK_HARP, SoundSource.RECORDS, at, 2.5f, 0.5f + player.getRandom().nextFloat() * 0.3f);
		}
	}

	// ------------------------------------------------------------------ a grey day

	/** Now and then, late on, a day with no sun: the fog right in from first light. */
	private static void greyDay(ServerPlayer player, Haunt h, ServerLevel world, long now, RandomSource random) {
		if (h.data.act < 3 || world != world.getServer().overworld()) return;
		long time = Compat.dayTime(world) % 24000L;
		long day = Compat.dayTime(world) / 24000L;
		if (time > 1000 || h.greyDay == day) return;           // decided once, at first light
		h.greyDay = day;
		if (random.nextFloat() > 0.3f) return;
		h.greyUntil = now + (12000 - time);                      // until dusk
		if (OccupantConfig.get().screenWhispers) Cues.whisper(player, "no sun today", 100);
	}

	// ------------------------------------------------------------------ the tally

	/** Every day they live through, one more mark on the sign it keeps by their bed. */
	private static void tally(ServerPlayer player, Haunt h, ServerLevel world) {
		HauntData d = h.data;
		BlockPos bed = Compat.respawnPos(player);
		if (bed == null || world != world.getServer().overworld() || !Spots.isLoaded(world, bed)) return;
		long day = Compat.dayTime(world) / 24000L;
		if (h.tallyDay == day) return;
		h.tallyDay = day;
		int days = (int) Math.min(60, 1 + d.playTicks / 24000L);
		BlockPos sign = d.tallyY == Integer.MIN_VALUE ? null : new BlockPos(d.tallyX, d.tallyY, d.tallyZ);
		if (sign == null || sign.distSqr(bed) > 16 * 16 || !(world.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity)) {
			sign = placeTallySign(world, bed);
			if (sign == null) return;
			d.tallyX = sign.getX();
			d.tallyY = sign.getY();
			d.tallyZ = sign.getZ();
		}
		if (world.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity entity) {
			String[] lines = new String[4];
			int left = days;
			for (int i = 0; i < 4; i++) {
				StringBuilder line = new StringBuilder();
				for (int g = 0; g < 3 && left > 0; g++) {
					int n = Math.min(5, left);
					left -= n;
					if (line.length() > 0) line.append(' ');
					line.append("IIIII", 0, n);
				}
				lines[i] = line.toString();
			}
			Compat.writeSign(entity, lines);
		}
	}

	/** A sign on a wall near their bed, where they will see it; null if there is no wall. */
	@Nullable
	private static BlockPos placeTallySign(ServerLevel world, BlockPos bed) {
		for (int r = 1; r <= 5; r++) {
			for (BlockPos p : BlockPos.betweenClosed(bed.offset(-r, 0, -r), bed.offset(r, 2, r))) {
				if (!world.getBlockState(p).isAir()) continue;
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos wall = p.relative(dir);
					if (!world.getBlockState(wall).isFaceSturdy(world, wall, dir.getOpposite())) continue;
					world.setBlock(p, Blocks.OAK_WALL_SIGN.defaultBlockState()
							.setValue(BlockStateProperties.HORIZONTAL_FACING, dir.getOpposite()), 3);
					return p.immutable();
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the marked trees

	private static final java.util.Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> STRIPPED = java.util.Map.of(
			Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG,
			Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG,
			Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
			Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG);

	/**
	 * Once: a line of trees going away from their home, each with the bark torn off at the height
	 * of a face, all in the same direction. Where it leads, there is nothing. Out of their sight.
	 */
	private static void markTrees(ServerPlayer player, Haunt h, ServerLevel world, RandomSource random) {
		BlockPos home = Compat.respawnPos(player);
		if (home == null || Math.sqrt(home.distToCenterSqr(player.position())) > 40) return;
		Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		java.util.List<BlockPos> marks = new java.util.ArrayList<>();
		for (int step = 10; step <= 70 && marks.size() < 7; step += 2) {
			BlockPos along = home.relative(dir, step);
			search:
			for (int side = -3; side <= 3; side++) {
				BlockPos column = along.relative(dir.getClockWise(), side);
				for (int dy = -1; dy <= 6; dy++) {
					BlockPos p = column.above(dy);
					if (!Spots.isLoaded(world, p)) break search;
					BlockState s = world.getBlockState(p);
					net.minecraft.world.level.block.Block to = STRIPPED.get(s.getBlock());
					if (to == null || !world.getBlockState(p.below()).is(s.getBlock()) && !world.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)
							&& !world.getBlockState(p.below()).is(Blocks.DIRT)) continue;
					if (!Sight.isHidden(player, p) || !isTree(world, p)) continue;
					if (!marks.isEmpty() && marks.get(marks.size() - 1).distSqr(p) < 5 * 5) continue;
					marks.add(p.above().immutable());
					break search;
				}
			}
		}
		if (marks.size() < 3) return;
		for (BlockPos p : marks) {
			BlockState s = world.getBlockState(p);
			net.minecraft.world.level.block.Block to = STRIPPED.get(s.getBlock());
			if (to == null) continue;
			world.setBlock(p, to.defaultBlockState().setValue(BlockStateProperties.AXIS, s.getValue(BlockStateProperties.AXIS)), 3);
		}
		h.data.marks |= 1;
	}

	/**
	 * A tree, not a wall of logs: leaves over the trunk, and nothing they made nearby (a log cabin
	 * is logs on logs on the ground too).
	 */
	private static boolean isTree(ServerLevel world, BlockPos base) {
		boolean leaves = false;
		for (int up = 1; up <= 14 && !leaves; up++) {
			BlockPos p = base.above(up);
			for (Direction d : Direction.values()) {
				if (d == Direction.DOWN) continue;
				if (world.getBlockState(p.relative(d)).is(net.minecraft.tags.BlockTags.LEAVES)) {
					leaves = true;
					break;
				}
			}
		}
		return leaves && !com.wolfsmask.occupant.world.Places.looksBuilt(world, base, 3);
	}

	// ------------------------------------------------------------------ the words they said

	/** The last thing they typed, a long while later, in their own name. Once for each thing said. */
	private static void echo(ServerPlayer player, Haunt h, OccupantConfig cfg, RandomSource random) {
		HauntData d = h.data;
		if (d.act < 2 || !cfg.fakeMessages || d.heardChat.isEmpty()) return;
		String last = d.heardChat.peekFirst();
		if (!last.equals(h.echoOf)) {
			h.echoOf = last;
			h.echoAt = d.playTicks + 20L * 60 * (20 + random.nextInt(40));
			return;
		}
		if (h.echoAt < 0 || d.playTicks < h.echoAt || h.active != null) return;
		h.echoAt = -1;
		Cues.message(player, DoppelChatEvent.chat(player.getName().getString(), last));
	}

	// ------------------------------------------------------------------ one more step

	/** They stop walking; one more step comes, behind them. */
	private static void footsteps(ServerPlayer player, Haunt h, ServerLevel world, long now) {
		if (h.stepAt > 0 && now >= h.stepAt) {
			h.stepAt = 0;
			Vec3 back = Sight.flatLook(player).scale(-1.6);
			Vec3 at = player.position().add(back);
			BlockState floor = world.getBlockState(BlockPos.containing(at.x, player.getY() - 0.5, at.z));
			if (!floor.isAir()) {
				SoundType type = floor.getSoundType();
				Cues.sound(player, type.getStepSound(), SoundSource.PLAYERS, at, type.getVolume() * 0.3f, type.getPitch());
			}
			return;
		}
		Vec3 v = player.getDeltaMovement();
		boolean moving = player.onGround() && v.x * v.x + v.z * v.z > 0.003;
		if (h.wasWalking && !moving && player.onGround() && now >= h.stepAgainAt && player.getRandom().nextFloat() < 0.06f) {
			h.stepAt = now + 6;
			h.stepAgainAt = now + 20L * 60 * 3;
		}
		h.wasWalking = moving;
	}

	// ------------------------------------------------------------------ the wrong name

	/**
	 * Something in their pack goes by another name for a few seconds. Only on their screen: the
	 * thing itself is never touched, and the next look at the pack puts it right.
	 */
	private static void rename(ServerPlayer player, Haunt h, long now, RandomSource random) {
		// Never in creative, whose inventory would send the false name back as the real one.
		if (player.isCreative()) return;
		var inventory = player.getInventory();
		for (int tries = 0; tries < 12; tries++) {
			int index = random.nextInt(36);
			ItemStack stack = inventory.getItem(index);
			if (stack.isEmpty()) continue;
			String name = NAMES[random.nextInt(NAMES.length)].replace("{player}", player.getName().getString());
			ItemStack shown = Compat.named(stack, Component.literal(name).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
			int slot = index < 9 ? 36 + index : index;               // the inventory screen's own numbering
			var menu = player.inventoryMenu;
			player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slot, shown));
			h.renamedUntil = now + 20 * 6;
			h.renameAgainAt = now + 20L * 60 * 20;
			return;
		}
	}

	private static void restoreName(ServerPlayer player, Haunt h) {
		h.renamedUntil = 0;
		player.inventoryMenu.sendAllDataToRemote();
	}

	// ------------------------------------------------------------------ home

	/**
	 * Their home (their bed): away from it at night, a skull is left beside it; coming back to it
	 * after a long while away, a door of it is open; and the things in a chest there are not quite
	 * where they left them.
	 */
	private static void home(ServerPlayer player, Haunt h, OccupantConfig cfg, ServerLevel world, long now, RandomSource random) {
		HauntData d = h.data;
		BlockPos bed = Compat.respawnPos(player);
		if (bed == null || d.act < 2 || !cfg.worldChanges || world != world.getServer().overworld()) return;
		double dist = Math.sqrt(bed.distToCenterSqr(player.position()));
		long day = Compat.dayTime(world) / 24000L;
		boolean night = world.isDarkOutside();

		if (dist > 48 && Spots.isLoaded(world, bed)) {
			if (h.awaySince < 0) h.awaySince = now;
			// Away at night: something lies down where they sleep, and leaves something behind.
			if (night && h.skullDay != day && random.nextFloat() < 0.02f && !skullBeside(world, bed)) {
				h.skullDay = day;
				leaveBySide(world, bed, Blocks.SKELETON_SKULL.defaultBlockState()
						.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
			}
			// And now and then their things are moved about.
			if (h.chestDay != day && random.nextFloat() < 0.01f) {
				h.chestDay = day;
				swapInChest(world, bed, random);
			}
			return;
		}
		// Coming home after a while away: a door is open, before they get there.
		if (h.awaySince >= 0 && dist < 32 && now - h.awaySince > 20L * 60 * 2 && now >= h.doorAgainAt) {
			h.awaySince = -1;
			if (random.nextFloat() < 0.5f) {
				h.doorAgainAt = now + 20L * 60 * 30;
				openADoor(player, world, bed);
			}
		} else if (dist < 32) {
			h.awaySince = -1;
		}
	}

	/** On the floor beside {@code bed}, if there is a free spot. */
	private static void leaveBySide(ServerLevel world, BlockPos bed, BlockState thing) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int step = 1; step <= 2; step++) {
				BlockPos p = bed.relative(dir, step);
				BlockPos below = p.below();
				if (world.getBlockState(p).isAir() && world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)
						&& !world.getBlockState(p.relative(dir.getOpposite())).isAir()) {
					world.setBlock(p, thing, 3);
					return;
				}
			}
		}
	}

	/** One is enough: it does not leave a pile of them. */
	private static boolean skullBeside(ServerLevel world, BlockPos bed) {
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-2, -1, -2), bed.offset(2, 1, 2))) {
			if (world.getBlockState(p).is(Blocks.SKELETON_SKULL)) return true;
		}
		return false;
	}

	/** Only a chest or a barrel: a furnace's or a brewing stand's slots are not all alike. */
	private static void swapInChest(ServerLevel world, BlockPos bed, RandomSource random) {
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-10, -3, -10), bed.offset(10, 3, 10))) {
			var entity = world.getBlockEntity(p);
			if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity)) continue;
			if (!(entity instanceof Container box) || box.getContainerSize() < 2) continue;
			if (com.wolfsmask.occupant.world.Loot.unopened(p)) continue;
			int a = random.nextInt(box.getContainerSize());
			int b = random.nextInt(box.getContainerSize());
			if (a == b || box.getItem(a).isEmpty() && box.getItem(b).isEmpty()) continue;
			ItemStack first = box.getItem(a);
			box.setItem(a, box.getItem(b));
			box.setItem(b, first);
			box.setChanged();
			return;
		}
	}

	private static void openADoor(ServerPlayer player, ServerLevel world, BlockPos bed) {
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-10, -2, -10), bed.offset(10, 2, 10))) {
			if (!WorldBlocks.isClosedWoodenDoor(world, p) || !Sight.isHidden(player, p)) continue;
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock door) {
				door.setOpen(null, world, s, p.immutable(), true);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ pets

	/**
	 * At night, their animals will not come out with them: once in the night, they sit down where
	 * they are. Stood up again, they stay up.
	 */
	private static void pets(ServerPlayer player, Haunt h, ServerLevel world, RandomSource random) {
		long night = (Compat.dayTime(world) + 12000L) / 24000L;
		if (!world.isDarkOutside() || h.petsNight == night || !world.canSeeSky(player.blockPosition().above())
				|| random.nextFloat() > 0.01f) return;
		h.petsNight = night;
		for (TamableAnimal pet : world.getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(16.0),
				a -> a.isAlive() && a.isOwnedBy(player) && !a.isOrderedToSit() && !a.isPassenger())) {
			pet.setOrderedToSit(true);
			pet.setInSittingPose(true);
			pet.getNavigation().stop();
		}
	}

	// ------------------------------------------------------------------ fire, and a grave

	/** Fire will not catch while it is close. True if a light was put out. */
	public static boolean fireRefused(ServerPlayer player, ItemStack held) {
		if (!held.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) && !held.is(net.minecraft.world.item.Items.FIRE_CHARGE)) return false;
		Director director = Director.get();
		if (director == null || !OccupantConfig.get().enabled || director.data(player).act < 2) return false;
		boolean near = !Compat.level(player).getEntitiesOfClass(OccupantEntity.class, player.getBoundingBox().inflate(24.0),
				e -> e.isAlive() && e.isHaunting(player)).isEmpty();
		if (!near) return false;
		Cues.sound(player, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, player.getEyePosition().add(player.getLookAngle()), 0.5f, 1.4f);
		return true;
	}

	/** Where they died, a flower, afterwards. */
	public static void grave(ServerPlayer player) {
		Director director = Director.get();
		OccupantConfig cfg = OccupantConfig.get();
		if (director == null || !cfg.enabled || !cfg.worldChanges || director.data(player).act < 2) return;
		ServerLevel world = Compat.level(player);
		BlockPos at = player.blockPosition();
		for (int dy = 0; dy >= -3; dy--) {
			BlockPos p = at.above(dy);
			BlockState below = world.getBlockState(p.below());
			if (world.getBlockState(p).isAir()
					&& (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.PODZOL) || below.is(Blocks.COARSE_DIRT))) {
				// Nothing that hurts: they will be walking back over it for their things.
				world.setBlock(p, (player.getRandom().nextBoolean() ? Blocks.POPPY : Blocks.LILY_OF_THE_VALLEY).defaultBlockState(), 3);
				return;
			}
		}
	}

	/** Puts back anything shown wrongly, when they go. */
	static void forget(@Nullable ServerPlayer player, Haunt h) {
		if (player != null && h.renamedUntil > 0) restoreName(player, h);
		h.renamedUntil = 0;
	}
}
