# Nvidiator Android: NVDA's api module for add-ons. Objects are Android AccessibilityNodeInfo
# objects rather than NVDA objects.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
from nvidiator.androidApi import (  # noqa: F401
	copyToClip,
	getCaretObject,
	getClipData,
	getFocusAncestors,
	getFocusObject,
	getForegroundObject,
	getNavigatorObject,
	setNavigatorObject,
)
