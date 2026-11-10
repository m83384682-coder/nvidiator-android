# Nvidiator Android: stand-in for NVDA's scriptHandler. The script decorator matches NVDA's, so
# add-ons written for NVDA declare their scripts and gestures the same way.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.

import time

_lastScript = None
_lastScriptTime = 0.0
_lastScriptCount = 0


def script(
	description: str = "",
	category: str | None = None,
	gesture: str | None = None,
	gestures: list[str] | None = None,
	canPropagate: bool = False,
	bypassInputHelp: bool = False,
	allowInSleepMode: bool = False,
	resumeSayAllMode: int | None = None,
	speakOnDemand: bool = False,
):
	"""NVDA's scriptHandler.script decorator."""
	if gestures is None:
		gestures = []

	def script_decorator(decoratedScript):
		if not decoratedScript.__name__.startswith("script_"):
			return decoratedScript
		decoratedScript.__doc__ = description
		if category is not None:
			decoratedScript.category = category
		if gesture is not None:
			gestures.append(gesture)
		if gestures:
			decoratedScript.gestures = gestures
		decoratedScript.canPropagate = canPropagate
		decoratedScript.bypassInputHelp = bypassInputHelp
		decoratedScript.allowInSleepMode = allowInSleepMode
		decoratedScript.resumeSayAllMode = resumeSayAllMode
		decoratedScript.speakOnDemand = speakOnDemand
		return decoratedScript

	return script_decorator


def _noteScriptRun(func) -> None:
	"""Counts repeats of the same script within NVDA's half second, for getLastScriptRepeatCount."""
	global _lastScript, _lastScriptTime, _lastScriptCount
	now = time.monotonic()
	key = getattr(func, "__func__", func)
	if key is _lastScript and now - _lastScriptTime < 0.5:
		_lastScriptCount += 1
	else:
		_lastScriptCount = 0
	_lastScript = key
	_lastScriptTime = now


def getLastScriptRepeatCount() -> int:
	return _lastScriptCount


def isScriptWaiting() -> bool:
	return False


def getCurrentScript():
	return _lastScript
