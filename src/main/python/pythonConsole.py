# A part of NonVisual Desktop Access (NVDA)
# This file is covered by the GNU General Public License.
# See the file COPYING for more details.
# Copyright (C) 2008-2025 NV Access Limited, Leonard de Ruijter, Julien Cochuyt, Cyrille Bougot
#
# Modified for Nvidiator Android (unofficial, not affiliated with NV Access), 2026:
# NVDA's PythonConsole, Completer, CommandCompiler, HelpCommand and ExitConsoleCommand are kept.
# The wx ConsoleUI is replaced by an Android activity (PythonConsoleActivity.java) that calls
# push(), pushBlock() and complete() here through Chaquopy. The namespace holds the Android
# equivalents of NVDA's objects: focus, nav, fg and caretObj are the live
# android.view.accessibility.AccessibilityNodeInfo objects, and api, speech, tones and ui talk to
# the running screen reader.

"""Provides an interactive Python console which can be run from within Nvidiator Android."""

import builtins
import os
import code
import codeop
import sys
import pydoc
import re
import itertools
import rlcompleter

from logHandler import log


class HelpCommand:
	"""
	Emulation of the 'help' command found in the Python interactive shell.
	"""

	_reprMessage = _("Type help(object) to get help about object.")

	def __repr__(self):
		return self._reprMessage

	def __call__(self, *args, **kwargs):
		return pydoc.help(*args, **kwargs)


class ExitConsoleCommand:
	"""
	An object that can be used as an exit command that can close the console or print a friendly message for its repr.
	"""

	def __init__(self, exitFunc):
		self._exitFunc = exitFunc

	_reprMessage = _("Type exit() to exit the console")

	def __repr__(self):
		return self._reprMessage

	def __call__(self):
		self._exitFunc()


class Completer(rlcompleter.Completer):
	def attr_matches(self, text: str) -> list[str]:
		"""attr_matches implementation as used in Python 3.9.
		In Python 3.10, attr_matches was changed in a way that filtered out property descriptors,
		but more importantly, no longer catches exceptions when calling getattr.
		"""
		m = re.match(r"(\w+(\.\w+)*)\.(\w*)", text)
		if not m:
			return []
		expr, attr = m.group(1, 3)
		try:
			thisobject = eval(expr, self.namespace)
		except Exception:  # noqa: BLE001
			return []

		# get the content of the object, except __builtins__
		words = set(dir(thisobject))
		words.discard("__builtins__")

		if hasattr(thisobject, "__class__"):
			words.add("__class__")
			words.update(rlcompleter.get_class_members(thisobject.__class__))
		matches = []
		n = len(attr)
		if attr == "":
			noprefix = "_"
		elif attr == "_":
			noprefix = "__"
		else:
			noprefix = None
		while True:
			for word in words:
				if word[:n] == attr and not (noprefix and word[: n + 1] == noprefix):
					match = f"{expr}.{word}"
					try:
						val = getattr(thisobject, word)
					except Exception:  # noqa: BLE001, S110
						pass  # Include even if attribute not set
					else:
						match = self._callable_postfix(val, match)
					matches.append(match)
			if matches or not noprefix:
				break
			if noprefix == "_":
				noprefix = "__"
			else:
				noprefix = None
		matches.sort()
		return matches

	def _callable_postfix(self, val, word):
		# Just because something is callable doesn't always mean we want to call it.
		return word


class CommandCompiler(codeop.CommandCompiler):
	"""
	A L{codeop.CommandCompiler} exposing the status of the last compilation.
	"""

	def __init__(self):
		codeop.CommandCompiler.__init__(self)
		#: Whether the last compilation was on error.
		self.error = False

	def __call__(self, *args, **kwargs):
		self.error = False
		try:
			return codeop.CommandCompiler.__call__(self, *args, **kwargs)
		except:
			self.error = True
			raise


class PythonConsole(code.InteractiveConsole):
	"""An interactive Python console which directs output to supplied functions.
	This is necessary for a Python console with input/output other than stdin/stdout/stderr.
	Input is always received via the L{push} method.
	This console handles redirection of stdout and stderr and prevents clobbering of the gettext "_" builtin.
	"""

	def __init__(self, outputFunc, setPromptFunc, exitFunc, echoFunc=None, **kwargs):
		self._output = outputFunc
		self._echo = echoFunc
		self._exit = exitFunc
		self._setPrompt = setPromptFunc
		self._prompt = ">>>"

		#: The namespace available to the console. This can be updated externally.
		self.namespace = {}
		self.initNamespace()
		#: The variables last added to the namespace containing a snapshot of the screen reader's state.
		self._namespaceSnapshotVars = None

		super().__init__(locals=self.namespace, **kwargs)
		self.compile = CommandCompiler()
		self.prompt = ">>>"
		self.lastResult = None

	# NVDA uses AutoPropertyObject for this; a plain property does the same job here.
	@property
	def prompt(self):
		return self._prompt

	@prompt.setter
	def prompt(self, prompt):
		self._prompt = prompt
		self._setPrompt(prompt)

	def write(self, data):
		self._output(data)

	def flush(self):
		pass

	def push(self, line):
		if self._echo:
			self._echo("%s %s\n" % (self.prompt, line))  # noqa: UP031
		# Capture stdout/stderr output as well as code interaction.
		stdout, stderr = sys.stdout, sys.stderr
		sys.stdout = sys.stderr = self
		# Prevent this from messing with the gettext "_" builtin.
		saved_ = builtins._
		self.lastResult = None
		try:
			more = super().push(line)
		finally:
			sys.stdout, sys.stderr = stdout, stderr
		if builtins._ is not saved_:
			self.lastResult = builtins._
			# Preserve the namespace if gettext has explicitly been pushed there
			if "_" not in self.namespace or self.namespace["_"] is not saved_:
				self.namespace["_"] = builtins._
		builtins._ = saved_
		self.prompt = "..." if more else ">>>"
		return more

	def showsyntaxerror(self, filename: str | None = None, **kwargs):
		excepthook = sys.excepthook
		sys.excepthook = sys.__excepthook__
		super().showsyntaxerror(filename=filename, **kwargs)
		sys.excepthook = excepthook

	def showtraceback(self):
		excepthook = sys.excepthook
		sys.excepthook = sys.__excepthook__
		super().showtraceback()
		sys.excepthook = excepthook

	def initNamespace(self):
		"""(Re-)Initialize the console namespace with useful globals."""
		exitCmd = ExitConsoleCommand(self._exit)
		import config
		import controlTypes
		import characterProcessing
		import languageHandler
		from nvidiator import androidApi

		self.namespace.clear()
		self.namespace.update(
			{
				"help": HelpCommand(),
				"exit": exitCmd,
				"quit": exitCmd,
				"os": os,
				"sys": sys,
				"api": androidApi,
				"config": config,
				"controlTypes": controlTypes,
				"characterProcessing": characterProcessing,
				"languageHandler": languageHandler,
				"log": log,
				"speech": androidApi.speech,
				"tones": androidApi.tones,
				"ui": androidApi.ui,
				"nvwave": androidApi.nvwave,
			},
		)
		try:
			from java import jclass

			self.namespace["jclass"] = jclass
		except ImportError:
			pass

	def updateNamespaceSnapshotVars(self):
		"""Update the console namespace with a snapshot of the screen reader's current state:
		focus, focusAnc, fg, nav and caretObj, as in NVDA."""
		from nvidiator import androidApi

		self._namespaceSnapshotVarsGetters = {
			"focus": lambda: androidApi.snapshot("focus"),
			"focusAnc": androidApi.getFocusAncestors,
			"fg": lambda: androidApi.snapshot("fg"),
			"nav": lambda: androidApi.snapshot("nav"),
			"caretObj": lambda: androidApi.snapshot("focus"),
		}
		self._namespaceSnapshotVars = {}
		for name, getter in self._namespaceSnapshotVarsGetters.items():
			try:
				value = getter()
			except Exception:
				log.error(f"Unable to set {name} snapshot variable for python console.", exc_info=True)  # noqa: G201
				value = None
			self._namespaceSnapshotVars[name] = value
		self.namespace.update(self._namespaceSnapshotVars)

	def removeNamespaceSnapshotVars(self):
		if not self._namespaceSnapshotVars:
			return
		for key in self._namespaceSnapshotVars:
			try:
				del self.namespace[key]
			except KeyError:
				pass
		self._namespaceSnapshotVars = None


# --- The Android console front end --------------------------------------------------------
# These replace NVDA's wx ConsoleUI. The Java activity passes an object with output(str),
# setPrompt(str) and close() methods.

#: The singleton console, like NVDA's consoleUI.console.
console: PythonConsole | None = None
_completer: Completer | None = None
_completionState = {"ambiguous": False}


def initialize(ui) -> str:
	"""Create the console (once) and connect it to the Android activity. Returns the banner."""
	global console, _completer
	if console is None:
		console = PythonConsole(
			outputFunc=lambda data: ui.output(str(data)),
			setPromptFunc=lambda p: ui.setPrompt(str(p)),
			exitFunc=lambda: ui.close(),
			echoFunc=lambda data: ui.output(str(data)),
		)
		_completer = Completer(console.namespace)
	else:
		console._output = lambda data: ui.output(str(data))
		console._echo = lambda data: ui.output(str(data))
		console._setPrompt = lambda p: ui.setPrompt(str(p))
		console._exit = lambda: ui.close()
	console.updateNamespaceSnapshotVars()
	return "Python %s on Android (Nvidiator, NVDA's Python console)\n%s\n" % (
		sys.version.split()[0],
		"Snapshot variables: focus, focusAnc, fg, nav, caretObj. Try: speech.speakMessage('hello')",
	)


def push(line: str) -> bool:
	"""Run one line, exactly like typing it at NVDA's console prompt. Returns True when more
	input is needed (the prompt becomes ...)."""
	return bool(console.push(line))


def pushBlock(text: str) -> bool:
	"""Run pasted text. One line goes through push(), exactly like NVDA's prompt. Several lines
	are run as one script (Python's "exec" mode), so a pasted program runs whole, blank lines
	and all, the way it would from a .py file. Each line is echoed with the prompt like NVDA does."""
	text = text.replace("\r\n", "\n").replace("\r", "\n")
	lines = text.split("\n")
	while lines and not lines[-1].strip():
		lines.pop()
	if not lines:
		return push("")
	if len(lines) == 1 and console.prompt == ">>>":
		return push(lines[0])
	if console.prompt != ">>>":
		# Finish whatever block was already open, line by line, like NVDA's paste.
		more = False
		for line in lines:
			more = console.push(line)
		return bool(more)
	return runScript("\n".join(lines) + "\n")


def runScript(source: str) -> bool:
	"""Run a whole script in the console namespace, with output captured like push()."""
	if console._echo:
		for i, line in enumerate(source.rstrip("\n").split("\n")):
			console._echo("%s %s\n" % (">>>" if i == 0 else "...", line))
	stdout, stderr = sys.stdout, sys.stderr
	sys.stdout = sys.stderr = console
	saved_ = builtins._
	try:
		try:
			codeObj = compile(source, "<console>", "exec")
		except (SyntaxError, ValueError, OverflowError):
			console.showsyntaxerror("<console>")
		else:
			console.runcode(codeObj)
	finally:
		sys.stdout, sys.stderr = stdout, stderr
		builtins._ = saved_
	console.prompt = ">>>"
	return False


def refreshSnapshot() -> None:
	console.updateNamespaceSnapshotVars()


def getPrompt() -> str:
	return console.prompt if console else ">>>"


def complete(textBeforeCursor: str) -> str:
	"""Tab completion, using NVDA's Completer and longest-common-prefix logic. Returns the text
	to insert (may be empty)."""
	m = re.search(r"[\w.]*$", textBeforeCursor)
	if not m or not m.group(0):
		return ""
	original = m.group(0)
	completions = []
	for state in itertools.count():
		c = _completer.complete(original, state)
		if not c:
			break
		completions.append(c)
	if not completions:
		return ""
	if len(completions) == 1:
		return completions[0][len(original) :]
	longest = max(completions, key=len)
	for prefixLen in range(len(longest), 0, -1):
		prefix = longest[:prefixLen]
		if all(c.startswith(prefix) for c in completions):
			if prefix == original:
				break
			return prefix[len(original) :]
	return ""


def completions(textBeforeCursor: str) -> str:
	"""All completions for the word before the cursor, one per line, for the activity to list
	when the completion is ambiguous (NVDA shows a menu)."""
	m = re.search(r"[\w.]*$", textBeforeCursor)
	if not m or not m.group(0):
		return ""
	out = []
	for state in itertools.count():
		c = _completer.complete(m.group(0), state)
		if not c:
			break
		out.append(c)
	return "\n".join(out)
