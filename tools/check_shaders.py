"""Compiles every core shader with glslangValidator (the reference GLSL compiler, as strict as AMD's drivers), with
Minecraft's #moj_import lines filled in first. Prints one ::error:: annotation per failing shader."""
import glob, os, re, subprocess, sys, tempfile

root = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/assets'
inc_dirs = glob.glob(os.path.join(root, '*', 'shaders', 'include'))

def inline(src, depth=0):
    def rep(m):
        name = m.group(1).split(':')[-1]
        for d in inc_dirs:
            p = os.path.join(d, name)
            if os.path.exists(p):
                return inline(open(p).read(), depth + 1) if depth < 5 else ''
        return '// missing include ' + m.group(1)
    return re.sub(r'#moj_import\s*[<"]([^>"]+)[>"]', rep, src)

bad = 0
files = sorted(glob.glob(os.path.join(root, '*', 'shaders', '**', '*.[vf]sh'), recursive=True))
for f in files:
    stage = 'vert' if f.endswith('.vsh') else 'frag'
    with tempfile.NamedTemporaryFile('w', suffix='.' + stage, delete=False) as t:
        t.write(inline(open(f).read()))
    r = subprocess.run(['glslangValidator', '-S', stage, t.name], capture_output=True, text=True)
    os.unlink(t.name)
    if r.returncode != 0:
        bad += 1
        msg = ' '.join(l for l in r.stdout.splitlines() if 'ERROR' in l)[:1500]
        print('::error::shader %s: %s' % (os.path.relpath(f, root), msg))
print('::notice::shaders checked with glslang: %d, failing: %d' % (len(files), bad))
