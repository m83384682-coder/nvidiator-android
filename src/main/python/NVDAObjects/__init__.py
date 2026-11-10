# Nvidiator Android: NVDA objects for add-ons. Each one describes an Android on-screen item with
# NVDA's own properties (role, states, name, value, description, positionInfo), built with the
# same role and state mapping Nvidiator speaks with. They are snapshots: they don't change.
# Part of Nvidiator Android, an unofficial experimental port of NVDA to Android.
# Not affiliated with NV Access. Covered by the GNU General Public License, version 2.
import baseObject


class NVDAObject(baseObject.ScriptableObject):
	def __init__(self, role=None, states=None, name="", value=None, description="", windowClassName="",
			appName="", positionInfo=None, roleText=None, node=None, parent=None):
		super().__init__()
		import controlTypes

		self.role = role if role is not None else controlTypes.Role.UNKNOWN
		self.states = set(states or ())
		self.name = name or ""
		self.value = value
		self.description = description or ""
		self.windowClassName = windowClassName or ""
		self.positionInfo = positionInfo or {}
		self.roleText = roleText
		self.keyboardShortcut = ""
		self.location = None
		self.isFocusable = True
		self.hasFocus = True
		self.processID = 0
		self.windowHandle = 0
		self.windowControlID = 0
		self.parent = parent
		self.treeInterceptor = None
		self._node = node
		import appModuleHandler

		self.appModule = appModuleHandler.AppModule(appName=appName)

	@property
	def roleTextOrName(self):
		return self.roleText or self.role.displayString

	def __repr__(self):
		return "<NVDAObject role=%s name=%r>" % (getattr(self.role, "name", self.role), self.name)

	@classmethod
	def fromNodeInfo(cls, n: dict, node=None):
		"""An NVDA object for a node as Nodes.toJson describes it."""
		from nvidiator import androidRoles

		props = androidRoles.propertiesForNode(n)
		pos = {}
		if props.get("positionInfo_indexInGroup"):
			pos["indexInGroup"] = props["positionInfo_indexInGroup"]
			pos["similarItemsInGroup"] = props.get("positionInfo_similarItemsInGroup", 0)
		if props.get("positionInfo_level"):
			pos["level"] = props["positionInfo_level"]
		return cls(
			role=props.get("role"),
			states=props.get("states"),
			name=props.get("name", ""),
			value=props.get("value"),
			description=props.get("description", ""),
			windowClassName=n.get("cls", ""),
			appName=n.get("pkg", ""),
			positionInfo=pos,
			roleText=props.get("roleText"),
			node=node,
		)


# NVDA's Windows object classes, so add-ons that import them still load. Android items are never
# instances of these, so checks such as isinstance(obj, IAccessible) are simply False.
class Window(NVDAObject):
	pass
