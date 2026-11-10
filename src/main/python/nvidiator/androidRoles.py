# Nvidiator Android: maps Android accessibility nodes onto NVDA's controlTypes roles and states.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
#
# On Windows, NVDA gets roles and states from IAccessible2, UIA and MSAA. On Android the
# equivalent is AccessibilityNodeInfo. The Java side flattens each node into a dict, and this
# module turns that dict into the (Role, states, properties) triple NVDA's speech code expects.

import re

import controlTypes
from controlTypes import Role, State

# Simple Android class names (the part after the last dot or $) to NVDA roles.
_CLASS_ROLES = {
	"Button": Role.BUTTON,
	"ImageButton": Role.BUTTON,
	"MaterialButton": Role.BUTTON,
	"FloatingActionButton": Role.BUTTON,
	"ExtendedFloatingActionButton": Role.BUTTON,
	"Chip": Role.BUTTON,
	"CheckBox": Role.CHECKBOX,
	"CheckedTextView": Role.CHECKBOX,
	"MaterialCheckBox": Role.CHECKBOX,
	"RadioButton": Role.RADIOBUTTON,
	"MaterialRadioButton": Role.RADIOBUTTON,
	"Switch": Role.SWITCH,
	"SwitchCompat": Role.SWITCH,
	"SwitchMaterial": Role.SWITCH,
	"MaterialSwitch": Role.SWITCH,
	"ToggleButton": Role.TOGGLEBUTTON,
	"EditText": Role.EDITABLETEXT,
	"AutoCompleteTextView": Role.EDITABLETEXT,
	"MultiAutoCompleteTextView": Role.EDITABLETEXT,
	"TextInputEditText": Role.EDITABLETEXT,
	"SearchAutoComplete": Role.EDITABLETEXT,
	"TextView": Role.STATICTEXT,
	"MaterialTextView": Role.STATICTEXT,
	"ImageView": Role.GRAPHIC,
	"SeekBar": Role.SLIDER,
	"RatingBar": Role.SLIDER,
	"Slider": Role.SLIDER,
	"RangeSlider": Role.SLIDER,
	"ProgressBar": Role.PROGRESSBAR,
	"LinearProgressIndicator": Role.PROGRESSBAR,
	"CircularProgressIndicator": Role.PROGRESSBAR,
	"Spinner": Role.COMBOBOX,
	"AppCompatSpinner": Role.COMBOBOX,
	"ListView": Role.LIST,
	"RecyclerView": Role.LIST,
	"GridView": Role.LIST,
	"ExpandableListView": Role.TREEVIEW,
	"TabWidget": Role.TABCONTROL,
	"TabLayout": Role.TABCONTROL,
	"TabView": Role.TAB,
	"Toolbar": Role.TOOLBAR,
	"ActionBar": Role.TOOLBAR,
	"MaterialToolbar": Role.TOOLBAR,
	"BottomNavigationView": Role.TABCONTROL,
	"NavigationBarView": Role.TABCONTROL,
	"WebView": Role.DOCUMENT,
	"NumberPicker": Role.SPINBUTTON,
	"DatePicker": Role.DATEEDITOR,
	"TimePicker": Role.DATEEDITOR,
	"CalendarView": Role.CALENDAR,
	"VideoView": Role.VIDEO,
	"ActionMenuItemView": Role.BUTTON,
	"ListMenuItemView": Role.MENUITEM,
	"MenuItem": Role.MENUITEM,
	"ScrollView": Role.SCROLLPANE,
	"HorizontalScrollView": Role.SCROLLPANE,
	"NestedScrollView": Role.SCROLLPANE,
	"ViewPager": Role.PANE,
	"ViewPager2": Role.PANE,
	"DrawerLayout": Role.PANE,
}

# Chrome and WebView report the web role in the AccessibilityNodeInfo.chromeRole extra.
# These map ARIA / Chromium role names to NVDA roles, in the spirit of NVDA's aria.py.
_WEB_ROLES = {
	"link": Role.LINK,
	"heading": Role.HEADING,
	"button": Role.BUTTON,
	"popupbutton": Role.BUTTON,
	"togglebutton": Role.TOGGLEBUTTON,
	"checkbox": Role.CHECKBOX,
	"radiobutton": Role.RADIOBUTTON,
	"switch": Role.SWITCH,
	"textfield": Role.EDITABLETEXT,
	"searchbox": Role.EDITABLETEXT,
	"textfieldwithcombobox": Role.COMBOBOX,
	"combobox": Role.COMBOBOX,
	"comboboxselect": Role.COMBOBOX,
	"comboboxmenubutton": Role.COMBOBOX,
	"listbox": Role.LIST,
	"listboxoption": Role.LISTITEM,
	"list": Role.LIST,
	"listitem": Role.LISTITEM,
	"image": Role.GRAPHIC,
	"img": Role.GRAPHIC,
	"figure": Role.FIGURE,
	"table": Role.TABLE,
	"grid": Role.TABLE,
	"row": Role.TABLEROW,
	"cell": Role.TABLECELL,
	"gridcell": Role.TABLECELL,
	"columnheader": Role.TABLECOLUMNHEADER,
	"rowheader": Role.TABLEROWHEADER,
	"tab": Role.TAB,
	"tablist": Role.TABCONTROL,
	"tabpanel": Role.PROPERTYPAGE,
	"menu": Role.POPUPMENU,
	"menubar": Role.MENUBAR,
	"menuitem": Role.MENUITEM,
	"menuitemcheckbox": Role.CHECKMENUITEM,
	"menuitemradio": Role.RADIOMENUITEM,
	"toolbar": Role.TOOLBAR,
	"dialog": Role.DIALOG,
	"alertdialog": Role.DIALOG,
	"alert": Role.ALERT,
	"slider": Role.SLIDER,
	"spinbutton": Role.SPINBUTTON,
	"progressindicator": Role.PROGRESSBAR,
	"progressbar": Role.PROGRESSBAR,
	"tree": Role.TREEVIEW,
	"treeitem": Role.TREEVIEWITEM,
	"article": Role.ARTICLE,
	"region": Role.REGION,
	"group": Role.GROUPING,
	"radiogroup": Role.GROUPING,
	"separator": Role.SEPARATOR,
	"splitter": Role.SEPARATOR,
	"paragraph": Role.PARAGRAPH,
	"blockquote": Role.BLOCKQUOTE,
	"math": Role.MATH,
	"rootwebarea": Role.DOCUMENT,
	"webarea": Role.DOCUMENT,
	"document": Role.DOCUMENT,
	"statictext": Role.STATICTEXT,
	"inlinetextbox": Role.STATICTEXT,
	"labeltext": Role.LABEL,
	"video": Role.VIDEO,
	"audio": Role.AUDIO,
	"status": Role.STATUSBAR,
	"banner": Role.LANDMARK,
	"navigation": Role.LANDMARK,
	"main": Role.LANDMARK,
	"contentinfo": Role.LANDMARK,
	"complementary": Role.LANDMARK,
	"search": Role.LANDMARK,
	"form": Role.LANDMARK,
	"header": Role.LANDMARK,
	"footer": Role.LANDMARK,
}

_LANDMARK_ALIASES = {"header": "banner", "footer": "contentinfo"}


def landmarkLabel(webRole: str) -> str:
	"""Return NVDA's translated landmark name, e.g. 'navigation', from aria.py's pgettext('aria', ...)."""
	key = _LANDMARK_ALIASES.get(webRole, webRole)
	labels = {
		"banner": pgettext("aria", "banner"),
		"complementary": pgettext("aria", "complementary"),
		"contentinfo": pgettext("aria", "content info"),
		"main": pgettext("aria", "main"),
		"navigation": pgettext("aria", "navigation"),
		"search": pgettext("aria", "search"),
		"form": pgettext("aria", "form"),
	}
	return labels.get(key, key)


def simpleClassName(cls: str) -> str:
	if not cls:
		return ""
	return re.split(r"[.$]", cls)[-1]


def normWebRole(r) -> str:
	if not r:
		return ""
	r = str(r)
	if len(r) > 1 and r[0] == "k" and r[1].isupper():
		r = r[1:]
	return r.replace("_", "").replace("-", "").lower()


def _headingRole(level):
	# NVDA's browse mode says "heading level 2", which comes from Role.HEADING plus the level,
	# so the level is added in propertiesForNode rather than using Role.HEADING2.
	return Role.HEADING


def roleForNode(n: dict) -> Role:
	webRole = normWebRole(n.get("chromeRole"))
	if webRole in _WEB_ROLES:
		role = _WEB_ROLES[webRole]
		if role == Role.HEADING:
			return _headingRole(n.get("headingLevel"))
		return role
	if n.get("heading"):
		return _headingRole(n.get("headingLevel"))
	if n.get("link"):
		return Role.LINK
	simple = simpleClassName(n.get("cls", ""))
	role = _CLASS_ROLES.get(simple)
	if role is None:
		# Fall back on the platform class the app's widget extends, which Java sends as "baseCls".
		role = _CLASS_ROLES.get(simpleClassName(n.get("baseCls", "")))
	roleDesc = (n.get("roleDesc") or "").strip().lower()
	if roleDesc == "tab":
		return Role.TAB
	if role is None:
		if n.get("editable"):
			return Role.EDITABLETEXT
		if n.get("range"):
			return Role.SLIDER if n.get("rangeAdjustable") else Role.PROGRESSBAR
		if n.get("checkable"):
			return Role.CHECKBOX
		if n.get("collection"):
			return Role.LIST
		if n.get("isListItem"):
			return Role.LISTITEM
		if n.get("text") or n.get("desc") or n.get("name"):
			# A generic view that only carries text is static text to NVDA.
			return Role.STATICTEXT if not n.get("clickable") else Role.UNKNOWN
		return Role.UNKNOWN
	if role in (Role.STATICTEXT, Role.UNKNOWN) and n.get("isListItem"):
		return Role.LISTITEM
	if role == Role.CHECKBOX and simple == "CheckedTextView" and not n.get("checkable"):
		return Role.STATICTEXT
	return role


_CLICKABLE_REPORTED_ROLES = {
	Role.STATICTEXT,
	Role.UNKNOWN,
	Role.GRAPHIC,
	Role.LABEL,
	Role.PARAGRAPH,
	Role.SECTION,
	Role.GROUPING,
	Role.PANE,
	Role.HEADING,
	Role.HEADING1,
	Role.HEADING2,
	Role.HEADING3,
	Role.HEADING4,
	Role.HEADING5,
	Role.HEADING6,
	Role.TABLECELL,
	Role.FIGURE,
	Role.ARTICLE,
	Role.REGION,
}


def statesForNode(n: dict, role: Role) -> set:
	states = set()
	if not n.get("enabled", True):
		states.add(State.UNAVAILABLE)
	if n.get("focusable") or n.get("clickable"):
		states.add(State.FOCUSABLE)
	if n.get("focused"):
		states.add(State.FOCUSED)
	checked = n.get("checked", 0)
	if n.get("checkable"):
		if role not in (Role.SWITCH, Role.TOGGLEBUTTON):
			# NVDA only says "not checked" for things it knows are checkable.
			states.add(State.CHECKABLE)
		if role == Role.SWITCH:
			if checked:
				states.add(State.ON)
		elif role == Role.TOGGLEBUTTON:
			if checked:
				states.add(State.PRESSED)
		elif checked == 2:
			states.add(State.HALFCHECKED)
		elif checked:
			states.add(State.CHECKED)
	if n.get("selected"):
		states.add(State.SELECTED)
	if (n.get("clickable") or n.get("longClickable")) and role in _CLICKABLE_REPORTED_ROLES:
		# On Android nearly every control is clickable. Like NVDA in web content, only say
		# "clickable" where the role alone would not tell the user they can activate it.
		states.add(State.CLICKABLE)
	if n.get("password"):
		states.add(State.PROTECTED)
	if role == Role.EDITABLETEXT:
		if n.get("editable") is False and n.get("readOnlyKnown"):
			states.add(State.READONLY)
		if n.get("multiLine"):
			states.add(State.MULTILINE)
		if simpleClassName(n.get("cls", "")) in ("AutoCompleteTextView", "MultiAutoCompleteTextView", "SearchAutoComplete"):
			states.add(State.AUTOCOMPLETE)
	exp = n.get("expand")
	if exp == "expanded":
		states.add(State.EXPANDED)
	elif exp == "collapsed":
		states.add(State.COLLAPSED)
	if n.get("error"):
		states.add(State.INVALID_ENTRY)
	if n.get("required"):
		states.add(State.REQUIRED)
	if n.get("visited"):
		states.add(State.VISITED)
	if n.get("busy"):
		states.add(State.BUSY)
	if n.get("hasPopup") and role != Role.COMBOBOX:
		states.add(State.HASPOPUP)
	return states


def _rangeValue(n: dict, role: Role):
	r = n.get("range")
	if not r:
		return None
	try:
		mn, mx, cur, kind = float(r["min"]), float(r["max"]), float(r["cur"]), int(r.get("type", 0))
	except (KeyError, TypeError, ValueError):
		return None
	if kind == 2:  # RANGE_TYPE_PERCENT
		return ngettext("%d percent", "%d percent", int(round(cur))) % int(round(cur))
	if role == Role.PROGRESSBAR and mx > mn:
		pct = int(round((cur - mn) * 100.0 / (mx - mn)))
		return ngettext("%d percent", "%d percent", pct) % pct
	if cur == int(cur):
		return str(int(cur))
	return ("%.2f" % cur).rstrip("0").rstrip(".")


def propertiesForNode(n: dict) -> dict:
	"""Return the keyword arguments for speechPort.getPropertiesSpeech, as NVDA builds them in
	getObjectPropertiesSpeech from an NVDAObject."""
	role = roleForNode(n)
	states = statesForNode(n, role)
	props = {"role": role, "states": states}
	name = n.get("name") or ""
	value = None
	placeholder = None
	if role in (Role.EDITABLETEXT, Role.COMBOBOX) and n.get("editable"):
		# For edits NVDA's name is the label and the value is the text in the field.
		name = n.get("label") or n.get("desc") or ""
		text = "" if n.get("showingHint") else (n.get("text") or "")
		if text:
			value = text
		else:
			hint = n.get("hint") or (n.get("text") if n.get("showingHint") else "")
			if hint and hint != name:
				placeholder = hint
			value = _("blank") if not placeholder else None
	elif n.get("range") is not None:
		value = n.get("stateDesc") or _rangeValue(n, role)
	elif n.get("stateDesc") and not n.get("checkable"):
		value = n.get("stateDesc")
	if role == Role.LANDMARK:
		props["roleText"] = "%s %s" % (landmarkLabel(normWebRole(n.get("chromeRole"))), Role.LANDMARK.displayString)
	elif n.get("roleDesc") and role not in (Role.TAB,):
		props["roleText"] = n["roleDesc"]
	if name:
		props["name"] = name
	if value:
		props["value"] = value
	if placeholder:
		props["placeholder"] = placeholder
	desc = n.get("description")
	if desc and desc != name and desc != value:
		props["description"] = desc
	if n.get("error"):
		props["errorMessage"] = n["error"]
	idx, total = n.get("posIndex", 0), n.get("posTotal", 0)
	if idx and total:
		props["positionInfo_indexInGroup"] = idx
		props["positionInfo_similarItemsInGroup"] = total
	if role == Role.HEADING and n.get("headingLevel"):
		props["positionInfo_level"] = n["headingLevel"]
	elif n.get("level"):
		props["positionInfo_level"] = n["level"]
	coll = n.get("collection")
	if coll and role == Role.TABLE:
		props["rowCount"] = coll.get("rows", 0)
		props["columnCount"] = coll.get("cols", 0)
	return props


def containerRoleText(c: dict) -> str:
	"""The spoken role of a container, e.g. 'list', 'tool bar' or 'navigation landmark'."""
	role = roleForNode(c)
	if role == Role.LANDMARK:
		return "%s %s" % (landmarkLabel(normWebRole(c.get("chromeRole"))), Role.LANDMARK.displayString)
	if c.get("roleDesc") and role not in (Role.TAB, Role.TABCONTROL):
		return c["roleDesc"]
	if role in (Role.UNKNOWN, Role.STATICTEXT, Role.SCROLLPANE, Role.PANE):
		role = Role.LIST if c.get("collection") else Role.GROUPING
	return role.displayString


__all__ = ["roleForNode", "statesForNode", "propertiesForNode", "containerRoleText", "controlTypes"]
