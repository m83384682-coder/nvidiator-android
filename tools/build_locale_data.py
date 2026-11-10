#!/usr/bin/env python3
"""Copy NVDA's locale data into the app assets (assets/nvda/locale).

For every language NVDA ships, this copies symbols.dic and characterDescriptions.dic
(used by NVDA's characterProcessing for punctuation and spelling) and compiles nvda.po
into nvda.mo (NVDA's own translations of roles, states and messages).

Usage: build_locale_data.py <nvda source dir> <app assets/nvda dir>
Needs: pip install polib
"""

import os
import shutil
import sys

import polib

src = os.path.join(sys.argv[1], "locale")
out = os.path.join(sys.argv[2], "locale")
shutil.rmtree(out, ignore_errors=True)
os.makedirs(out)
count = 0
for lang in sorted(os.listdir(src)):
	d = os.path.join(src, lang)
	if not os.path.isdir(d):
		continue
	od = os.path.join(out, lang)
	os.makedirs(od, exist_ok=True)
	for f in ("symbols.dic", "characterDescriptions.dic"):
		if os.path.isfile(os.path.join(d, f)):
			shutil.copy(os.path.join(d, f), od)
	po = os.path.join(d, "LC_MESSAGES", "nvda.po")
	if not os.path.isfile(po):
		po = os.path.join(d, "nvda.po")
	if os.path.isfile(po):
		cat = polib.pofile(po)
		# Keep only translated, non-fuzzy entries, like msgfmt does.
		for e in list(cat):
			if e.obsolete or "fuzzy" in e.flags:
				cat.remove(e)
		os.makedirs(os.path.join(od, "LC_MESSAGES"), exist_ok=True)
		cat.save_as_mofile(os.path.join(od, "LC_MESSAGES", "nvda.mo"))
		count += 1
	if not os.listdir(od):
		os.rmdir(od)
print(f"{count} translations compiled into {out}")
