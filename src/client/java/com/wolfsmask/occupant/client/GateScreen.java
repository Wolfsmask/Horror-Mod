package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.WorldMode;
import com.wolfsmask.occupant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The first screen. Not a menu: the wood at night, the title, and one thing you can do. Hover over
 * it and it notices you: the letters go the colour of old blood and will not keep still, the dark
 * closes in, something whispers, and between the trees it comes forward, and a line writes itself
 * underneath. Click it and you make a world for it to live in.
 * <p>
 * Whether the world is the Creator Cut was asked before this, off camera ({@link ModeScreen}), so
 * there is nothing here to give the game away on a recording.
 * <p>
 * Or, very small at the bottom, you can leave it alone, and get the game's own menu back with the
 * haunting switched off for as long as the game is open.
 */
public final class GateScreen extends Screen {
	private static final String ENTER = "CREATE WORLD";
	private static final String UNDER = "it will be there when you arrive";
	private static final String LEAVE = "or leave it alone";

	private static boolean passed;
	private static boolean showing;
	private static boolean accepted;
	private static Boolean configured;
	private static boolean drawn;
	private static GateScreen current;

	private boolean hoverEnter;
	private boolean hoverLeave;
	private long hoverSince;
	private boolean whispered;

	public GateScreen() {
		super(Component.literal("The Occupant"));
	}

	/** Has the player chosen yet, this time the game has been open? */
	public static boolean passed() {
		return passed;
	}

	/** Did they choose it? */
	public static boolean accepted() {
		return accepted;
	}

	/** For the automated test: through the gate, haunting on, to the title screen. */
	public static void passForTest(Minecraft mc) {
		passed = true;
		accepted = true;
		mc.setScreenAndShow(new TitleScreen());
	}

	/** Is the gate on screen right now? */
	public static boolean showing() {
		return showing;
	}

	/** The gate on screen now, if any, for the automated test. */
	public static GateScreen current() {
		return current;
	}

	@Override
	protected void init() {
		showing = true;
		current = this;
		TitleAtmosphere.opened();
	}

	@Override
	public void tick() {
		TitleAtmosphere.tick(this.minecraft);
	}

	@Override
	public void removed() {
		showing = false;
		if (current == this) current = null;
		TitleAtmosphere.closed(this.minecraft);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Nothing: the whole scene is drawn below.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (!drawn) {
			drawn = true;
			Occupant.LOGGER.info("[client] the gate screen has been drawn");
		}
		int w = this.width;
		int h = this.height;
		boolean calm = ClientConfig.get().reduceFlashing;
		TitleAtmosphere.drawScene(g, w, h, h);

		// The way in.
		float scale = enterScale();
		int tw = Math.round(this.font.width(ENTER) * scale);
		int th = Math.round(this.font.lineHeight * scale);
		int tx = (w - tw) / 2;
		int ty = Math.round(h * 0.50f);
		boolean over = inside(enterBox(), mouseX, mouseY);
		if (over != hoverEnter) {
			hoverEnter = over;
			hoverSince = System.currentTimeMillis();
			if (over && !whispered) {
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.WHISPER.value(), 0.9f, 0.5f));
				whispered = true;
			}
		}
		TitleAtmosphere.beckon(over);
		float held = over ? Mth.clamp((System.currentTimeMillis() - hoverSince) / 1600f, 0f, 1f) : 0f;

		// When it is looking back at you, the dark comes in from the edges.
		if (held > 0f) TitleAtmosphere.drawVignette(g, w, h, held * 0.8f);

		TitleAtmosphere.drawLogo(g, w, h, Math.max(10, Math.round(h * 0.16f)));

		ThreadLocalRandom r = ThreadLocalRandom.current();
		int bone = 0xD8CCBC;
		int blood = 0x8A1A16;
		int colour = lerpColour(bone, blood, held);
		GuiCompat.push(g);
		GuiCompat.translate(g, tx, ty);
		GuiCompat.scale(g, scale);
		int x = 0;
		for (int i = 0; i < ENTER.length(); i++) {
			String c = String.valueOf(ENTER.charAt(i));
			// The letters will not keep still: a little always, much more once it has noticed you.
			int jx = calm ? 0 : (r.nextFloat() < 0.04f + held * 0.5f ? r.nextInt(3) - 1 : 0);
			int jy = calm ? 0 : (r.nextFloat() < 0.03f + held * 0.45f ? r.nextInt(3) - 1 : 0);
			if (held > 0.3f && !calm && r.nextFloat() < held * 0.25f) {
				GuiCompat.text(g, this.font, c, x + jx + 1, jy, 0x70000000 | blood);   // a ghost of it, a little behind
			}
			GuiCompat.text(g, this.font, c, x + jx, jy, 0xFF000000 | colour);
			x += this.font.width(c);
		}
		GuiCompat.pop(g);

		// And underneath, a line writes itself out while you hover, and unwrites when you stop.
		int shown = Math.round(UNDER.length() * held);
		if (shown > 0) {
			String line = UNDER.substring(0, shown);
			int lw = this.font.width(UNDER);
			int v = (int) Mth.lerp(held, 40f, 150f);
			GuiCompat.text(g, this.font, line, (w - lw) / 2, ty + th + 12, 0xFF000000 | v << 16 | (v - 10) << 8 | (v - 14));
		}

		// The way out, very small, at the bottom.
		int lw = this.font.width(LEAVE);
		int lx = (w - lw) / 2;
		int ly = h - 22;
		hoverLeave = inside(leaveBox(), mouseX, mouseY);
		int grey = hoverLeave ? 0x8C8478 : 0x3E3A36;
		GuiCompat.text(g, this.font, LEAVE, lx, ly, 0xFF000000 | grey);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Decided from where the click is, not from what was last drawn: the two are not always in
		// step, and a click that lands on the words must always count.
		double x = event.x();
		double y = event.y();
		if (event.button() == 0) {
			boolean onEnter = inside(enterBox(), x, y);
			boolean onLeave = inside(leaveBox(), x, y);
			Occupant.LOGGER.info("[client] gate clicked at {}, {}: {}", Math.round(x), Math.round(y),
					onEnter ? "create world" : onLeave ? "leave it alone" : "nothing");
			if (onEnter) {
				enter(ModeScreen.recording());
				return true;
			}
			if (onLeave) {
				leave();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private float enterScale() {
		return Math.max(2f, Math.min(3f, this.width / 220f));
	}

	/** Where CREATE WORLD can be clicked: left, top, right, bottom, in screen coordinates. */
	private int[] enterBox() {
		float scale = enterScale();
		int tw = Math.round(this.font.width(ENTER) * scale);
		int th = Math.round(this.font.lineHeight * scale);
		int tx = (this.width - tw) / 2;
		int ty = Math.round(this.height * 0.50f);
		return new int[]{tx - 12, ty - 8, tx + tw + 12, ty + th + 8};
	}

	/** Where "or leave it alone" can be clicked. */
	private int[] leaveBox() {
		int lw = this.font.width(LEAVE);
		int lx = (this.width - lw) / 2;
		int ly = this.height - 22;
		return new int[]{lx - 6, ly - 4, lx + lw + 6, ly + this.font.lineHeight + 4};
	}

	private static boolean inside(int[] box, double x, double y) {
		return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
	}

	/** For the automated test: the middle of either choice, in screen coordinates. */
	public double[] centreOf(boolean enter) {
		int[] b = enter ? enterBox() : leaveBox();
		return new double[]{(b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0};
	}

	/** Make a world for it, straight away. If that is cancelled, it comes back here, not to the menu. */
	private void enter(boolean creatorCut) {
		passed = true;
		accepted = true;
		setHaunting(true);
		WorldMode.requestForNextWorld(creatorCut);
		Minecraft mc = this.minecraft;
		QuickWorld.begin(mc, () -> {
			passed = false;
			WorldMode.requestForNextWorld(false);
			mc.setScreenAndShow(new GateScreen());
		});
	}

	/** The game's own menu, with the haunting off until the game is closed. */
	private void leave() {
		passed = true;
		accepted = false;
		setHaunting(false);
		this.minecraft.setScreenAndShow(new TitleScreen());
	}

	/** Only ever for this session: the config file itself is never changed. */
	private static void setHaunting(boolean on) {
		OccupantConfig cfg = OccupantConfig.get();
		if (configured == null) configured = cfg.enabled;
		cfg.enabled = on && configured;
	}

	private static int lerpColour(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
