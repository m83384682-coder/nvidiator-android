# Nvidiator Android: stand-in for NVDA's NVDAState module.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
import os


class _WritePaths:
	configDir = os.path.join(os.environ.get("HOME", "/tmp"), "nvidiator-config")

	def getSymbolsConfigFile(self, locale: str) -> str:
		return os.path.join(self.configDir, f"symbols-{locale}.dic")


WritePaths = _WritePaths()


def shouldWriteToDisk() -> bool:
	# User symbol editing is not ported yet, so nothing is written.
	return False
