# Nvidiator Android: stand-in for NVDA's inputCore, enough for add-ons and baseObject.
# Gesture identifiers keep NVDA's form, e.g. "kb:NVDA+shift+a" or "ts:2finger_double_tap".
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.

import re

import baseObject

# NVDA's script categories (inputCore.SCRCAT_*), as NVDA's own source strings.
SCRCAT_MISC = _("Miscellaneous")
SCRCAT_KBEMU = _("Emulated system keyboard keys")
SCRCAT_BROWSEMODE = _("Browse mode")


class NoInputGestureAction(LookupError):
	"""Raised by a gesture that does nothing, like NVDA's."""


def normalizeGestureIdentifier(identifier: str) -> str:
	"""NVDA's normalization: lower case, layout dropped, and the parts in a fixed order, so
	"kb:shift+NVDA+A" and "kb:nvda+shift+a" are the same gesture."""
	identifier = re.sub(r"\((desktop|laptop)\)", "", identifier)
	prefix, _sep, main = identifier.partition(":")
	parts = [p.strip().lower() for p in main.split("+") if p.strip()]
	if len(parts) > 1:
		parts = sorted(parts[:-1]) + [parts[-1]]
	return "%s:%s" % (prefix.strip().lower(), "+".join(parts))


class InputGesture(baseObject.AutoPropertyObject):
	"""A gesture that came from Android: its NVDA identifier and a spoken name."""

	cachePropertiesByDefault = True

	def __init__(self, identifier: str, displayName: str = ""):
		super().__init__()
		self._identifier = identifier
		self._displayName = displayName or identifier.partition(":")[2]

	def _get_identifiers(self):
		return (self._identifier,)

	def _get_normalizedIdentifiers(self):
		return [normalizeGestureIdentifier(self._identifier)]

	def _get_displayName(self):
		return self._displayName

	def _get_isModifier(self):
		return False

	def send(self):
		"""NVDA passes the key on to Windows. On Android the key was already handled."""

	def reportExtra(self):
		pass


#: NVDA's input manager. Nvidiator routes gestures itself, so there is none.
manager = None
