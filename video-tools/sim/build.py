"""SimTape: each Tape scene's camera, worked out exactly as the game test does, without the game."""
import re, subprocess, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
R = (REPO / "src/client/java/com/wolfsmask/occupant/client/test/Reel.java").read_text().split("\n")
T = (REPO / "src/client/java/com/wolfsmask/occupant/client/test/Tape.java").read_text().split("\n")
head = ((HERE / "head.java").read_text()
        .replace("public class Sim7", "public class SimTape"))
w = next(i for i, l in enumerate(R) if "static final class Way" in l)
h = next(i for i, l in enumerate(R) if "private static Vec3 head(" in l)
body = R[w:h]
out = [head, "\tstatic final int WIDE_FOV = 55;", "\tstatic final class Sitter { static final int TURN = 46, REACH = 58, COVER = 76; }"] + body
scenes = ["home", "camp", "pasture", "well", "field", "intruder", "visitor", "doorway", "radio", "house", "chapel", "graves", "knock", "morning", "mine"]
for name in scenes:
    s = next(i for i, l in enumerate(T) if re.match(rf"\tprivate static void {name}\(", l))
    e = next(i for i in range(s, len(T)) if "double[][] poses = f.flat(o.getY());" in T[i])
    sec = []
    keep = False
    for l in T[s + 1:e + 1]:
        if re.search(r"server|game|context|floor ==|site\(|cabin\(|BlockPos floor|BlockPos column|int top|BlockPos o = ", l):
            # setup that needs the game; but keep plain definitions that happen to say 'approach'
            if "double[][] approach" in l or "double[][] trail" in l:
                sec.append(l)
            continue
        if re.match(r"\t\t(\}\);|\t)", l) and not keep:
            # inside a server lambda: skipped with it
            pass
        sec.append(l)
    txt = "\n".join(sec)
    # drop the bodies of server lambdas left over (lines from 'ServerLevel level' to '});')
    txt = re.sub(r"\n\t\t\tServerLevel level = s\.overworld\(\);.*?\n\t\t\}\);", "", txt, flags=re.S)
    txt = re.sub(r"\n\t\t\tReel\.wood\(.*?\n", "\n", txt)
    txt = txt.replace("Reel.Fear", "Fear").replace("Reel.Way", "Way").replace("Reel.local", "local").replace("Reel.smooth", "smooth").replace("Reel.WIDE_FOV", "WIDE_FOV").replace("Cinematic.EYE", "EYE")
    out.append(f"\tstatic final double EYE = 1.62;" if name == scenes[0] else "")
    out.append(f"\tstatic void {name}(BlockPos o) {{")
    out.append(txt)
    out.append(f'\t\tdump("{name}", f, poses);')
    out.append("\t}")
out.append('''	static void dump(String name, Fear f, double[][] poses) {
		try (java.io.PrintWriter w = new java.io.PrintWriter(name + ".csv")) {
			for (double[] p : poses) w.printf(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.3f,%.3f,%.3f%n", p[0], p[1], p[2], p[3], p[4], p[5]);
		} catch (Exception e) { throw new RuntimeException(e); }
		try (java.io.PrintWriter w = new java.io.PrintWriter(name + ".raw")) {
			for (int t = 0; t < poses.length; t++) {
				double[] r = f.raw(t);
				StringBuilder b = new StringBuilder();
				for (int k = 0; k < r.length; k++) b.append(k == 0 ? "" : ",").append(String.format(java.util.Locale.ROOT, "%.5f", r[k]));
				w.println(b);
			}
		} catch (Exception e) { throw new RuntimeException(e); }
		try (java.io.PrintWriter w = new java.io.PrintWriter(name + ".marks")) {
			for (String m : f.marks()) w.println(m);
		} catch (Exception e) { throw new RuntimeException(e); }
		System.out.println(name + ": " + poses.length + " frames");
	}
	public static void main(String[] a) {
		BlockPos o = new BlockPos(0, 64, 0);
''' + "\n".join(f"\t\t{n}(o);" for n in scenes) + "\n\t}\n}\n")
(HERE / "SimTape.java").write_text("\n".join(out))
