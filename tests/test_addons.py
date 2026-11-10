"""Desktop tests for Nvidiator's add-on support (run with Python 3.11+).

python3 tests/test_addons.py

Builds small .nvda-addon packages, installs them with addonHandler, and checks loading, a gesture
running an add-on script, initTranslation, enable/disable/remove, and that a broken add-on is
turned off with a plain reason instead of stopping the screen reader.
"""
import json, os, shutil, sys, tempfile, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "src", "main", "python"))
from nvidiator import bridge, androidApi

APPDIR = os.path.join(HERE, "..", "src", "main", "assets", "nvda")
fails = 0
def check(label, got, want):
    global fails
    ok = got == want
    if not ok:
        fails += 1
    print(("PASS " if ok else "FAIL ") + label + ": " + repr(got) + ("" if ok else "  wanted " + repr(want)))


class FakeService:
    def __init__(self):
        self.spoken, self.beeps, self.files = [], [], []
    def pySpeak(self, text, interrupt): self.spoken.append(text)
    def pyBeep(self, hz, ms, l, r): self.beeps.append((hz, ms))
    def pyPlaySound(self, name): self.files.append(name)
    def pyPlayFile(self, path): self.files.append(path)
    def pyCancelSpeech(self): pass


GOOD = '''
import globalPluginHandler, ui, tones, addonHandler, api, speech, scriptHandler
from scriptHandler import script
addonHandler.initTranslation()

class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def __init__(self):
        super().__init__()
        ui.message("hello loaded")

    @script(description=_("Says hee-haw"), gesture="kb:NVDA+shift+h")
    def script_bray(self, gesture):
        tones.beep(440, 50)
        ui.message(_("Hee-haw"))

    __gestures = {"ts:3finger_double_tap": "bray"}

    def terminate(self):
        ui.message("hello stopped")
'''

BROKEN_IMPORT = '''
import globalPluginHandler
import winUser  # Windows only
class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    pass
'''

BROKEN_SCRIPT = '''
import globalPluginHandler
from scriptHandler import script
class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    @script(gesture="kb:NVDA+shift+b")
    def script_boom(self, gesture):
        raise RuntimeError("boom")
'''


def makeBundle(dirpath, name, summary, plugin, extra=None):
    path = os.path.join(dirpath, name + ".nvda-addon")
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.ini", 'name = %s\nsummary = "%s"\nversion = 1.0\nauthor = "Iron Donkey <a@b.c>"\n'
                   'description = """A test\nadd-on."""\nminimumNVDAVersion = 2023.1\n\n[translations]\n' % (name, summary))
        z.writestr("globalPlugins/%s.py" % name, plugin)
        for k, v in (extra or {}).items():
            z.writestr(k, v)
    return path


def makeMo(msgs):
    """A minimal GNU .mo file."""
    import struct
    keys = sorted(msgs)
    ids = b"".join(k.encode() + b"\0" for k in keys)
    strs = b"".join(msgs[k].encode() + b"\0" for k in keys)
    n = len(keys)
    koff = 7 * 4 + 16 * n
    voff = koff + len(ids)
    kt, vt, o1, o2 = [], [], 0, 0
    for k in keys:
        kt += [len(k.encode()), koff + o1]; o1 += len(k.encode()) + 1
        vt += [len(msgs[k].encode()), voff + o2]; o2 += len(msgs[k].encode()) + 1
    return struct.pack("Iiiiiii", 0x950412de, 0, n, 7 * 4, 7 * 4 + 8 * n, 0, 0) + struct.pack("%di" % (2 * n), *kt) + struct.pack("%di" % (2 * n), *vt) + ids + strs


assert bridge.init(APPDIR, "de") == "de"
svc = FakeService()
androidApi.setService(svc)
import addonHandler

tmp = tempfile.mkdtemp()
try:
    addonsDir = os.path.join(tmp, "addons")
    check("no add-ons at first", json.loads(addonHandler.initialize(addonsDir)), [])
    check("empty list", json.loads(addonHandler.listAddons()), [])

    # manifest parsing
    m = addonHandler.parseManifest('name = x\nsummary = "X thing"\ndescription = """line1\nline2"""\nversion = 2.0 # c\n[translations]\nname = no')
    check("manifest parse", (m["name"], m["summary"], m["description"], m["version"]), ("x", "X thing", "line1\nline2", "2.0"))

    good = makeBundle(tmp, "hello", "Hello add-on", GOOD, {"locale/de/LC_MESSAGES/nvda.mo": makeMo({"Hee-haw": "I-ah", "Says hee-haw": "Sagt I-ah"})})
    check("bundle info", json.loads(addonHandler.bundleInfo(good))["summary"], "Hello add-on")
    check("install returns name", addonHandler.installBundle(good), "hello")
    check("restart pending after install", addonHandler.isRestartPending(), True)
    check("not running before restart", addonHandler.getRunningAddons(), [])
    check("restart loads, no failures", json.loads(addonHandler.restart()), [])
    check("plugin spoke on load", svc.spoken[-1], "hello loaded")
    check("running after restart", [a.name for a in addonHandler.getRunningAddons()], ["hello"])
    check("script by decorator gesture", addonHandler.executeGesture("kb:shift+NVDA+H"), True)
    check("translated with initTranslation", svc.spoken[-1], "I-ah")
    check("script beeped", svc.beeps[-1], (440.0, 50))
    check("script by __gestures", addonHandler.executeGesture("ts:3finger_double_tap"), True)
    check("unbound gesture", addonHandler.executeGesture("kb:NVDA+shift+z"), False)
    binds = json.loads(addonHandler.gestureBindings())
    check("bindings listed", sorted(b["gesture"] for b in binds), ["kb:nvda+shift+h", "ts:3finger_double_tap"])
    check("binding description translated", [b["description"] for b in binds if b["gesture"] == "kb:nvda+shift+h"], ["Sagt I-ah"])

    addonHandler.setEnabled("hello", False)
    check("disabled status", json.loads(addonHandler.listAddons())[0]["status"], "disabled")
    addonHandler.restart()
    check("terminate ran", "hello stopped" in svc.spoken, True)
    check("disabled add-on not running", addonHandler.executeGesture("kb:NVDA+shift+h"), False)
    addonHandler.setEnabled("hello", True)
    addonHandler.restart()
    check("re-enabled runs", addonHandler.executeGesture("kb:NVDA+shift+h"), True)

    # A Windows-only add-on is turned off with a plain reason, and the others keep running.
    addonHandler.installBundle(makeBundle(tmp, "winonly", "Windows thing", BROKEN_IMPORT))
    failed = json.loads(addonHandler.restart())
    check("broken add-on reported", [f["summary"] for f in failed], ["Windows thing"])
    check("plain reason", failed[0]["reason"], "It needs the winUser module, which only exists in NVDA on Windows.")
    states = {a["name"]: a["status"] for a in json.loads(addonHandler.listAddons())}
    check("broken marked failed", states, {"hello": "enabled", "winonly": "failed"})
    check("good add-on still runs", addonHandler.executeGesture("kb:NVDA+shift+h"), True)
    check("failure not repeated next start", json.loads(addonHandler.restart()), [])

    # A script that raises turns its add-on off.
    addonHandler.installBundle(makeBundle(tmp, "boom", "Boom", BROKEN_SCRIPT))
    addonHandler.restart()
    try:
        addonHandler.executeGesture("kb:NVDA+shift+b")
        raised = None
    except addonHandler.AddonScriptError as e:
        raised = (e.addonName, e.reason)
    check("script error turns it off", raised, ("boom", "It stopped with an error: RuntimeError: boom"))
    check("no longer runs", addonHandler.executeGesture("kb:NVDA+shift+b"), False)

    # Bad packages
    bad = os.path.join(tmp, "bad.nvda-addon")
    open(bad, "wb").write(b"not a zip")
    try:
        addonHandler.installBundle(bad); got = "installed"
    except addonHandler.AddonError:
        got = "refused"
    check("invalid file refused", got, "refused")
    evil = os.path.join(tmp, "evil.nvda-addon")
    with zipfile.ZipFile(evil, "w") as z:
        z.writestr("manifest.ini", "name = evil\n")
        z.writestr("../../escape.txt", "x")
    try:
        addonHandler.installBundle(evil); got = "installed"
    except addonHandler.AddonError:
        got = "refused"
    check("path escape refused", (got, os.path.exists(os.path.join(tmp, "escape.txt"))), ("refused", False))

    addonHandler.remove("winonly")
    addonHandler.remove("boom")
    check("removed", [a["name"] for a in json.loads(addonHandler.listAddons())], ["hello"])
    check("state survives", os.path.isfile(os.path.join(addonsDir, ".state.json")), True)

    # NVDA's wording in German for the add-on screens
    ui = json.loads(bridge.uiStrings())
    check("German Add-on Store title", ui["addonStoreTitle"], "Store f\u00fcr NVDA-Erweiterungen" if ui["addonStoreTitle"] != "Add-on Store" else "translated")
    check("German install from external", ui["installExternal"] != "Install from external source", True)
    check("German welcome title", ui["welcomeTitle"] != "Welcome to NVDA", True)
    check("German input gestures", ui["inputGesturesTitle"] != "Input Gestures", True)
finally:
    addonHandler.terminate()
    shutil.rmtree(tmp, ignore_errors=True)

print("%d failed" % fails)
sys.exit(1 if fails else 0)
