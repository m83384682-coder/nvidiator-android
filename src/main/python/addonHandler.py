# Nvidiator Android: add-on support, modelled on NVDA's addonHandler.
# An NVDA add-on package (.nvda-addon) is a zip with a manifest.ini. Nvidiator installs it into
# its own add-ons folder and loads the add-on's globalPlugins in Python, with stand-ins for the NVDA
# modules add-ons import most (api, ui, speech, tones, nvwave, scriptHandler, inputCore,
# globalPluginHandler, addonHandler, config, logHandler). App modules, synth drivers, braille
# display drivers and vision providers are Windows-specific and are not loaded.
# An add-on whose plugin fails to load is turned off, and the reason is kept so it can be shown
# in plain words. Nothing an add-on does can take over the volume keys: those never reach Python.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.

import gettext
import importlib
import inspect
import json
import os
import pkgutil
import re
import shutil
import sys
import traceback
import types
import zipfile

from logHandler import log

BUNDLE_EXTENSION = "nvda-addon"
MANIFEST_FILENAME = "manifest.ini"

#: Where add-ons are installed. Set by initialize(); the Java side passes the app's files folder.
_addonsDir = None
_stateFile = None
#: name -> Addon, for every installed add-on
_installed = {}
#: name -> list of plugin instances, for every running add-on
_running = {}
#: name -> plain-language reason the add-on was turned off
_failures = {}
#: add-ons changed since the last load, so a restart is needed
_pendingRestart = False
_state = {"disabled": []}


class AddonError(Exception):
	"""An add-on package that cannot be installed."""


# --- manifest ----------------------------------------------------------------------------

_KEY_RE = re.compile(r"^\s*([A-Za-z_][\w]*)\s*=\s*(.*)$")


def parseManifest(text: str) -> dict:
	"""Reads the top-level keys of an NVDA manifest.ini (a configobj file): plain values,
	"quoted" values and \"\"\"triple quoted\"\"\" multi-line values. Sections such as
	[translations] are skipped."""
	out = {}
	lines = text.replace("\r\n", "\n").split("\n")
	i = 0
	while i < len(lines):
		line = lines[i]
		i += 1
		if line.strip().startswith("["):
			break
		m = _KEY_RE.match(line)
		if not m:
			continue
		key, value = m.group(1), m.group(2).strip()
		for q in ('"""', "'''"):
			if value.startswith(q):
				body = value[3:]
				while q not in body and i < len(lines):
					body += "\n" + lines[i]
					i += 1
				value = body.split(q)[0]
				break
		else:
			if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
				value = value[1:-1]
			else:
				value = value.split(" #")[0].strip()
		out[key] = value
	return out


def _readManifest(path: str) -> dict:
	with open(path, encoding="utf-8-sig") as f:
		return parseManifest(f.read())


# --- Addon -------------------------------------------------------------------------------


class Addon:
	"""One installed add-on, with NVDA's attribute names."""

	def __init__(self, path: str):
		self.path = path
		self.manifest = _readManifest(os.path.join(path, MANIFEST_FILENAME))
		self.name = self.manifest.get("name") or os.path.basename(path)
		self.version = self.manifest.get("version", "")

	@property
	def isDisabled(self) -> bool:
		return self.name in _state["disabled"]

	@property
	def isRunning(self) -> bool:
		return self.name in _running

	def getTranslationsInstance(self, domain="nvda"):
		import languageHandler

		localeDir = os.path.join(self.path, "locale")
		lang = languageHandler.getLanguage()
		return gettext.translation(domain, localedir=localedir_or(localeDir), languages=[lang, lang.split("_")[0]], fallback=True)

	def getDocFilePath(self, fileName=None):
		return None

	def asDict(self) -> dict:
		m = self.manifest
		if self.name in _failures:
			status = "failed"
		elif self.isDisabled:
			status = "disabled"
		else:
			status = "enabled"
		return {
			"name": self.name,
			"summary": m.get("summary", self.name),
			"version": self.version,
			"author": m.get("author", ""),
			"description": m.get("description", ""),
			"status": status,
			"running": self.isRunning,
			"failure": _failures.get(self.name, ""),
		}


def localedir_or(path):
	return path if os.path.isdir(path) else None


# --- state -------------------------------------------------------------------------------


def _loadState():
	global _state
	try:
		with open(_stateFile, encoding="utf-8") as f:
			_state = json.load(f)
	except (OSError, ValueError):
		_state = {"disabled": []}
	_state.setdefault("disabled", [])


def _saveState():
	os.makedirs(_addonsDir, exist_ok=True)
	_state["failed"] = dict(_failures)
	tmp = _stateFile + ".tmp"
	with open(tmp, "w", encoding="utf-8") as f:
		json.dump(_state, f)
	os.replace(tmp, _stateFile)


def _scanInstalled():
	_installed.clear()
	if not _addonsDir or not os.path.isdir(_addonsDir):
		return
	for entry in sorted(os.listdir(_addonsDir)):
		path = os.path.join(_addonsDir, entry)
		if entry.startswith(".") or not os.path.isfile(os.path.join(path, MANIFEST_FILENAME)):
			continue
		try:
			addon = Addon(path)
		except Exception:
			log.error("Could not read the manifest of %s" % entry, exc_info=True)
			continue
		_installed[addon.name] = addon


# --- loading -----------------------------------------------------------------------------


def _plainReason(exc: BaseException) -> str:
	"""A short reason a person can read, instead of a traceback."""
	if isinstance(exc, ModuleNotFoundError):
		return "It needs the %s module, which only exists in NVDA on Windows." % exc.name
	if isinstance(exc, ImportError):
		return "It uses a part of NVDA that Nvidiator does not have: %s" % exc
	if isinstance(exc, (AttributeError, NameError)):
		return "It uses a part of NVDA that Nvidiator does not have: %s" % exc
	return "It stopped with an error: %s: %s" % (type(exc).__name__, exc)


def _ensurePackage(name: str) -> types.ModuleType:
	pkg = sys.modules.get(name)
	if pkg is None:
		pkg = types.ModuleType(name)
		pkg.__path__ = []
		sys.modules[name] = pkg
	return pkg


def _loadAddon(addon: Addon) -> None:
	"""Imports the add-on's global plugins and starts them. Raises on any failure."""
	import globalPluginHandler

	pluginsDir = os.path.join(addon.path, "globalPlugins")
	if not os.path.isdir(pluginsDir):
		_running[addon.name] = []
		return
	pkg = _ensurePackage("globalPlugins")
	if pluginsDir not in pkg.__path__:
		pkg.__path__.append(pluginsDir)
	libDir = os.path.join(addon.path, "lib")
	if os.path.isdir(libDir) and libDir not in sys.path:
		sys.path.append(libDir)
	instances = []
	try:
		for _finder, modName, _isPkg in pkgutil.iter_modules([pluginsDir]):
			full = "globalPlugins." + modName
			sys.modules.pop(full, None)
			mod = importlib.import_module(full)
			cls = getattr(mod, "GlobalPlugin", None)
			if cls is None or not issubclass(cls, globalPluginHandler.GlobalPlugin):
				continue
			plugin = cls()
			instances.append(plugin)
			globalPluginHandler.runningPlugins.add(plugin)
	except BaseException:
		for plugin in instances:
			_terminatePlugin(plugin)
		raise
	_running[addon.name] = instances


def _terminatePlugin(plugin) -> None:
	import globalPluginHandler

	globalPluginHandler.runningPlugins.discard(plugin)
	try:
		plugin.terminate()
	except Exception:
		log.error("Error terminating a global plugin", exc_info=True)


def initialize(addonsDir: str | None = None) -> str:
	"""Loads every enabled add-on. Returns a JSON list of the add-ons that failed and were turned
	off, each with its summary and a plain reason, so the Java side can tell the user."""
	global _addonsDir, _stateFile, _pendingRestart
	if addonsDir:
		_addonsDir = addonsDir
	if not _addonsDir:
		import NVDAState

		_addonsDir = os.path.join(NVDAState.WritePaths.configDir, "addons")
	_stateFile = os.path.join(_addonsDir, ".state.json")
	os.makedirs(_addonsDir, exist_ok=True)
	_loadState()
	_scanInstalled()
	_failures.clear()
	_failures.update({k: v for k, v in _state.get("failed", {}).items() if k in _installed})
	failedNow = []
	for addon in list(_installed.values()):
		if addon.isDisabled:
			continue
		try:
			_loadAddon(addon)
		except BaseException as e:  # an add-on must never stop the screen reader
			log.error("Add-on %s failed to load\n%s" % (addon.name, traceback.format_exc()))
			reason = _plainReason(e)
			_failures[addon.name] = reason
			if addon.name not in _state["disabled"]:
				_state["disabled"].append(addon.name)
			failedNow.append({"name": addon.name, "summary": addon.manifest.get("summary", addon.name), "reason": reason})
	if failedNow:
		_saveState()
	_pendingRestart = False
	return json.dumps(failedNow)


def scanOnly(addonsDir: str) -> None:
	"""Reads the installed add-ons for the Add-on Store without running any of them."""
	global _addonsDir, _stateFile
	_addonsDir = addonsDir
	_stateFile = os.path.join(_addonsDir, ".state.json")
	os.makedirs(_addonsDir, exist_ok=True)
	_loadState()
	_scanInstalled()


def terminate() -> None:
	for name in list(_running):
		for plugin in _running.pop(name):
			_terminatePlugin(plugin)


def restart() -> str:
	"""NVDA restarts itself to apply add-on changes; Nvidiator stops and reloads the add-ons."""
	terminate()
	for mod in [m for m in sys.modules if m.startswith("globalPlugins.")]:
		del sys.modules[mod]
	pkg = sys.modules.get("globalPlugins")
	if pkg is not None:
		pkg.__path__ = []
	return initialize()


# --- gestures ----------------------------------------------------------------------------


def executeGesture(identifier: str, displayName: str = "") -> bool:
	"""Runs the script a running add-on binds to this gesture. Returns True when one ran.
	A script that raises turns its add-on off, like a failed load."""
	import inputCore
	import scriptHandler

	gesture = inputCore.InputGesture(identifier, displayName)
	for name, plugins in list(_running.items()):
		for plugin in plugins:
			func = plugin.getScript(gesture)
			if not func:
				continue
			scriptHandler._noteScriptRun(func)
			try:
				func(gesture)
			except BaseException as e:
				log.error("Add-on %s script failed\n%s" % (name, traceback.format_exc()))
				_failAndDisable(name, _plainReason(e))
				raise AddonScriptError(name, _failures.get(name, "")) from None
			return True
	return False


class AddonScriptError(Exception):
	def __init__(self, name, reason):
		super().__init__("%s: %s" % (name, reason))
		self.addonName = name
		self.reason = reason


def _failAndDisable(name: str, reason: str) -> None:
	for plugin in _running.pop(name, []):
		_terminatePlugin(plugin)
	_failures[name] = reason
	if name not in _state["disabled"]:
		_state["disabled"].append(name)
	_saveState()


def gestureBindings() -> str:
	"""JSON list of the gestures running add-ons bind: identifier, description, add-on summary."""
	out = []
	for name, plugins in _running.items():
		summary = _installed[name].manifest.get("summary", name) if name in _installed else name
		for plugin in plugins:
			for ident, func in plugin._gestureMap.items():
				out.append({"gesture": ident, "description": (func.__doc__ or func.__name__[7:]).strip(), "addon": summary})
	return json.dumps(out)


# --- install, enable, disable, remove -----------------------------------------------------


def readBundle(bundlePath: str) -> dict:
	"""Reads the manifest of an add-on package without installing it."""
	try:
		with zipfile.ZipFile(bundlePath) as z:
			with z.open(MANIFEST_FILENAME) as f:
				manifest = parseManifest(f.read().decode("utf-8-sig"))
	except (OSError, KeyError, zipfile.BadZipFile, UnicodeDecodeError) as e:
		raise AddonError("Not an NVDA add-on package: %s" % e) from e
	if not manifest.get("name") or not re.match(r"^[\w.-]+$", manifest["name"]):
		raise AddonError("The add-on manifest has no valid name")
	return manifest


def bundleInfo(bundlePath: str) -> str:
	m = readBundle(bundlePath)
	return json.dumps({"name": m["name"], "summary": m.get("summary", m["name"]), "version": m.get("version", ""), "author": m.get("author", "")})


def installBundle(bundlePath: str) -> str:
	"""Installs (or updates) an add-on package. It runs after the next restart."""
	global _pendingRestart
	manifest = readBundle(bundlePath)
	name = manifest["name"]
	dest = os.path.join(_addonsDir, name)
	tmp = os.path.join(_addonsDir, ".installing-" + name)
	shutil.rmtree(tmp, ignore_errors=True)
	os.makedirs(tmp)
	try:
		with zipfile.ZipFile(bundlePath) as z:
			for info in z.infolist():
				target = os.path.realpath(os.path.join(tmp, info.filename))
				if not target.startswith(os.path.realpath(tmp) + os.sep) and target != os.path.realpath(tmp):
					raise AddonError("The package contains an unsafe path: %s" % info.filename)
			z.extractall(tmp)
	except BaseException:
		shutil.rmtree(tmp, ignore_errors=True)
		raise
	if name in _running:
		for plugin in _running.pop(name):
			_terminatePlugin(plugin)
	shutil.rmtree(dest, ignore_errors=True)
	os.replace(tmp, dest)
	if name in _state["disabled"]:
		_state["disabled"].remove(name)
	_failures.pop(name, None)
	_saveState()
	_scanInstalled()
	_pendingRestart = True
	return name


def setEnabled(name: str, enabled: bool) -> None:
	global _pendingRestart
	if enabled:
		if name in _state["disabled"]:
			_state["disabled"].remove(name)
		_failures.pop(name, None)
	elif name not in _state["disabled"]:
		_state["disabled"].append(name)
	_saveState()
	_pendingRestart = True


def remove(name: str) -> None:
	global _pendingRestart
	for plugin in _running.pop(name, []):
		_terminatePlugin(plugin)
	shutil.rmtree(os.path.join(_addonsDir, name), ignore_errors=True)
	if name in _state["disabled"]:
		_state["disabled"].remove(name)
	_failures.pop(name, None)
	_saveState()
	_scanInstalled()
	_pendingRestart = True


def isRestartPending() -> bool:
	return _pendingRestart


def listAddons() -> str:
	return json.dumps([a.asDict() for a in _installed.values()])


# --- NVDA's API for add-on code ------------------------------------------------------------


def getRunningAddons():
	return [a for a in _installed.values() if a.isRunning]


def getAvailableAddons(refresh=False, filterFunc=None):
	if refresh:
		_scanInstalled()
	return [a for a in _installed.values() if filterFunc is None or filterFunc(a)]


def getCodeAddon(obj=None, frameDist=1):
	"""The add-on whose code called this, found from the caller's file path, as in NVDA."""
	if obj is None:
		obj = sys._getframe(frameDist)
	fileName = inspect.getfile(obj)
	dir = os.path.realpath(os.path.abspath(fileName))
	for addon in _installed.values():
		base = os.path.realpath(addon.path) + os.sep
		if dir.startswith(base):
			return addon
	raise AddonError("Code does not belong to an addon package.")


def initTranslation():
	"""Installs the calling add-on's translations as _ (and ngettext, pgettext) in its module."""
	addon = getCodeAddon(frameDist=2)
	translations = addon.getTranslationsInstance()
	callerFrame = inspect.currentframe().f_back
	callerFrame.f_globals["_"] = translations.gettext
	callerFrame.f_globals["ngettext"] = translations.ngettext
	callerFrame.f_globals["pgettext"] = translations.pgettext
	callerFrame.f_globals["npgettext"] = translations.npgettext
	del callerFrame


def runGesture(identifier: str, displayName: str = "") -> str:
	"""executeGesture for the Java side: never raises. Returns JSON with handled, and the summary
	and plain reason of an add-on that failed and was turned off."""
	try:
		return json.dumps({"handled": executeGesture(identifier, displayName)})
	except AddonScriptError as e:
		addon = _installed.get(e.addonName)
		summary = addon.manifest.get("summary", e.addonName) if addon else e.addonName
		return json.dumps({"handled": True, "failed": summary, "reason": e.reason})
	except Exception:
		log.error("Gesture %s failed" % identifier, exc_info=True)
		return json.dumps({"handled": False})


# --- v0.2: events and speech hooks for add-ons ----------------------------------------------------


def wantedInfo() -> str:
	"""Which events the running add-ons handle, and whether any speech hook is registered, so the
	Java side only calls into Python when an add-on is listening."""
	try:
		import eventHandler
		import speech.extensions

		return json.dumps({"events": eventHandler.wantedEvents(), "speech": speech.extensions.hasHandlers()})
	except Exception:
		log.error("wantedInfo failed", exc_info=True)
		return json.dumps({"events": [], "speech": False})


def dispatchEvent(eventName: str, nodeJson: str, extraJson: str = "") -> str:
	"""Sends one screen reader event to the add-ons' event_<name> handlers. Never raises. Returns a
	JSON list of the add-ons whose handler failed and were turned off (summary, reason)."""
	try:
		import eventHandler
		from NVDAObjects import NVDAObject

		node = json.loads(nodeJson) if nodeJson else {}
		obj = NVDAObject.fromNodeInfo(node) if node else NVDAObject()
		extra = json.loads(extraJson) if extraJson else {}
		failures = eventHandler.executeEvent(eventName, obj, **extra)
	except Exception:
		log.error("Event %s failed" % eventName, exc_info=True)
		return "[]"
	out = []
	for name, exc in failures:
		if name not in _running:
			continue
		reason = _plainReason(exc)
		_failAndDisable(name, reason)
		addon = _installed.get(name)
		out.append({"name": name, "summary": addon.manifest.get("summary", name) if addon else name, "reason": reason})
	return json.dumps(out)


def speakThrough(text: str, interrupt: bool = False) -> None:
	"""Speaks screen reader text through NVDA's speech extension points, so add-on speech filters and
	pre-speech handlers see it. If anything goes wrong, the plain text is spoken."""
	import speech
	from nvidiator import androidApi

	try:
		seq = speech.processSequence([text])
	except Exception:
		log.error("speech hooks failed", exc_info=True)
		seq = [text]
	try:
		speech.playSequence(seq, interrupt=interrupt)
	except Exception:
		log.error("speech playback failed", exc_info=True)
		androidApi._speak(text, interrupt)

