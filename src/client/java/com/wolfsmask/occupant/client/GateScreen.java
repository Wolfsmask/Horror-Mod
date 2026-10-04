package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
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

	@Override
	protected void init() {
		showing = true;
		TitleAtmosphere.opened();
	}

	@Override
	public void tick() {
		TitleAtmosphere.tick(this.minecraft);
	}

	@Override
	public void removed() {
		showing = false;
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
		int w = this.width;
		int h = this.height;
		boolean calm = ClientConfig.get().reduceFlashing;
		TitleAtmosphere.drawScene(g, w, h, h);

		// The way in.
		float scale = Math.max(2f, Math.min(3f, w / 220f));
		int tw = Math.round(this.font.width(ENTER) * scale);
		int th = Math.round(this.font.lineHeight * scale);
		int tx = (w - tw) / 2;
		int ty = Math.round(h * 0.58f);
		boolean over = mouseX >= tx - 12 && mouseX <= tx + tw + 12 && mouseY >= ty - 8 && mouseY <= ty + th + 8;
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
		g.pose().pushMatrix();
		g.pose().translate(tx, ty);
		g.pose().scale(scale, scale);
		int x = 0;
		for (int i = 0; i < ENTER.length(); i++) {
			String c = String.valueOf(ENTER.charAt(i));
			// The letters will not keep still: a little always, much more once it has noticed you.
			int jx = calm ? 0 : (r.nextFloat() < 0.04f + held * 0.5f ? r.nextInt(3) - 1 : 0);
			int jy = calm ? 0 : (r.nextFloat() < 0.03f + held * 0.45f ? r.nextInt(3) - 1 : 0);
			if (held > 0.3f && !calm && r.nextFloat() < held * 0.25f) {
				g.text(this.font, c, x + jx + 1, jy, 0x70000000 | blood, false);   // a ghost of it, a little behind
			}
			g.text(this.font, c, x + jx, jy, 0xFF000000 | colour, false);
			x += this.font.width(c);
		}
		g.pose().popMatrix();

		// And underneath, a line writes itself out while you hover, and unwrites when you stop.
		int shown = Math.round(UNDER.length() * held);
		if (shown > 0) {
			String line = UNDER.substring(0, shown);
			int lw = this.font.width(UNDER);
			int v = (int) Mth.lerp(held, 40f, 150f);
			g.text(this.font, line, (w - lw) / 2, ty + th + 12, 0xFF000000 | v << 16 | (v - 10) << 8 | (v - 14), false);
		}

		// The way out, very small, at the bottom.
		int lw = this.font.width(LEAVE);
		int lx = (w - lw) / 2;
		int ly = h - 22;
		hoverLeave = mouseX >= lx - 6 && mouseX <= lx + lw + 6 && mouseY >= ly - 4 && mouseY <= ly + this.font.lineHeight + 4;
		int grey = hoverLeave ? 0x8C8478 : 0x3E3A36;
		g.text(this.font, LEAVE, lx, ly, 0xFF000000 | grey, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) return super.mouseClicked(event, doubleClick);
		if (hoverEnter) {
			enter();
			return true;
		}
		if (hoverLeave) {
			leave();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Make a world for it. Backing out of world creation comes back here, not to the menu. */
	private void enter() {
		passed = true;
		accepted = true;
		setHaunting(true);
		Minecraft mc = this.minecraft;
		CreateWorldScreen.openFresh(mc, () -> {
			passed = false;
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
