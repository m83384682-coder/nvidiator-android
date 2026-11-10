# Nvidiator Android: NVDAObjects.IAccessible for add-ons that import it. Android has no IAccessible
# objects, so nothing is ever an instance of these classes.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from NVDAObjects import Window


class IAccessible(Window):
	pass
