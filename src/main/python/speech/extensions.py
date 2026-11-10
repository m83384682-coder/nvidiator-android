# Nvidiator Android: NVDA's speech extension points (speech/extensions.py), with NVDA's names.
# Nvidiator runs them for everything the screen reader says when an add-on registers a handler.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from extensionPoints import Action, Filter

#: Notifies when code attempts to speak text. Handlers get speechSequence, symbolLevel and priority.
pre_speech = Action()
#: Notifies when speech is canceled.
speechCanceled = Action()
#: Notifies when speech is canceled, before it happens.
pre_speechCanceled = Action()
#: Notifies when speech is paused (switch=True) or resumed.
post_speechPaused = Action()
#: Notifies when a speech sequence is queued.
pre_speechQueued = Action()
#: Lets add-ons change a speech sequence (a list of strings and speech commands) before it is spoken.
filter_speechSequence = Filter()


def hasHandlers() -> bool:
	return any(len(list(ep.handlers)) for ep in (pre_speech, filter_speechSequence, pre_speechQueued))
