# Nvidiator Android: NVDA's ui module for add-ons, speaking through the Android service.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from nvidiator import androidApi as _a  # noqa: F401


def _say(text):
	import speech

	speech.speakMessage(str(text))


def message(text, *args, **kwargs):
	_say(text)


def reviewMessage(text, *args, **kwargs):
	_say(text)


def browseableMessage(message, title=None, *args, **kwargs):
	"""NVDA opens a browseable window; Nvidiator speaks the message instead."""
	_say(message)
