package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Before anything else, off camera: how are you playing tonight? Just playing, or recording a
 * video. Recording means the Creator Cut, the whole story in about forty minutes. Asked here, on
 * a plain screen of its own, so that the title screen after it can be shown on camera as it is,
 * with nothing on it but the way in.
 */
public final class ModeScreen extends Screen {
	private static final String HEADING = "Before you go in";
	private static final String PLAY = "PLAYING";
	private static final String PLAY_NOTE = "the slow burn · a long evening, or several";
	private static final String RECORD = "RECORDING";
	private static final String RECORD_NOTE = "the Creator Cut · about forty minutes · ending included";
	private static final String RECOMMENDED = "recommended for creators";
	private static final String FOOTER = "Pick one, and start recording on the next screen: it is the title.";

	/** What was chosen, this time the game has been open: null until then. */
	private static Boolean recording;
	private static boolean showing;
	private static boolean drawn;
	private static ModeScreen current;

	public ModeScreen() {
		super(Component.literal("The Occupant"));
	}

	/** Has the choice been made, this time the game has been open? */
	public static boolean chosen() {
		return recording != null;
	}

	/** Are worlds made from the title screen to be the Creator Cut? */
	public static boolean recording() {
		return recording != null && recording;
	}

	public static boolean showing() {
		return showing;
	}

	public static ModeScreen current() {
		return current;
	}

	/** Not asked, if the player said not to ask: their last choice stands. */
	static boolean skip() {
		ClientConfig c = ClientConfig.get();
		if (c.askHowPlaying) return false;
		recording = c.recording;
		return true;
	}

	@Override
	protected void init() {
		showing = true;
		current = this;
	}

	@Override
	public void removed() {
		showing = false;
		if (current == this) current = null;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Nothing: the whole screen is drawn below.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (!drawn) {
			drawn = true;
			Occupant.LOGGER.info("[client] the mode screen has been drawn");
		}
		int w = this.width;
		int h = this.height;
		TitleAtmosphere.drawScene(g, w, h, h);
		TitleAtmosphere.drawVignette(g, w, h, 1.0f);
		centred(g, HEADING, Math.round(h * 0.18f), 0xFF6A625A, 1.0f);

		boolean last = ClientConfig.get().recording;
		option(g, PLAY, PLAY_NOTE, null, box(false), inside(box(false), mouseX, mouseY), !last);
		option(g, RECORD, RECORD_NOTE, RECOMMENDED, box(true), inside(box(true), mouseX, mouseY), last);

		centred(g, FOOTER, h - 30, 0xFF4A4440, 1.0f);
	}

	private void option(GuiGraphicsExtractor g, String name, String note, String extra, int[] b, boolean over, boolean wasLast) {
		float scale = nameScale();
		int colour = over ? 0x8A1A16 : wasLast ? 0xD8CCBC : 0x9A8E80;
		int y = b[1] + 6;
		centred(g, name, y, 0xFF000000 | colour, scale);
		int ny = y + Math.round(this.font.lineHeight * scale) + 6;
		centred(g, note, ny, 0xFF000000 | (over ? 0xB8AC9C : 0x6A625A), 1.0f);
		if (extra != null) centred(g, extra, ny + this.font.lineHeight + 3, 0xFF000000 | (over ? 0xA03A30 : 0x7A3A32), 1.0f);
	}

	private void centred(GuiGraphicsExtractor g, String text, int y, int colour, float scale) {
		int tw = Math.round(this.font.width(text) * scale);
		GuiCompat.push(g);
		GuiCompat.translate(g, (this.width - tw) / 2f, y);
		GuiCompat.scale(g, scale);
		GuiCompat.text(g, this.font, text, 0, 0, colour);
		GuiCompat.pop(g);
	}

	private float nameScale() {
		return Math.max(1.5f, Math.min(2.5f, this.width / 260f));
	}

	/** Where each choice can be clicked: left, top, right, bottom. */
	private int[] box(boolean record) {
		float scale = nameScale();
		int wide = Math.max(Math.round(this.font.width(record ? RECORD : PLAY) * scale), this.font.width(record ? RECORD_NOTE : PLAY_NOTE));
		int tall = Math.round(this.font.lineHeight * scale) + 6 + this.font.lineHeight * 2 + 16;
		int top = Math.round(this.height * (record ? 0.56f : 0.33f));
		int left = (this.width - wide) / 2;
		return new int[]{left - 12, top, left + wide + 12, top + tall};
	}

	private static boolean inside(int[] box, double x, double y) {
		return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
	}

	/** For the automated test: the middle of either choice. */
	public double[] centreOf(boolean record) {
		int[] b = box(record);
		return new double[]{(b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		if (event.button() == 0) {
			boolean onRecord = inside(box(true), x, y);
			boolean onPlay = inside(box(false), x, y);
			Occupant.LOGGER.info("[client] mode clicked at {}, {}: {}", Math.round(x), Math.round(y),
					onRecord ? "recording" : onPlay ? "playing" : "nothing");
			if (onRecord || onPlay) {
				choose(onRecord);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void choose(boolean record) {
		recording = record;
		ClientConfig.get().recording = record;
		ClientConfig.save();
		Minecraft mc = this.minecraft;
		mc.setScreenAndShow(new GateScreen());
	}
}
