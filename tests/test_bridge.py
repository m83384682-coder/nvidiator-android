"""Desktop tests for the Python side of Nvidiator Android (run with Python 3.11+).

python3 tests/test_bridge.py
"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "src", "main", "python"))
from nvidiator import bridge

APPDIR = os.path.join(HERE, "..", "src", "main", "assets", "nvda")
fails = 0
def check(label, got, want):
    global fails
    ok = got == want
    if not ok:
        fails += 1
    print(("PASS " if ok else "FAIL ") + label + ": " + repr(got) + ("" if ok else "  wanted " + repr(want)))

assert bridge.init(APPDIR, "en_US") == "en"
d = lambda **n: bridge.describe(json.dumps(n))
check("button", d(cls="android.widget.Button", name="OK", clickable=True, focusable=True), "OK button")
check("unchecked checkbox", d(cls="android.widget.CheckBox", name="Wi-Fi", checkable=True, checked=0, clickable=True), "Wi-Fi check box not checked")
check("checked checkbox", d(cls="android.widget.CheckBox", name="Wi-Fi", checkable=True, checked=1, clickable=True), "Wi-Fi check box checked")
check("switch on", d(cls="android.widget.Switch", name="Bluetooth", checkable=True, checked=1, clickable=True), "Bluetooth switch on")
check("switch off", d(cls="android.widget.Switch", name="Bluetooth", checkable=True, checked=0, clickable=True), "Bluetooth switch off")
check("empty edit", d(cls="android.widget.EditText", label="Search", editable=True, focusable=True, clickable=True), "Search edit blank")
check("edit with hint", d(cls="android.widget.EditText", editable=True, hint="Type a message", text="Type a message", showingHint=True, clickable=True), "edit Type a message")
check("edit with text", d(cls="android.widget.EditText", label="Name", editable=True, text="Iron Donkey", clickable=True), "Name edit Iron Donkey")
check("password", d(cls="android.widget.EditText", label="Password", editable=True, password=True, text="\u2022\u2022\u2022", clickable=True), "Password edit protected \u2022\u2022\u2022".replace("protected \u2022\u2022\u2022","\u2022\u2022\u2022 protected"))
check("list item position", d(cls="android.widget.LinearLayout", name="Settings", isListItem=True, clickable=True, posIndex=2, posTotal=7), "Settings 2 of 7")
check("text clickable", d(cls="android.view.View", name="Open", clickable=True), "Open clickable")
check("static text", d(cls="android.widget.TextView", name="Hello"), "Hello")
check("link visited", d(cls="android.view.View", chromeRole="link", name="NV Access", clickable=True, visited=True), "NV Access link visited")
check("heading 2", d(cls="android.view.View", chromeRole="heading", headingLevel=2, name="Downloads"), "Downloads heading level 2")
check("native heading", d(cls="android.widget.TextView", heading=True, name="Network"), "Network heading")
check("slider", d(cls="android.widget.SeekBar", name="Volume", range={"min":0,"max":100,"cur":40,"type":0}, rangeAdjustable=True, focusable=True), "Volume slider 40")
check("progress", d(cls="android.widget.ProgressBar", range={"min":0,"max":200,"cur":50,"type":0}), "progress bar 25 percent")
check("unavailable", d(cls="android.widget.Button", name="Send", enabled=False, clickable=True), "Send button unavailable")
check("collapsed", d(cls="android.widget.Button", name="More", expand="collapsed", clickable=True), "More button collapsed")
check("landmark", d(cls="android.view.View", chromeRole="navigation", name="Main menu"), "Main menu navigation landmark")
check("tab selected", d(cls="android.widget.LinearLayout", roleDesc="Tab", name="Chats", selected=True, clickable=True, posIndex=1, posTotal=3), "Chats tab selected 1 of 3")
check("focus with containers", bridge.describeFocus(json.dumps(dict(cls="android.widget.TextView", name="Inbox", isListItem=True, clickable=True, posIndex=1, posTotal=5)),
      json.dumps([dict(cls="androidx.recyclerview.widget.RecyclerView", collection={"items":5,"rows":5,"cols":1})]), json.dumps([dict(cls="android.widget.Toolbar")])),
      "out of tool bar, list with 5 items, Inbox 1 of 5")
check("symbols some", bridge.processSymbols("Price: $5 & 50%", 100), "Price: $5  and  50 percent ")
check("symbols all", bridge.processSymbols("a.b", 300), "a dot b")
check("symbols none", bridge.processSymbols("Hello, world!", 0), "Hello, world!")
check("char dot", bridge.speakCharacter("."), "dot")
check("char cap", bridge.speakCharacter("A"), "cap A")
check("char space", bridge.speakCharacter(" "), "space")
check("phonetic", bridge.characterDescription("a"), "Alfa")
m = json.loads(bridge.messages())
check("msg no next", m["noNext"], "No next")
check("msg speech mode", m["speechModeFmt"].format(mode=m["modeTalk"]), "Speech mode talk")
check("msg symbol level", m["symbolLevelFmt"] % m["symbolLevel300"], "Symbol level all")

check("state change checked", bridge.stateSpeech(json.dumps(dict(cls="android.widget.CheckBox", name="Wi-Fi", checkable=True, checked=1))), "checked")
check("state change unchecked", bridge.stateSpeech(json.dumps(dict(cls="android.widget.CheckBox", name="Wi-Fi", checkable=True, checked=0))), "not checked")
check("state change slider", bridge.stateSpeech(json.dumps(dict(cls="android.widget.SeekBar", name="Vol", range={"min":0,"max":10,"cur":7,"type":0}, rangeAdjustable=True))), "7")
check("languages", len(json.loads(bridge.listLanguages())) > 40, True)
# Other languages come straight from NVDA's translations.
bridge.setLanguage("de_DE")
check("german checkbox", d(cls="android.widget.CheckBox", name="WLAN", checkable=True, checked=1, clickable=True), "WLAN Kontrollfeld aktiviert")
check("german symbols", bridge.processSymbols("a.b", 300), "a Punkt b")
used = bridge.setLanguage("ur_PK")
check("urdu locale picked", used, "ur")
print("urdu button:", d(cls="android.widget.Button", name="OK", clickable=True))
print("urdu messages:", json.loads(bridge.messages())["noNext"])
bridge.setLanguage("en")
print("%d failed" % fails)
sys.exit(1 if fails else 0)
