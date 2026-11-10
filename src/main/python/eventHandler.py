# Nvidiator Android: NVDA's eventHandler for add-ons. Events from the screen reader go to every
# running global plugin's event_<name>(obj, nextHandler) in turn, as in NVDA. On Android, moving to
# an item with a swipe or by touch counts as a focus change (gainFocus), like TalkBack's focus.
# An add-on whose event handler raises is turned off; the screen reader carries on.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
import traceback

from logHandler import log

lastQueuedFocusObject = None

#: Events Nvidiator sends, with NVDA's names.
SUPPORTED = ("gainFocus", "loseFocus", "becomeNavigatorObject", "foreground", "stateChange",
	"valueChange", "nameChange")


class _EventFailed(Exception):
	def __init__(self, addonName, exc):
		super().__init__(addonName)
		self.addonName = addonName
		self.exc = exc


def _handlers(eventName):
	"""(addonName, bound method) for each running plugin that handles the event, in load order."""
	import addonHandler

	out = []
	for name, plugins in list(addonHandler._running.items()):
		for plugin in plugins:
			func = getattr(plugin, "event_" + eventName, None)
			if callable(func):
				out.append((name, func))
	return out


def wantedEvents():
	return sorted(e for e in SUPPORTED if _handlers(e))


def executeEvent(eventName, obj, **kwargs):
	"""Runs the event through the plugins' handlers. Returns a list of (addonName, exception) for
	handlers that failed; each failure stops only that add-on's handler."""
	global lastQueuedFocusObject
	if eventName == "gainFocus":
		lastQueuedFocusObject = obj
	handlers = _handlers(eventName)
	failures = []

	def run(i):
		if i >= len(handlers):
			return  # NVDA's own handling: the screen reader speaks the item itself
		name, func = handlers[i]
		called = []

		def nextHandler():
			if called:
				return
			called.append(True)
			run(i + 1)

		try:
			func(obj, nextHandler, **kwargs) if kwargs else func(obj, nextHandler)
		except BaseException as e:  # an add-on must never stop the screen reader
			log.error("Add-on %s event_%s failed\n%s" % (name, eventName, traceback.format_exc()))
			failures.append((name, e))
			if not called:
				called.append(True)
				run(i + 1)

	run(0)
	return failures


def queueEvent(eventName, obj, **kwargs):
	executeEvent(eventName, obj, **kwargs)


def isPendingEvents(eventName=None, obj=None):
	return False
