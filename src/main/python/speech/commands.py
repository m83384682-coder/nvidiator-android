# Nvidiator Android: the speech commands add-ons put in speech sequences (speech/commands.py).
# Wave and beep commands play; the voice commands are accepted and ignored by Android speech.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.


class SpeechCommand:
	pass


class SynthCommand(SpeechCommand):
	pass


class BeepCommand(SpeechCommand):
	def __init__(self, hz, length, left=50, right=50):
		self.hz, self.length, self.left, self.right = hz, length, left, right

	def __repr__(self):
		return "BeepCommand(%r, %r, left=%r, right=%r)" % (self.hz, self.length, self.left, self.right)


class WaveFileCommand(SpeechCommand):
	def __init__(self, fileName):
		self.fileName = fileName

	def __repr__(self):
		return "WaveFileCommand(%r)" % self.fileName


class _Ignored(SynthCommand):
	def __init__(self, *args, **kwargs):
		self.args, self.kwargs = args, kwargs


class IndexCommand(_Ignored):
	pass


class CharacterModeCommand(_Ignored):
	pass


class LangChangeCommand(_Ignored):
	pass


class BreakCommand(_Ignored):
	pass


class EndUtteranceCommand(_Ignored):
	pass


class PitchCommand(_Ignored):
	pass


class VolumeCommand(_Ignored):
	pass


class RateCommand(_Ignored):
	pass


class PhonemeCommand(_Ignored):
	pass


class CallbackCommand(SpeechCommand):
	def __init__(self, callback, name=None):
		self._callback = callback

	def run(self):
		self._callback()
