# Nvidiator Android: NVDAObjects.UIA for add-ons that import it. Android has no UIA objects, so
# nothing is ever an instance of this class.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from NVDAObjects import Window


class UIA(Window):
	pass
