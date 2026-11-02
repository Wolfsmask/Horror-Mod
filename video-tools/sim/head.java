import java.util.*;
import java.util.function.*;
public class Sim7 {
	static final class Person {
	static final int LANDED = 8;
	static final int SCRAMBLE = 11;
	static final int[] SHOVES = {12, 16, 21};
	static final int SHOVE_TICKS = 4;
	static final int KICK = 26;
	static final int[] KICKS = {27, 30, 33};
	static final int KICK_TICKS = 3;
	static final int ROLL = 37, ROLL_TICKS = 16;
	static final int PUSHUP = 53;
	static final int RISE = 60;
	static final int SLIP = 68;
	static final int CRAWL = 75;
	static final int[] PULLS = {76, 82};
	static final int PULL_TICKS = 6;
	static final int DRAG = 88;
	static final int STRIKE = 98;
		static double itCame(double t) { return 0; }
	}
	static final double SPRINT = 0.27;
	static final class Cinematic { static final double EYE = 1.62; }
	static final class Vec3 { static final Vec3 ZERO = new Vec3(0,0,0); final double x,y,z; Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
		Vec3 lerp(Vec3 o,double t){return new Vec3(x+(o.x-x)*t,y+(o.y-y)*t,z+(o.z-z)*t);} static Vec3 atBottomCenterOf(BlockPos p){return new Vec3(p.x+0.5,p.y,p.z+0.5);}
		Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);}
		Vec3 add(Vec3 o){return new Vec3(x+o.x,y+o.y,z+o.z);}
		Vec3 subtract(Vec3 o){return new Vec3(x-o.x,y-o.y,z-o.z);}
		Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
		double lengthSqr(){return x*x+y*y+z*z;}
		double length(){return Math.sqrt(x*x+y*y+z*z);} Vec3 normalize(){double l=length();return l<1e-9?ZERO:new Vec3(x/l,y/l,z/l);}
		double distanceTo(Vec3 o){return subtract(o).length();} double dot(Vec3 o){return x*o.x+y*o.y+z*o.z;} double horizontalDistance(){return Math.sqrt(x*x+z*z);} }
	static final class BlockPos { final int x,y,z; BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
		int getX(){return x;} int getY(){return y;} int getZ(){return z;} BlockPos offset(int a,int b,int c){return new BlockPos(x+a,y+b,z+c);} BlockPos above(){return offset(0,1,0);} static BlockPos containing(double a,double b,double c){return new BlockPos((int)Math.floor(a),(int)Math.floor(b),(int)Math.floor(c));} }
	static final class Mth { static double clamp(double v,double a,double b){return v<a?a:v>b?b:v;} static float clamp(float v,float a,float b){return v<a?a:v>b?b:v;} static int clamp(int v,int a,int b){return v<a?a:v>b?b:v;} static int floor(double v){return (int)Math.floor(v);}
		static double wrapDegrees(double d){d%=360; if(d>=180)d-=360; if(d<-180)d+=360; return d;} static float wrapDegrees(float d){return (float)wrapDegrees((double)d);} }
	static double smooth(double x){double c=Mth.clamp(x,0,1);return c*c*(3-2*c);}
	static Vec3 local(BlockPos o,double x,double y,double z){return new Vec3(o.getX()+0.5+x,o.getY()+y,o.getZ()+0.5+z);}
