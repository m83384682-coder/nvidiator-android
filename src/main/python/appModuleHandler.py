# Nvidiator Android: stand-in for NVDA's appModuleHandler. App modules target Windows programs,
# so Nvidiator never loads them, but add-ons can import this module without failing.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.

import baseObject

runningTable = {}


class AppModule(baseObject.ScriptableObject):
	def __init__(self, processID=0, appName=None):
		super().__init__()
		self.processID = processID
		self.appName = appName or ""

	def terminate(self):
		pass
