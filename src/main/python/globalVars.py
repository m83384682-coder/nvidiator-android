# Nvidiator Android: stand-in for NVDA's globalVars module.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
# appDir points at the folder the Java side copies NVDA's locale data into
# (symbols.dic, characterDescriptions.dic and nvda.mo for each language), so NVDA's code
# finds it at appDir/locale/<lang>/ exactly as on Windows.
import os

appDir = os.environ.get("NVIDIATOR_APPDIR", os.path.dirname(os.path.abspath(__file__)))
