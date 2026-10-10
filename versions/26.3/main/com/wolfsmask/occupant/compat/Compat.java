package com.wolfsmask.occupant.compat;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

/** The few places where Minecraft versions disagree about something small. This copy is for 26.3 and later. */
public final class Compat {
	private Compat() {
	}

	/** Every position within the given distance of {@code center} on each axis, nearest first. */
	public static Iterable<BlockPos> withinManhattan(BlockPos center, int rx, int ry, int rz) {
		int r = Math.max(rx, Math.max(ry, rz));
		return Iterables.filter(BlockPos.withinManhattan(center, r), p -> Math.abs(p.getX() - center.getX()) <= rx
				&& Math.abs(p.getY() - center.getY()) <= ry && Math.abs(p.getZ() - center.getZ()) <= rz);
	}

	/** Write up to four lines on the front of a sign. */
	public static void writeSign(SignBlockEntity sign, String[] lines) {
		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) messages.add(Component.literal(i < lines.length ? lines[i] : ""));
		sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), SignTextSlot.FRONT);
	}

	/** As {@link #writeSign(SignBlockEntity, String[])}; {@code glowing}: white, and lit from within. */
	public static void writeSign(SignBlockEntity sign, String[] lines, boolean glowing) {
		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) messages.add(Component.literal(i < lines.length ? lines[i] : ""));
		sign.setText(new SignText(messages, messages, glowing ? DyeColor.WHITE : DyeColor.BLACK, glowing), SignTextSlot.FRONT);
	}

	/** Where the server-to-client payloads are registered. */
	public static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverToClient() {
		return PayloadTypeRegistry.clientboundPlay();
	}

	/** The time of day in the overworld, in ticks since the world began. */
	public static long dayTime(Level level) {
		return level.getOverworldClockTime();
	}

	/** The type of a piece of data saved with the world, under {@code data/occupant/<name>}. */
	public static <T extends SavedData> SavedDataType<T> savedData(String name, Supplier<T> fresh, Codec<T> codec, DataFixTypes fix) {
		return new SavedDataType<>(com.wolfsmask.occupant.Occupant.id(name), fresh, codec, fix);
	}

	/** Keep a mob's paths out of water, lava, fire and anything else that hurts. */
	public static void avoidHazards(Mob mob) {
		mob.setPathfindingMalus(PathType.WATER, -1.0f);
		mob.setPathfindingMalus(PathType.LAVA, -1.0f);
		mob.setPathfindingMalus(PathType.FIRE, -1.0f);
		mob.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0f);
		mob.setPathfindingMalus(PathType.DAMAGING, -1.0f);
	}

	/** The level a player is in, as a server level. */
	public static ServerLevel level(ServerPlayer player) {
		return player.level();
	}

	/** How many chunks this player's game draws, as their client last said; 0 if it never says. */
	public static int viewDistance(ServerPlayer player) {
		return player.requestedViewDistance();
	}

	/** The player's bed or respawn anchor, if it is in the world they are standing in. */
	@Nullable
	public static BlockPos respawnPos(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		if (config == null || config.respawnData() == null) return null;
		return config.respawnData().dimension() == player.level().dimension() ? config.respawnData().pos() : null;
	}

	/** Set where the player respawns, in the world they are in, without telling them. */
	public static void setRespawn(ServerPlayer player, BlockPos pos) {
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(player.level().dimension(), pos, 0.0f, 0.0f), true), false);
	}

	/** Where players first appear in this world. */
	public static BlockPos spawnPos(ServerLevel level) {
		return level.getRespawnData().pos();
	}

	/** A copy of {@code stack} going by another name. */
	public static ItemStack named(ItemStack stack, Component name) {
		ItemStack copy = stack.copy();
		copy.set(DataComponents.CUSTOM_NAME, name);
		return copy;
	}

	/** A finished written book. */
	public static ItemStack writtenBook(String title, String author, java.util.List<String> pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		java.util.List<Filterable<Component>> content = new java.util.ArrayList<>();
		for (String page : pages) content.add(Filterable.<Component>passThrough(Component.literal(page)));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, content, true));
		return book;
	}
}
