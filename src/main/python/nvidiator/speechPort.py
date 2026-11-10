# A part of NonVisual Desktop Access (NVDA)
# Copyright (C) 2006-2026 NV Access Limited, Peter Vágner, Aleksey Sadovoy, Babbage B.V., Bill Dengler,
# Julien Cochuyt, Derek Riemer, Cyrille Bougot, Leonard de Ruijter, Łukasz Golonka, Cary-rowen,
# Carter Temm
# This file may be used under the terms of the GNU General Public License, version 2 or later, as modified by the NVDA license.
# For full terms and any additional permissions, see the NVDA license file: https://github.com/nvaccess/nvda/blob/master/copying.txt
#
# Modified for Nvidiator Android (unofficial, not affiliated with NV Access), 2026:
# getPropertiesSpeech below is NVDA's speech.speech.getPropertiesSpeech with the Windows-only
# parts removed (keyboard shortcuts, math, annotations, the speech manager's sequence types).
# The property order, the silent roles, the state processing and the translated strings are
# NVDA's own, so objects are spoken in NVDA's order: name, role, value, states, description,
# placeholder, position.

"""NVDA's object speech, ported to Android."""

import controlTypes
from controlTypes import OutputReason


class _SpeechState:
	oldTreeLevel = None
	oldTableID = None
	oldRowNumber = None
	oldRowSpan = None
	oldColumnNumber = None
	oldColumnSpan = None


_speechState = _SpeechState()


def _rowAndColumnCountText(rowCount: int, columnCount: int) -> str | None:
	if rowCount and columnCount:
		# Translators: Speaks number of columns and rows in a table (example output: with 3 rows and 2 columns).
		return _("with {rowCount} rows and {columnCount} columns").format(
			rowCount=rowCount,
			columnCount=columnCount,
		)
	elif columnCount and not rowCount:
		# Translators: Speaks number of columns (example output: with 4 columns).
		return ngettext("with %s column", "with %s columns", columnCount) % columnCount
	elif rowCount and not columnCount:
		# Translators: Speaks number of rows (example output: with 2 rows).
		return ngettext("with %s row", "with %s rows", rowCount) % rowCount
	return None


def getPropertiesSpeech(
	reason: OutputReason = OutputReason.QUERY,
	**propertyValues,
) -> list[str]:
	textList: list[str] = []
	name: str | None = propertyValues.get("name")
	if name:
		textList.append(name)
	if "role" in propertyValues:
		role: controlTypes.Role = propertyValues["role"]
		speakRole = True
	elif "_role" in propertyValues:
		speakRole = False
		role = propertyValues["_role"]
	else:
		speakRole = False
		role = controlTypes.Role.UNKNOWN
	role = controlTypes.Role(role)
	value: str | None = propertyValues.get("value") if role not in controlTypes.silentValuesForRoles else None
	rowNumber = propertyValues.get("rowNumber")
	columnNumber = propertyValues.get("columnNumber")

	if role == controlTypes.Role.CHARTELEMENT:
		speakRole = False
	roleText: str | None = propertyValues.get("roleText")
	if (
		speakRole
		and (
			roleText
			or reason
			not in (
				OutputReason.SAYALL,
				OutputReason.CARET,
				OutputReason.FOCUS,
				OutputReason.QUICKNAV,
			)
			or not (name or value or rowNumber or columnNumber)
			or role not in controlTypes.silentRolesOnFocus
		)
		and (
			role != controlTypes.Role.MATH
			or reason
			not in (
				OutputReason.CARET,
				OutputReason.SAYALL,
			)
		)
	):
		textList.append(roleText if roleText else role.displayString)
	if value:
		textList.append(value)
	states = propertyValues.get("states")
	realStates = propertyValues.get("_states", states)
	negativeStates = propertyValues.get("negativeStates", set())
	# If the caller didn't want states, states will be None.
	# However, empty states means the caller still wants states, but the object
	# had no states; e.g. an unchecked check box with no other states.
	if states is not None or negativeStates:
		if states is None:
			# processAndLabelStates won't accept None for states.
			states = set()
		labelStates = controlTypes.processAndLabelStates(role, realStates, reason, states, negativeStates)
		textList.extend(labelStates)
	# sometimes description key is present but value is None
	description: str | None = propertyValues.get("description")
	if description:
		textList.append(description)
	if rowNumber:
		# Translators: Speaks current row number (example output: row 3).
		textList.append(_("row %s") % rowNumber)
	if columnNumber:
		# Translators: Speaks current column number (example output: column 3).
		textList.append(_("column %s") % columnNumber)
	rowCount = propertyValues.get("rowCount", 0)
	columnCount = propertyValues.get("columnCount", 0)
	rowAndColumnCountText = _rowAndColumnCountText(rowCount, columnCount)
	if rowAndColumnCountText:
		textList.append(rowAndColumnCountText)

	placeholder: str | None = propertyValues.get("placeholder", None)
	if placeholder:
		textList.append(placeholder)
	indexInGroup = propertyValues.get("positionInfo_indexInGroup", 0)
	similarItemsInGroup = propertyValues.get("positionInfo_similarItemsInGroup", 0)
	if 0 < indexInGroup <= similarItemsInGroup:
		# Translators: Spoken to indicate the position of an item in a group of items (such as a list).
		# {number} is replaced with the number of the item in the group.
		# {total} is replaced with the total number of items in the group.
		itemPosTranslation: str = _("{number} of {total}").format(
			number=indexInGroup,
			total=similarItemsInGroup,
		)
		textList.append(itemPosTranslation)
	if "positionInfo_level" in propertyValues:
		level = propertyValues.get("positionInfo_level", None)
		role = propertyValues.get("role", None)
		if level is not None:
			# Translators: Speaks the item level in treeviews (example output: level 2).
			levelTranslation: str = _("level %s") % level
			if (
				role in (controlTypes.Role.TREEVIEWITEM, controlTypes.Role.LISTITEM)
				and level != _speechState.oldTreeLevel
			):
				textList.insert(0, levelTranslation)
				_speechState.oldTreeLevel = level
			else:
				textList.append(levelTranslation)

	errorMessage: str | None = propertyValues.get("errorMessage", None)
	if errorMessage:
		textList.append(errorMessage)
	return textList


def getControlFieldEnterSpeech(roleText: str, childCount: int) -> list[str]:
	"""What NVDA's browse mode says when the caret enters a container, e.g. 'list with 5 items'.
	Adapted from speech.getControlFieldSpeech."""
	out = [roleText]
	if childCount:
		# Translators: Number of items in a list (example output: list with 5 items).
		out.append(ngettext("with %s item", "with %s items", childCount) % childCount)
	return out


def getControlFieldExitSpeech(roleTexts: list[str]) -> list[str]:
	"""What NVDA's browse mode says when leaving containers, e.g. 'out of list'.
	Adapted from speech.getControlFieldSpeech."""
	if not roleTexts:
		return []
	joinedRoleText = " ".join(roleTexts)
	# Translators: Indicates end of something (example output: at the end of a list, speaks out of list).
	return [_("out of %s") % joinedRoleText]
