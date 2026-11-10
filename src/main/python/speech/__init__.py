# Nvidiator Android: the parts of NVDA's speech package add-ons use most, speaking through the
# Android service. speak() runs NVDA's speech extension points (pre_speech, filter_speechSequence)
# and plays the wave and beep commands in a sequence, so speech and sound add-ons can work.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from nvidiator import androidApi as _a

from . import commands, extensions  # noqa: F401
from .commands import BeepCommand, WaveFileCommand  # noqa: F401


class SpeechMode:
	off = 0
	beeps = 1
	talk = 2
	onDemand = 3


class Spri:
	NORMAL = 0
	NEXT = 1
	NOW = 2


_MODES = {"off": SpeechMode.off, "beeps": SpeechMode.beeps, "talk": SpeechMode.talk}


def getState():
	try:
		return _MODES.get(str(_a.speech.getState()), SpeechMode.talk)
	except Exception:
		return SpeechMode.talk


def processSequence(seq):
	"""Runs NVDA's pre_speech and filter_speechSequence on a sequence. Returns the new sequence."""
	seq = list(seq)
	extensions.pre_speech.notify(speechSequence=seq, symbolLevel=None, priority=Spri.NORMAL)
	return list(extensions.filter_speechSequence.apply(seq))


def playSequence(seq, interrupt=False):
	"""Speaks the text in a sequence and plays its wave and beep commands, in order."""
	text = []

	def flush():
		nonlocal interrupt
		if text:
			_a._speak(" ".join(text), interrupt)
			text.clear()
			interrupt = False

	for item in seq:
		if isinstance(item, str):
			if item.strip():
				text.append(item)
		elif isinstance(item, WaveFileCommand):
			flush()
			import nvwave

			nvwave.playWaveFile(item.fileName)
		elif isinstance(item, BeepCommand):
			flush()
			import tones

			tones.beep(item.hz, item.length, item.left, item.right)
	flush()


def speak(speechSequence, symbolLevel=None, priority=None, *args, **kwargs):
	if isinstance(speechSequence, str):
		speechSequence = [speechSequence]
	playSequence(processSequence(speechSequence), interrupt=priority == Spri.NOW)


def speakMessage(text, *args, **kwargs):
	speak([text])


def speakText(text, *args, **kwargs):
	speak([text])


def speakSpelling(text, *args, **kwargs):
	_a.speech.speakSpelling(text)


def cancelSpeech():
	_a.speech.cancelSpeech()
	extensions.speechCanceled.notify()


def pauseSpeech(switch):
	extensions.post_speechPaused.notify(switch=switch)
