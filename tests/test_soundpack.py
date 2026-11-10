"""Desktop tests for add-on events and speech hooks (run with Python 3.11+).

python3 tests/test_soundpack.py

Installs a sample sound-pack add-on in the style of NVDA's earcon / role-sound add-ons. It plays a
.wav from its own folder on event_gainFocus by role and state, beeps on stateChange, plays a sound
on foreground, filters speech with filter_speechSequence, puts a WaveFileCommand into speech, and
watches pre_speech. Also checks that an add-on whose event handler raises is turned off while the
other add-ons keep getting events.
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
        self.spoken, self.beeps, self.files, self.sounds = [], [], [], []
    def pySpeak(self, text, interrupt): self.spoken.append(text)
    def pyBeep(self, hz, ms, l, r): self.beeps.append((hz, ms))
    def pyPlaySound(self, name): self.sounds.append(name)
    def pyPlayFile(self, path): self.files.append(os.path.basename(path))
    def pyCancelSpeech(self): pass
    def pySpeechMode(self): return "talk"


SOUNDPACK = '''
import os, globalPluginHandler, nvwave, tones, controlTypes, speech
from speech.commands import WaveFileCommand
from NVDAObjects.IAccessible import IAccessible
HERE = os.path.dirname(__file__)
SOUNDS = os.path.join(HERE, "..", "..", "sounds")
seen = []

def dropRoleWords(seq):
    return [s.replace(" button", "") if isinstance(s, str) else s for s in seq]

def watch(speechSequence, **kw):
    seen.append(list(speechSequence))

class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def __init__(self):
        super().__init__()
        speech.extensions.filter_speechSequence.register(dropRoleWords)
        speech.extensions.pre_speech.register(watch)

    def event_gainFocus(self, obj, nextHandler):
        if isinstance(obj, IAccessible):
            return nextHandler()
        name = {controlTypes.Role.BUTTON: "button", controlTypes.Role.CHECKBOX: "checkbox"}.get(obj.role)
        if name:
            if controlTypes.State.CHECKED in obj.states:
                name += "_checked"
            nvwave.playWaveFile(os.path.join(SOUNDS, name + ".wav"))
        nextHandler()

    def event_stateChange(self, obj, nextHandler):
        tones.beep(880 if controlTypes.State.CHECKED in obj.states else 440, 30)
        nextHandler()

    def event_foreground(self, obj, nextHandler):
        nvwave.playWaveFile(os.path.join(SOUNDS, "window.wav"))
        nextHandler()

    def terminate(self):
        speech.extensions.filter_speechSequence.unregister(dropRoleWords)
        speech.extensions.pre_speech.unregister(watch)
'''

WAVE_IN_SPEECH = '''
import globalPluginHandler, speech
from speech.commands import WaveFileCommand, BeepCommand
def addEarcon(seq):
    if seq and seq[0] == "Settings":
        return [WaveFileCommand("waves\\\\browseMode.wav"), BeepCommand(500, 20)] + list(seq)
    return seq
class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def __init__(self):
        super().__init__()
        speech.extensions.filter_speechSequence.register(addEarcon)
    def terminate(self):
        speech.extensions.filter_speechSequence.unregister(addEarcon)
'''

BAD_EVENT = '''
import globalPluginHandler
class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def event_gainFocus(self, obj, nextHandler):
        raise ValueError("bad focus")
'''

BAD_FILTER = '''
import globalPluginHandler, speech
def broken(seq):
    raise RuntimeError("filter broke")
class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def __init__(self):
        super().__init__()
        speech.extensions.filter_speechSequence.register(broken)
    def terminate(self):
        speech.extensions.filter_speechSequence.unregister(broken)
'''


def makeBundle(dirpath, name, summary, plugin, extra=None):
    path = os.path.join(dirpath, name + ".nvda-addon")
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.ini", 'name = %s\nsummary = "%s"\nversion = 1.0\nauthor = "Iron Donkey"\nminimumNVDAVersion = 2023.1\n' % (name, summary))
        z.writestr("globalPlugins/%s/__init__.py" % name, plugin)
        for k, v in (extra or {}).items():
            z.writestr(k, v)
    return path


RIFF = b"RIFF$\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00\x44\xac\x00\x00\x88X\x01\x00\x02\x00\x10\x00data\x00\x00\x00\x00"
NODE_BUTTON = {"cls": "android.widget.Button", "pkg": "com.whatsapp", "name": "Send", "clickable": True, "enabled": True, "focusable": True}
NODE_CHECKED = {"cls": "android.widget.CheckBox", "name": "Wi-Fi", "checkable": True, "checked": True, "enabled": True}

assert bridge.init(APPDIR, "en") == "en"
svc = FakeService()
androidApi.setService(svc)
import addonHandler, speech

tmp = tempfile.mkdtemp()
try:
    addonsDir = os.path.join(tmp, "addons")
    addonHandler.initialize(addonsDir)
    check("nothing wanted with no add-ons", json.loads(addonHandler.wantedInfo()), {"events": [], "speech": False})
    sounds = {"sounds/%s.wav" % n: RIFF for n in ("button", "checkbox", "checkbox_checked", "window")}
    addonHandler.installBundle(makeBundle(tmp, "earcons", "Earcons sample", SOUNDPACK, sounds))
    check("loads", json.loads(addonHandler.restart()), [])
    info = json.loads(addonHandler.wantedInfo())
    check("wanted events", info["events"], ["foreground", "gainFocus", "stateChange"])
    check("speech hooks seen", info["speech"], True)

    from NVDAObjects import NVDAObject
    import controlTypes
    o = NVDAObject.fromNodeInfo(NODE_BUTTON)
    check("object role", o.role, controlTypes.Role.BUTTON)
    check("object name", o.name, "Send")
    check("object app", o.appModule.appName, "com.whatsapp")

    check("gainFocus no failures", json.loads(addonHandler.dispatchEvent("gainFocus", json.dumps(NODE_BUTTON))), [])
    check("button earcon played from add-on folder", svc.files[-1], "button.wav")
    addonHandler.dispatchEvent("gainFocus", json.dumps(NODE_CHECKED))
    check("checked checkbox earcon", svc.files[-1], "checkbox_checked.wav")
    addonHandler.dispatchEvent("stateChange", json.dumps(NODE_CHECKED))
    check("stateChange beep", svc.beeps[-1], (880.0, 30))
    addonHandler.dispatchEvent("foreground", json.dumps({"cls": "android.widget.FrameLayout", "pkg": "com.android.settings"}))
    check("foreground sound", svc.files[-1], "window.wav")
    check("unhandled event is harmless", json.loads(addonHandler.dispatchEvent("nameChange", json.dumps(NODE_BUTTON))), [])
    check("bad node JSON is harmless", addonHandler.dispatchEvent("gainFocus", "{not json"), "[]")

    addonHandler.speakThrough("Send button", False)
    check("speech filter changed what was spoken", svc.spoken[-1], "Send")
    import globalPlugins.earcons as ep
    check("pre_speech saw the sequence", ep.seen[-1], ["Send button"])

    # Waves and beeps inside a speech sequence play in order with the text.
    addonHandler.installBundle(makeBundle(tmp, "wavespeech", "Wave in speech", WAVE_IN_SPEECH))
    addonHandler.restart()
    n0 = len(svc.sounds)
    addonHandler.speakThrough("Settings", False)
    check("wave command played NVDA sound", svc.sounds[n0:], ["browseMode"])
    check("beep command played", svc.beeps[-1], (500.0, 20))
    check("text after wave", svc.spoken[-1], "Settings")

    # nvwave.decide_playWaveFile can stop a sound, as in NVDA.
    import nvwave
    stop = lambda **kw: False
    nvwave.decide_playWaveFile.register(stop)
    nf = len(svc.files)
    addonHandler.dispatchEvent("gainFocus", json.dumps(NODE_BUTTON))
    check("decide_playWaveFile stops sound", len(svc.files), nf)
    nvwave.decide_playWaveFile.unregister(stop)

    # A broken filter never stops speech.
    addonHandler.installBundle(makeBundle(tmp, "badfilter", "Bad filter", BAD_FILTER))
    addonHandler.restart()
    addonHandler.speakThrough("OK button", False)
    check("speech still spoken past a broken filter", svc.spoken[-1], "OK")

    # An event handler that raises turns its add-on off; the sound pack still gets the event.
    addonHandler.installBundle(makeBundle(tmp, "badevent", "Bad event", BAD_EVENT))
    addonHandler.restart()
    nf = len(svc.files)
    failed = json.loads(addonHandler.dispatchEvent("gainFocus", json.dumps(NODE_BUTTON)))
    check("bad event add-on reported", [(f["summary"], f["reason"]) for f in failed], [("Bad event", "It stopped with an error: ValueError: bad focus")])
    check("sound pack still played", len(svc.files), nf + 1)
    check("bad add-on marked failed", {a["name"]: a["status"] for a in json.loads(addonHandler.listAddons())}["badevent"], "failed")
    check("no second report", json.loads(addonHandler.dispatchEvent("gainFocus", json.dumps(NODE_BUTTON))), [])

    # Turning the sound pack off removes its hooks.
    addonHandler.setEnabled("earcons", False)
    addonHandler.setEnabled("wavespeech", False)
    addonHandler.setEnabled("badfilter", False)
    addonHandler.restart()
    check("hooks gone after disable", json.loads(addonHandler.wantedInfo()), {"events": [], "speech": False})
    addonHandler.speakThrough("Back button", False)
    check("speech unfiltered again", svc.spoken[-1], "Back button")

    # ui.message goes through the speech hooks too, like NVDA's.
    check("speech mode", speech.getState(), speech.SpeechMode.talk)
finally:
    addonHandler.terminate()
    shutil.rmtree(tmp, ignore_errors=True)

print("%d failed" % fails)
sys.exit(1 if fails else 0)
