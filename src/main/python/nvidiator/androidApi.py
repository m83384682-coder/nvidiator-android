# Nvidiator Android: the Android stand-ins for NVDA's api, speech, tones, ui and nvwave modules,
# for use from the Python console. They call back into the running AccessibilityService.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
#
# focus, nav, fg and caretObj are real android.view.accessibility.AccessibilityNodeInfo objects,
# so in the console you can call any Java method on them through Chaquopy, for example
# nav.getText(), nav.getClassName(), nav.getChildCount() or nav.performAction(16) (click).

import types

#: The NvidiatorService Java object, set by the service when it starts.
_service = None


def setService(service):
	global _service
	_service = service


def _svc():
	if _service is None:
		raise RuntimeError("The Nvidiator screen reader is not running")
	return _service


# --- api ---------------------------------------------------------------------------------


def getFocusObject():
	"""The node with input focus (AccessibilityNodeInfo), like NVDA's api.getFocusObject()."""
	return _svc().pyGetFocus() if _service else None


def getNavigatorObject():
	"""The node the swipes are on (the accessibility focus), like api.getNavigatorObject()."""
	return _svc().pyGetNavigator() if _service else None


def getForegroundObject():
	"""The root node of the active window, like api.getForegroundObject()."""
	return _svc().pyGetForeground() if _service else None


def getCaretObject():
	"""The focused editable node, or the focus object."""
	return getFocusObject()


def getFocusAncestors():
	"""The ancestors of the focus, root first, like api.getFocusAncestors()."""
	obj = getFocusObject()
	out = []
	while obj is not None:
		obj = obj.getParent()
		if obj is not None:
			out.insert(0, obj)
	return out


def snapshot(name):
	"""The object as it was when the NVDA menu or the console was opened (NVDA's snapshot)."""
	return _svc().pySnapshot(name) if _service else None


def setNavigatorObject(obj):
	"""Move the navigator (accessibility focus) to a node and speak it."""
	return bool(_svc().pyNavigateTo(obj))


def describe(obj=None) -> str:
	"""What the screen reader would say for a node (the navigator object by default)."""
	if obj is None:
		obj = getNavigatorObject()
	return str(_svc().pyDescribe(obj)) if obj is not None else ""


def getClipData() -> str:
	return str(_svc().pyGetClip() or "")


def copyToClip(text: str) -> bool:
	return bool(_svc().pyCopyToClip(str(text)))


# --- speech ------------------------------------------------------------------------------


def _speak(text, interrupt=False):
	_svc().pySpeak(str(text), bool(interrupt))


speech = types.SimpleNamespace(
	speakMessage=lambda text: _speak(text),
	speakText=lambda text: _speak(text),
	speak=lambda seq: _speak(" ".join(str(s) for s in seq if isinstance(s, str))),
	speakSpelling=lambda text: _svc().pySpell(str(text)),
	cancelSpeech=lambda: _svc().pyCancelSpeech(),
	getState=lambda: str(_svc().pySpeechMode()),
)


# --- ui ----------------------------------------------------------------------------------

ui = types.SimpleNamespace(
	message=lambda text, *a, **k: _speak(text),
	reviewMessage=lambda text, *a, **k: _speak(text),
)


# --- tones and nvwave --------------------------------------------------------------------


def _beep(hz: float, length: int, left: int = 50, right: int = 50, **kwargs):
	_svc().pyBeep(float(hz), int(length), int(left), int(right))


tones = types.SimpleNamespace(beep=_beep)


def _playWaveFile(fileName: str, asynchronous: bool = True, **kwargs):
	"""Plays one of NVDA's sounds by name, e.g. nvwave.playWaveFile('waves\\\\start.wav')."""
	name = str(fileName).replace("\\", "/").split("/")[-1]
	if name.endswith(".wav"):
		name = name[:-4]
	_svc().pyPlaySound(name)


nvwave = types.SimpleNamespace(playWaveFile=_playWaveFile)


def playFile(path: str) -> None:
	"""Plays a .wav file from a path, e.g. a sound shipped inside an add-on."""
	_svc().pyPlayFile(str(path))
