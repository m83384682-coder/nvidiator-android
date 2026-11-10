# Nvidiator Android: stand-in for NVDA's garbageHandler. NVDA tracks objects to report reference
# cycles on Windows; on Android there is nothing to track, so TrackedObject is a plain base class.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.


class TrackedObject:
	__slots__ = ("__weakref__",)
