# Nvidiator Android: a cut-down version of NVDA's languageHandler.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
# Like NVDA, it installs _, ngettext, pgettext and npgettext as builtins from NVDA's own
# translation catalogue (nvda.mo), so NVDA's modules speak in the user's language.
import builtins
import gettext
import os

import globalVars

curLang = "en"
_translations = gettext.NullTranslations()


def _localeDir():
	return os.path.join(globalVars.appDir, "locale")


def listAvailableLanguages():
	d = _localeDir()
	langs = ["en"]
	for name in sorted(os.listdir(d)):
		if os.path.isfile(os.path.join(d, name, "LC_MESSAGES", "nvda.mo")):
			langs.append(name)
	return langs


def normalizeLanguage(lang: str) -> str:
	lang = (lang or "en").replace("-", "_")
	parts = lang.split("_")
	if len(parts) > 1:
		return parts[0].lower() + "_" + parts[1].upper()
	return parts[0].lower()


def _pickLocale(lang: str) -> str:
	"""Return the best NVDA locale directory for an Android language tag like 'ur_PK'."""
	lang = normalizeLanguage(lang)
	d = _localeDir()
	for cand in (lang, lang.split("_")[0]):
		if os.path.isdir(os.path.join(d, cand)):
			return cand
	return "en"


def setLanguage(lang: str) -> str:
	global curLang, _translations
	curLang = _pickLocale(lang)
	try:
		_translations = gettext.translation("nvda", localedir=_localeDir(), languages=[curLang])
	except OSError:
		_translations = gettext.NullTranslations()
	_translations.install(names=["ngettext", "pgettext", "npgettext"])
	return curLang


def getLanguage() -> str:
	return curLang


# Make sure the builtins exist even before setLanguage is called, as NVDA modules use them at import.
if not hasattr(builtins, "_"):
	gettext.NullTranslations().install(names=["ngettext", "pgettext", "npgettext"])
