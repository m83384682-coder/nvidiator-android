"""Desktop test for the ported NVDA Python console (no Android service attached)."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "src", "main", "python"))
from nvidiator import bridge
bridge.init(os.path.join(HERE, "..", "src", "main", "assets", "nvda"), "en")
import pythonConsole

class UI:
    def __init__(self): self.out = []; self.prompt = ">>>"; self.closed = False
    def output(self, s): self.out.append(s)
    def setPrompt(self, p): self.prompt = p
    def close(self): self.closed = True

ui = UI()
print(pythonConsole.initialize(ui))
fails = 0
def check(label, cond):
    global fails
    print(("PASS " if cond else "FAIL ") + label)
    fails += 0 if cond else 1

pythonConsole.push("1 + 2")
check("expression result printed", "\n3\n" in "".join(ui.out))
pythonConsole.pushBlock("def sq(x):\n    return x * x\n\nfor n in (12,):\n    print(sq(n))\n")
check("pasted block defines and runs", "\n144\n" in "".join(ui.out))
pythonConsole.push("for i in range(2):")
check("prompt becomes ...", ui.prompt == "...")
pythonConsole.push("    print('loop', i)")
pythonConsole.push("")
check("loop ran", "loop 1" in "".join(ui.out) and ui.prompt == ">>>")
pythonConsole.push("controlTypes.Role.BUTTON.displayString")
check("NVDA controlTypes in namespace", "'button'\n" in "".join(ui.out))
pythonConsole.push("1/0")
check("traceback shown", any("ZeroDivisionError" in s for s in ui.out))
pythonConsole.push("_('No next')")
check("gettext _ not clobbered", _("blank") == "blank")
check("completion", pythonConsole.complete("controlTypes.Role.BUTT") == "ON")
pythonConsole.push("exit()")
check("exit closes", ui.closed)
print("".join(ui.out))
print("%d failed" % fails); sys.exit(1 if fails else 0)
