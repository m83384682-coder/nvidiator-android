# Nvidiator Android: a small stand-in for NVDA's config package.
# Part of Nvidiator Android, an unofficial experimental port of NVDA (NonVisual Desktop Access)
# to Android. Not affiliated with NV Access.
# This file is covered by the GNU General Public License, version 2.
# NVDA's real config package uses configobj and a profile system that is tied to Windows paths.
# Android keeps its settings in SharedPreferences, so the Java side pushes the values NVDA's
# modules read into this nested dict, using NVDA's own section and key names.


class _Action:
	"""Minimal stand-in for extensionPoints.Action."""

	def __init__(self):
		self._handlers = []

	def register(self, handler):
		if handler not in self._handlers:
			self._handlers.append(handler)

	def unregister(self, handler):
		if handler in self._handlers:
			self._handlers.remove(handler)

	def notify(self, **kwargs):
		for h in list(self._handlers):
			h(**kwargs)


post_configProfileSwitch = _Action()

# Defaults copied from NVDA's config/configSpec.py for the keys the ported modules read.
conf = {
	"general": {"language": "Windows"},
	"speech": {
		"symbolLevel": 100,
		"symbolDictionaries": [],
		"includeCLDR": True,
	},
	"presentation": {
		"reportMultiSelect": False,
		"reportObjectDescriptions": True,
		"reportObjectPositionInformation": True,
		"reportTooltips": False,
		"reportHelpBalloons": True,
	},
	"documentFormatting": {
		"reportClickable": True,
		"reportLinkType": True,
		"reportLists": True,
	},
	"keyboard": {
		"speakTypedCharacters": True,
		"speakTypedWords": False,
	},
}
