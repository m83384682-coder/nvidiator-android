# Nvidiator Android: NVDA's nvwave.playWaveFile for add-ons. NVDA's own sounds play by name;
# a .wav file shipped inside an add-on plays from its path. decide_playWaveFile works as in NVDA.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
import os

from extensionPoints import Decider
from nvidiator import androidApi as _a

#: Lets add-ons stop a wave file from playing (return False), as in NVDA.
decide_playWaveFile = Decider()


def playWaveFile(fileName, asynchronous=True, isSpeechWaveFileCommand=False, **kwargs):
	if not decide_playWaveFile.decide(fileName=fileName, asynchronous=asynchronous, isSpeechWaveFileCommand=isSpeechWaveFileCommand):
		return None
	if os.path.isabs(str(fileName)) and os.path.isfile(str(fileName)):
		_a.playFile(str(fileName))
	else:
		_a.nvwave.playWaveFile(fileName)


class WavePlayer:
	"""NVDA's raw audio player is not available on Android; this stand-in accepts calls and plays nothing."""

	def __init__(self, *args, **kwargs):
		pass

	def feed(self, *args, **kwargs):
		pass

	def idle(self):
		pass

	def stop(self):
		pass

	def close(self):
		pass
