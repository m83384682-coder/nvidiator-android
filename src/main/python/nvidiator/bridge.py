# Nvidiator Android: the functions the Java AccessibilityService calls through Chaquopy.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
#
# Java sends plain JSON strings and gets plain strings back, so the bridge stays simple and
# can be tested on a desktop Python with the same inputs.

import json
import os
import sys

_ready = False


def init(appDir: str, lang: str) -> str:
	"""Point NVDA's modules at the extracted locale data and load the translation. Returns the
	NVDA locale actually used, e.g. 'ur' for an Android language of 'ur_PK'."""
	global _ready
	os.environ["NVIDIATOR_APPDIR"] = appDir
	import globalVars

	globalVars.appDir = appDir
	import languageHandler

	used = languageHandler.setLanguage(lang)
	# Import NVDA's modules only after _ is installed, as they translate at import time.
	import characterProcessing
	import controlTypes  # noqa: F401

	characterProcessing.initialize()
	_ready = True
	return used


def setLanguage(lang: str) -> str:
	"""Switch NVDA's language. NVDA builds its role and state labels when controlTypes is
	imported (NVDA itself needs a restart to change language), so those modules are reloaded."""
	import languageHandler

	used = languageHandler.setLanguage(lang)
	for name in list(sys.modules):
		if (
			name == "characterProcessing"
			or name == "controlTypes"
			or name.startswith("controlTypes.")
			or name == "utils.displayString"
			or name in ("nvidiator.androidRoles", "nvidiator.speechPort")
		):
			del sys.modules[name]
	import nvidiator

	for sub in ("androidRoles", "speechPort"):
		if hasattr(nvidiator, sub):
			delattr(nvidiator, sub)
	import characterProcessing
	import controlTypes  # noqa: F401

	characterProcessing.initialize()
	return used


def setConfig(section: str, key: str, value) -> None:
	import config

	config.conf.setdefault(section, {})[key] = value


def pythonVersion() -> str:
	return sys.version.split()[0]


# --- Object speech -----------------------------------------------------------------------


def _reason(name: str):
	from controlTypes import OutputReason

	return {
		"focus": OutputReason.FOCUS,
		"query": OutputReason.QUERY,
		"change": OutputReason.CHANGE,
		"quicknav": OutputReason.QUICKNAV,
		"sayall": OutputReason.SAYALL,
	}.get(name, OutputReason.FOCUS)


def describe(nodeJson: str, reason: str = "focus") -> str:
	"""Speak one node the way NVDA speaks an object: name, role, value, states, description,
	placeholder, position."""
	from nvidiator import androidRoles, speechPort

	n = json.loads(nodeJson)
	props = androidRoles.propertiesForNode(n)
	parts = speechPort.getPropertiesSpeech(_reason(reason), **props)
	return " ".join(p for p in parts if p)


def describeFocus(nodeJson: str, enteredJson: str, exitedJson: str, reason: str = "focus") -> str:
	"""Full NVDA-style announcement for a move: 'out of list', then the containers just entered
	('tool bar', 'list with 5 items'), then the object itself."""
	from nvidiator import androidRoles, speechPort

	out = []
	exited = json.loads(exitedJson) if exitedJson else []
	if exited:
		out += speechPort.getControlFieldExitSpeech([androidRoles.containerRoleText(c) for c in exited])
	for c in json.loads(enteredJson) if enteredJson else []:
		name = c.get("name") or ""
		parts = [name] if name else []
		count = (c.get("collection") or {}).get("items", 0)
		parts += speechPort.getControlFieldEnterSpeech(androidRoles.containerRoleText(c), count)
		out.append(" ".join(parts))
	out.append(describe(nodeJson, reason))
	return ", ".join(p for p in out if p)


def roleInfo(nodeJson: str) -> str:
	"""The NVDA role name in English for a node, used by Java to pick sounds and quick navigation."""
	from nvidiator import androidRoles

	return androidRoles.roleForNode(json.loads(nodeJson)).name


def containerRoleText(nodeJson: str) -> str:
	from nvidiator import androidRoles

	return androidRoles.containerRoleText(json.loads(nodeJson))


# --- Symbols and characters -------------------------------------------------------------


def processSymbols(text: str, level: int) -> str:
	"""NVDA's punctuation processing: replaces symbols with words for the given symbol level
	(0 none, 100 some, 200 most, 300 all), using NVDA's symbols.dic for the language."""
	import characterProcessing
	import languageHandler

	try:
		return characterProcessing.processSpeechSymbols(
			languageHandler.getLanguage(),
			text,
			characterProcessing.SymbolLevel(level),
		)
	except Exception:
		return text


def speakCharacter(ch: str) -> str:
	"""How NVDA speaks a single character, e.g. 'dot', 'cap A' or 'space'."""
	import characterProcessing
	import languageHandler

	lang = languageHandler.getLanguage()
	if not ch:
		return ""
	if ch.isupper() and len(ch) == 1:
		# Translators: cap will be spoken before the given letter when it is capitalized.
		return _("cap %s") % ch
	if ch == " ":
		return characterProcessing.processSpeechSymbol(lang, " ") or "space"
	sym = characterProcessing.processSpeechSymbol(lang, ch)
	return sym or ch


def characterDescription(ch: str) -> str:
	"""NVDA's phonetic description of a character, e.g. 'Alfa' for a (the second press of
	spell character)."""
	import characterProcessing
	import languageHandler

	desc = characterProcessing.getCharacterDescription(languageHandler.getLanguage(), ch.lower())
	return " ".join(desc) if desc else speakCharacter(ch)


# --- NVDA's own messages, translated -----------------------------------------------------


def messages() -> str:
	"""The translated NVDA messages the Java side speaks. Each key maps to NVDA's own source
	string, so the translation comes from NVDA's catalogue for the current language."""
	import characterProcessing

	m = {
		"noNext": _("No next"),
		"noPrevious": _("No previous"),
		"noFocus": _("No focus"),
		"noContaining": _("No containing object"),
		"speechModeFmt": _("Speech mode {mode}"),
		"modeOff": pgettext("speechModes", "off"),
		"modeBeeps": pgettext("speechModes", "beeps"),
		"modeTalk": pgettext("speechModes", "talk"),
		"symbolLevelFmt": _("Symbol level %s"),
		"typedCharsFmt": _("Speak typed characters {mode}"),
		"clipboardEmpty": _("There is no text on the clipboard"),
		"clipboardLargeFmt": ngettext("The clipboard contains a large amount of text. It is %s character long", "The clipboard contains a large amount of text. It is %s character long", 2),
		"sleepOn": _("Sleep mode on"),
		"sleepOff": _("Sleep mode off"),
		"typedWordsFmt": _("Speak typed words {mode}"),
		"echoOff": _("Off"),
		"echoAlways": _("Always"),
		"inputHelpOn": _("input help on. Press {gestureKeys} again to turn it off."),
		"inputHelpOff": _("input help off"),
		"textMode": _("text mode"),
		"objectMode": _("object mode"),
		"plugged": _("Plugged in"),
		"unplugged": _("Unplugged"),
		"percentFmt": ngettext("%d percent", "%d percent", 2),
		"noBattery": _("No system battery"),
		"blank": _("blank"),
		"exitNvda": _("Exit NVDA"),
		"top": _("top"),
		"bottom": _("bottom"),
		"selected": _("selected %s"),
		"unselected": _("unselected %s"),
		"noSelection": _("No selection"),
		"screenCurtainOn": _("Screen curtain enabled"),
		"screenCurtainOff": _("Screen curtain disabled"),
		"focusMode": _("focus mode"),
		"browseMode": _("browse mode"),
		"lineFmt": _("line %s"),
		"outOfFmt": _("out of %s"),
	}
	for lvl, label in characterProcessing.SPEECH_SYMBOL_LEVEL_LABELS.items():
		m["symbolLevel%d" % int(lvl)] = label
	return json.dumps(m, ensure_ascii=False)


def roleName(roleName: str) -> str:
	"""NVDA's translated name for a role, e.g. roleName('HEADING') -> 'heading'."""
	from controlTypes import Role

	try:
		return Role[roleName].displayString
	except KeyError:
		return roleName.lower()


def stateSpeech(nodeJson: str) -> str:
	"""What NVDA says when an object's state or value changes (event_stateChange /
	event_valueChange): only the states and value, not the name and role."""
	from nvidiator import androidRoles, speechPort

	n = json.loads(nodeJson)
	props = androidRoles.propertiesForNode(n)
	from controlTypes import Role, State

	states = props["states"]
	role = props["role"]
	negative = set()
	# For a change NVDA only speaks negative states it is told changed, so say what turned off.
	if n.get("checkable") and not n.get("checked"):
		negative.add(State.ON if role == Role.SWITCH else State.PRESSED if role == Role.TOGGLEBUTTON else State.CHECKED)
	if n.get("expand") == "collapsed":
		states = states | {State.COLLAPSED}
	# Java only asks for this on the object the user is on, which NVDA treats as the focus.
	states = states | {State.FOCUSED}
	keep = {"_role": role, "states": states, "negativeStates": negative}
	if props.get("value"):
		keep["value"] = props["value"]
	parts = speechPort.getPropertiesSpeech(_reason("change"), **keep)
	return " ".join(p for p in parts if p)


def listLanguages() -> str:
	"""NVDA's languages that have a translation, as JSON [[code, name], ...]."""
	import languageHandler

	out = []
	for code in languageHandler.listAvailableLanguages():
		out.append([code, code])
	return json.dumps(out)


# NVDA's own wording for the menus and screens, from gui/__init__.py, gui/settingsDialogs.py,
# synthDriverHandler.py, speechViewer.py, pythonConsole.py and config/configFlags.py.
# The keys are ours; each value is NVDA's source string, so _() finds NVDA's translation.
_UI_STRINGS = {
	# The NVDA menu (gui/__init__.py, SysTrayIcon)
	"menuPreferences": "&Preferences",
	"menuSettings": "&Settings...",
	"menuSettingsHelp": "NVDA settings",
	"menuTools": "&Tools",
	"menuSpeechViewer": "&Speech viewer",
	"menuPythonConsole": "&Python console",
	"menuHelp": "&Help",
	"menuUserGuide": "&User Guide",
	"menuQuickRef": "Commands &Quick Reference",
	"menuLicense": "L&icense",
	"menuAbout": "&About...",
	"aboutTitle": "About NVDA",
	"menuSaveConfig": "&Save configuration",
	"menuRevertConfig": "&Revert to saved configuration",
	"menuResetConfig": "Reset configuration to &factory defaults",
	"menuExit": "E&xit",
	"exitTitle": "Exit NVDA",
	"exitExit": "Exit",
	"exitRestart": "Restart",
	"exitQuestion": "What would you like to &do?",
	# Settings (gui/settingsDialogs.py)
	"settingsTitle": "NVDA Settings",
	"panelGeneral": "General",
	"panelSpeech": "Speech",
	"panelVoice": "Voice",
	"panelKeyboard": "Keyboard",
	"panelObjectPresentation": "Object Presentation",
	"panelBrowseMode": "Browse Mode",
	"panelTouch": "Touch Interaction",
	"panelAudio": "Audio",
	"language": "NVDA &Language (requires restart):",
	"playStartExitSounds": "&Play sounds when starting or exiting NVDA",
	"synthesizer": "Synthesizer",
	"change": "C&hange...",
	"selectSynth": "Select Synthesizer",
	"rate": "&Rate",
	"pitch": "&Pitch",
	"volume": "V&olume",
	"symbolLevel": "Punctuation/symbol &level:",
	"selectModifiers": "&Select NVDA Modifier Keys",
	"typedChars": "Speak typed &characters:",
	"typedWords": "Speak typed &words:",
	"echoOff": "Off",
	"echoEditControls": "Only in edit controls",
	"echoAlways": "Always",
	"reportNotifications": "Report &notifications",
	"reportPosition": "Report object &position information",
	"reportDescriptions": "Report object &descriptions",
	"progressOutput": "Progress &bar output:",
	"progressOff": "off",
	"progressSpeak": "Speak",
	"progressBeep": "Beep",
	"progressBoth": "Speak and beep",
	"reportDynamic": "Report dynamic &content changes",
	"autoFocusMode": "Automatic focus mode for focus changes",
	"modeAudio": "Audio indication of focus and browse modes",
	"touchSupport": "Enable touch interaction support",
	"touchTyping": "&Touch typing mode",
	"ok": "OK",
	"cancel": "Cancel",
	"close": "Close",
	# Speech viewer and Python console
	"speechViewerTitle": "NVDA Speech Viewer",
	"pythonConsoleTitle": "NVDA Python Console",
	# v0.2: the welcome dialog (gui/startupDialogs.py, WelcomeDialog)
	"welcomeTitle": "Welcome to NVDA",
	"welcomeHeader": "Welcome to NVDA!",
	"welcomeDetail": (
		"Most commands for controlling NVDA require you to hold down"
		" the NVDA key while pressing other keys.\n"
		"By default, the Insert and numpad Insert keys may both be used as the NVDA key.\n"
		"You can also configure NVDA to use the CapsLock as the NVDA key.\n"
		"Press NVDA+n at any time to activate the NVDA menu.\n"
		"From this menu, you can configure NVDA, get help, and access other NVDA functions."
	),
	"options": "Options",
	"capsAsNvda": "&Use CapsLock as an NVDA modifier key",
	"showWelcome": "&Show this dialog when NVDA starts",
	# v0.2: Input gestures (gui/__init__.py and gui/inputGestures.py)
	"menuInputGestures": "I&nput gestures...",
	"inputGesturesTitle": "Input Gestures",
	"gestAdd": "&Add",
	"gestRemove": "&Remove",
	"gestReset": "Reset to factory &defaults",
	"gestResetQuestion": (
		"Are you sure you want to reset all gestures to their factory defaults?\n\n"
		"\t\t\tAll of your user defined gestures, whether previously set or defined during this session, will be lost.\n"
		"\t\t\tThis cannot be undone."
	),
	"gestResetTitle": "Reset gestures",
	# v0.2: add-ons (gui/__init__.py, gui/addonGui.py, gui/addonStoreGui/controls/messageDialogs.py)
	"menuAddonStore": "&Add-on store...",
	"confirmInstall": (
		"Are you sure you want to install this add-on?\n"
		"Only install add-ons from trusted sources.\n"
		"Addon: {summary} {version}"
	),
	"restartQuestion": (
		"Changes were made to add-ons. "
		"You must restart NVDA for these changes to take effect. "
		"Would you like to restart now?"
	),
	"restartTitle": "Restart NVDA",
	"failedOpen": "Failed to open add-on package file at %s - missing file or invalid file format",
	"failedInstall": "Failed to install add-on from %s",
	"error": "Error",
	"yes": "&Yes",
	"no": "&No",
	# v0.2 settings (gui/settingsDialogs.py, synthDriverHandler.py, speech/speech.py)
	"rateBoost": "Rate boos&t",
	"capPitch": "Capital pitch change percentage",
	"sayCap": "Say &cap before capitals",
	"beepCaps": "&Beep for capitals",
	"interruptTyped": "Speech &interrupt for typed characters",
	"speakCommandKeys": "Speak c&ommand keys",
	"soundVolumeFollows": "Volume of NVDA sounds follows voice volume",
	"soundVolume": "Volume of NVDA sounds",
	"suggestionSounds": "Play a sound when &auto-suggestions appear",
	"capPrefix": "cap %s",
	"panelAudio2": "Audio",
}

# Strings NVDA translates with a context (pgettext). Key: (context, NVDA source string).
_UI_PSTRINGS = {
	"gestFilter": ("inputGestures", "&Filter by:"),
	"addonStoreTitle": ("addonStore", "Add-on Store"),
	"addonWarningTitle": ("addonStore", "Add-on Store Warning"),
	"addonWarning": (
		"addonStore",
		"Add-ons are created by the NVDA community and are not vetted by NV Access. "
		"NV Access cannot be held responsible for add-on behavior. "
		"The functionality of add-ons is unrestricted and can include "
		"accessing your personal data or even the entire system. ",
	),
	"installExternal": ("addonStore", "Install from e&xternal source"),
	"chooseAddonFile": ("addonStore", "Choose Add-on Package File"),
	"installedAddons": ("addonStore", "Installed add-ons"),
	"addonEnable": ("addonStore", "&Enable"),
	"addonDisable": ("addonStore", "&Disable"),
	"addonRemove": ("addonStore", "&Remove"),
	"removeTitle": ("addonStore", "Remove add-on"),
	"removeQuestion": (
		"addonStore",
		"Are you sure you wish to remove the {addon} add-on from NVDA? This cannot be undone.",
	),
	"installTitle": ("addonStore", "Add-on Installation"),
	"statusEnabled": ("addonStore", "Enabled"),
	"statusDisabled": ("addonStore", "Disabled"),
	"statusInstalledPending": ("addonStore", "Installed, pending restart"),
	"statusDisablePending": ("addonStore", "Disabled, pending restart"),
	"statusEnablePending": ("addonStore", "Enabled, pending restart"),
	"statusInstallFailed": ("addonStore", "Install failed"),
	"statusIncompatible": ("addonStore", "Incompatible"),
}


def _clean(s: str) -> str:
	"""Removes the & accelerator markers (Android has none) and the source's stray indentation."""
	s = s.replace("&&", "\0").replace("&", "").replace("\0", "&")
	return "\n".join(line.strip("\t") for line in s.split("\n"))


def uiStrings() -> str:
	"""NVDA's wording for the screens, translated, with the & accelerator markers removed
	(Android has no menu accelerators). Returned as JSON."""
	import controlTypes

	out = {}
	for key, msgid in _UI_STRINGS.items():
		out[key] = _clean(_(msgid))
	for key, (ctx, msgid) in _UI_PSTRINGS.items():
		out[key] = _clean(pgettext(ctx, msgid))
	out["modeTalk"] = pgettext("speechModes", "talk")
	out["modeBeeps"] = pgettext("speechModes", "beeps")
	out["modeOff"] = pgettext("speechModes", "off")
	import characterProcessing

	for lvl, label in characterProcessing.SPEECH_SYMBOL_LEVEL_LABELS.items():
		if int(lvl) in (0, 100, 200, 300):
			out["symbolLevel%d" % int(lvl)] = label
	out["speechMode"] = _("Speech mode {mode}").split("{mode}")[0].strip()
	return json.dumps(out)
