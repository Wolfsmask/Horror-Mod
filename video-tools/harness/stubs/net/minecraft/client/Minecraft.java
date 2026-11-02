package net.minecraft.client;
import net.minecraft.world.entity.Entity;
public class Minecraft {
	private static final Minecraft INSTANCE = new Minecraft();
	public static Minecraft getInstance() { return INSTANCE; }
	public Entity player;
	public final Options options = new Options();
	private final Window window = new Window();
	public Window getWindow() { return window; }
	public static class Window { public int getWidth() { return 1080; } public int getHeight() { return 1920; } }
	public static class Options { private final Opt<Integer> fov = new Opt<>(80); public Opt<Integer> fov() { return fov; } }
	public static class Opt<T> { private T v; Opt(T v) { this.v = v; } public T get() { return v; } }
}
