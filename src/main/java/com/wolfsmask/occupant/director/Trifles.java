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
		if (d.act >= 2) pets(player, world);
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
			if (night && h.skullDay != day && random.nextFloat() < 0.02f) {
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

	private static void swapInChest(ServerLevel world, BlockPos bed, RandomSource random) {
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-10, -3, -10), bed.offset(10, 3, 10))) {
			if (!(world.getBlockEntity(p) instanceof Container box) || box.getContainerSize() < 2) continue;
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

	/** At night, their animals will not come out with them: they sit down where they are. */
	private static void pets(ServerPlayer player, ServerLevel world) {
		if (!world.isDarkOutside() || !world.canSeeSky(player.blockPosition().above())) return;
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
				world.setBlock(p, (player.getRandom().nextBoolean() ? Blocks.POPPY : Blocks.WITHER_ROSE).defaultBlockState(), 3);
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
