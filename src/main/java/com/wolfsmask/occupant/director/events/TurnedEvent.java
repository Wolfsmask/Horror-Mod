package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Things in your base have been turned while you were not looking: the pictures in their frames
 * hang upside down, and the armour stands all face your bed.
 */
public final class TurnedEvent extends HorrorEvent {
	public static final String ID = "turned";

	public TurnedEvent() {
		super(ID, Tier.MINOR, 2, 4, 25);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		BlockPos bed = Spots.respawnPos(p);
		Vec3 face = bed != null ? Vec3.atCenterOf(bed) : p.position();
		int turned = 0;
		for (Entity e : ctx.world.getEntities(p, p.getBoundingBox().inflate(16.0))) {
			if (!Sight.isHidden(p, e.blockPosition())) continue;
			if (e instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
				frame.setRotation((frame.getRotation() + 4) % 8);
				turned++;
			} else if (e.getType() == EntityType.ARMOR_STAND) {
				float yaw = Sight.yawBetween(e.position(), face);
				e.setYRot(yaw);
				e.setYHeadRot(yaw);
				e.setYBodyRot(yaw);
				turned++;
			}
		}
		return turned == 0 ? null : new Timeline().at(0, pl -> {
		});
	}
}
