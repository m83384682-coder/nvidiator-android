# Nvidiator Android: stand-in for NVDA's logHandler, sending NVDA's log calls to Android's log.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
import logging
import sys

_logger = logging.getLogger("nvda")
if not _logger.handlers:
	_h = logging.StreamHandler(sys.stderr)
	_h.setFormatter(logging.Formatter("NVDA %(levelname)s: %(message)s"))
	_logger.addHandler(_h)
	_logger.setLevel(logging.INFO)


class _Log:
	def debug(self, msg, *a, **k):
		pass

	def debugWarning(self, msg, *a, **k):
		pass

	def info(self, msg, *a, **k):
		_logger.info(msg, *a, **{x: y for x, y in k.items() if x == "exc_info"})

	def warning(self, msg, *a, **k):
		_logger.warning(msg, *a, **{x: y for x, y in k.items() if x == "exc_info"})

	def error(self, msg, *a, **k):
		_logger.error(msg, *a, **{x: y for x, y in k.items() if x == "exc_info"})

	def exception(self, msg="", *a, **k):
		_logger.exception(msg, *a)

	def isEnabledFor(self, level):
		return False


log = _Log()
