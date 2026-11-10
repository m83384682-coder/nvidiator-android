# Nvidiator Android: NVDA's globalPluginHandler, for add-ons. addonHandler loads each enabled
# add-on's globalPlugins package and keeps the running plugins here.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.

import baseObject

#: The running global plugins.
runningPlugins = set()


class GlobalPlugin(baseObject.ScriptableObject):
	"""Base class for global plugins, as in NVDA."""

	def terminate(self):
		"""Called before the plugin is unloaded."""

	def chooseNVDAObjectOverlayClasses(self, obj, clsList):
		"""NVDA objects are Windows objects; Nvidiator has none to change."""
