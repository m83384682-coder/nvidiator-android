# Nvidiator Android: NVDA's tones module for add-ons, with NVDA's decide_beep extension point.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from extensionPoints import Decider
from nvidiator import androidApi as _a

#: Lets add-ons stop a beep (return False), as in NVDA.
decide_beep = Decider()


def beep(hz, length, left=50, right=50, isSpeechBeepCommand=False, **kwargs):
	if not decide_beep.decide(hz=hz, length=length, left=left, right=right, isSpeechBeepCommand=isSpeechBeepCommand):
		return
	_a.tones.beep(hz, length, left, right)
