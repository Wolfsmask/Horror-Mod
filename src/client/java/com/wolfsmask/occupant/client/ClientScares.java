package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The small things that happen on your side of the screen: your own heart, loud in your ears,
 * when it is close and you are not looking at it; its breath in cold air; the torch in your hand
 * guttering when it is near; and the "Saving world..." that is not saving the world.
 */
public final class ClientScares {
	private static int sinceBeat;
	private static int savingAge = -1;
	private static int savingLength;
	private static int savingWhich;

	private ClientScares() {
	}

	static void tick(Minecraft mc) {
		if (savingAge >= 0 && ++savingAge > savingLength) savingAge = -1;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.isPaused()) return;
		ClientConfig cfg = ClientConfig.get();
		double nearest = Double.MAX_VALUE;
		boolean seen = false;
		Vec3 look = player.getViewVector(1.0f);
		for (OccupantEntity e : mc.level.getEntitiesOfClass(OccupantEntity.class, player.getBoundingBox().inflate(40.0),
				e -> !e.isRemoved() && !e.isConcealed())) {
			double d = e.distanceTo(player);
			Vec3 to = e.position().add(0, 2.0, 0).subtract(player.getEyePosition()).normalize();
			boolean inView = look.dot(to) > 0.7;
			if (d < nearest) {
				nearest = d;
				seen = inView;
			}
			breathe(mc, e);
		}
		// Your heart, when it is close and you are not looking at it: faster the closer it is.
		if (cfg.heartbeat && nearest < 24.0 && !seen) {
			float close = (float) (1.0 - nearest / 24.0);
			int every = Math.round(26 - 14 * close);
			if (++sinceBeat >= every) {
				sinceBeat = 0;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.35f + 0.55f * close));
			}
		} else {
			sinceBeat = 0;
		}
		// The torch in your hand gutters when it is near.
		if (nearest < 16.0 && !cfg.reduceFlashing && holdingLight(player) && player.getRandom().nextFloat() < 0.015f) {
			ScreenEffects.flicker(6);
		}
	}

	/** In cold air, now and then, its breath. */
	private static void breathe(Minecraft mc, OccupantEntity e) {
		if ((e.tickCount + e.getId()) % 45 != 0) return;
		BlockPos head = BlockPos.containing(e.getX(), e.getY() + 3.0, e.getZ());
		if (mc.level.getBiome(head).value().getBaseTemperature() > 0.25f && head.getY() < 140) return;
		Vec3 facing = Vec3.directionFromRotation(0, e.getYHeadRot()).scale(0.35);
		for (int i = 0; i < 4; i++) {
			mc.level.addParticle(ParticleTypes.CLOUD, e.getX() + facing.x, e.getY() + 2.8, e.getZ() + facing.z,
					facing.x * 0.05, 0.01, facing.z * 0.05);
		}
	}

	private static boolean holdingLight(LocalPlayer p) {
		var item = p.getMainHandItem();
		var off = p.getOffhandItem();
		return item.is(Items.TORCH) || item.is(Items.SOUL_TORCH) || item.is(Items.LANTERN) || item.is(Items.SOUL_LANTERN)
				|| off.is(Items.TORCH) || off.is(Items.SOUL_TORCH) || off.is(Items.LANTERN) || off.is(Items.SOUL_LANTERN);
	}

	/** "Saving world..." for {@code ticks}; {@code which} picks what it says it is saving. */
	static void saving(int ticks, int which) {
		savingAge = 0;
		savingLength = Math.max(20, ticks);
		savingWhich = which;
	}

	static void render(GuiGraphicsExtractor g, int w, int h) {
		if (savingAge < 0) return;
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player != null ? mc.player.getName().getString() : "you";
		String text = switch (savingWhich) {
			case 1 -> "Saving you...";
			case 2 -> "Saving " + name + "...";
			default -> "Saving world...";
		};
		int x = w - mc.font.width(text) - 6;
		GuiCompat.text(g, mc.font, text, x, h - 14, 0xFFD0D0D0);
	}

	static void reset() {
		savingAge = -1;
		sinceBeat = 0;
	}
}
