package net.minecraft.util;
public class Mth {
	public static final float PI = (float) Math.PI;
	public static float sin(float v) { return (float) Math.sin(v); }
	public static float cos(float v) { return (float) Math.cos(v); }
	public static float sqrt(float v) { return (float) Math.sqrt(v); }
	public static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }
	public static double clamp(double v, double a, double b) { return v < a ? a : Math.min(v, b); }
	public static int clamp(int v, int a, int b) { return v < a ? a : Math.min(v, b); }
	public static long clamp(long v, long a, long b) { return v < a ? a : Math.min(v, b); }
	public static float lerp(float t, float a, float b) { return a + t * (b - a); }
	public static double lerp(double t, double a, double b) { return a + t * (b - a); }
	public static int floor(double v) { return (int) Math.floor(v); }
	public static int floor(float v) { return (int) Math.floor(v); }
	public static int ceil(double v) { return (int) Math.ceil(v); }
	public static int ceil(float v) { return (int) Math.ceil(v); }
	public static double atan2(double y, double x) { return Math.atan2(y, x); }
	public static float wrapDegrees(float d) { float f = d % 360.0F; if (f >= 180.0F) f -= 360.0F; if (f < -180.0F) f += 360.0F; return f; }
	public static double wrapDegrees(double d) { double f = d % 360.0; if (f >= 180.0) f -= 360.0; if (f < -180.0) f += 360.0; return f; }
	public static float abs(float v) { return Math.abs(v); }
	public static float rotLerp(float t, float a, float b) { return a + t * wrapDegrees(b - a); }
	public static float square(float v) { return v * v; }
	public static double square(double v) { return v * v; }
	public static double smoothstep(double x) { return x * x * x * (x * (x * 6.0 - 15.0) + 10.0); }
}
