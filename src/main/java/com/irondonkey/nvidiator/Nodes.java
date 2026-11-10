// Nvidiator Android: reads Android's accessibility tree the way NVDA reads objects.
// Flattens AccessibilityNodeInfo into the JSON the Python side (androidRoles.py) understands,
// works out which nodes are worth stopping on (NVDA's simple review mode), and builds the
// container chain used for "list with 5 items" and "out of list".
// Not affiliated with or endorsed by NV Access. Licensed under the GNU GPL version 2.
package com.irondonkey.nvidiator;

import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Nodes {
    private Nodes() {}

    static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    static String simpleClass(AccessibilityNodeInfo n) {
        String c = str(n.getClassName());
        int i = Math.max(c.lastIndexOf('.'), c.lastIndexOf('$'));
        return i >= 0 ? c.substring(i + 1) : c;
    }

    static String extra(AccessibilityNodeInfo n, String key) {
        try {
            Bundle b = n.getExtras();
            if (b == null) return "";
            Object v = b.get(key);
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String chromeRole(AccessibilityNodeInfo n) { return extra(n, "AccessibilityNodeInfo.chromeRole"); }
    static String roleDescription(AccessibilityNodeInfo n) { return extra(n, "AccessibilityNodeInfo.roleDescription"); }

    static boolean isActionable(AccessibilityNodeInfo n) {
        if (n.isClickable() || n.isLongClickable() || n.isCheckable() || n.isEditable()) return true;
        if (n.isFocusable() && !isContainer(n)) return true;
        return Build.VERSION.SDK_INT >= 28 && n.isScreenReaderFocusable();
    }

    static boolean isContainer(AccessibilityNodeInfo n) {
        if (n.getChildCount() == 0) return false;
        if (n.getCollectionInfo() != null || n.isScrollable()) return true;
        String s = simpleClass(n);
        return s.endsWith("ScrollView") || s.equals("RecyclerView") || s.equals("ListView")
                || s.equals("GridView") || s.startsWith("ViewPager") || s.equals("WebView");
    }

    static String ownText(AccessibilityNodeInfo n) {
        String cd = str(n.getContentDescription());
        if (!cd.isEmpty()) return cd;
        return str(n.getText());
    }

    static boolean hasOwnWidget(AccessibilityNodeInfo n) {
        return n.isCheckable() || n.isEditable() || n.getRangeInfo() != null;
    }

    /** Text of non-actionable descendants, the way TalkBack and NVDA's simple review name a row. */
    static void collectText(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (depth > 8) return;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null || !c.isVisibleToUser()) continue;
            if (isActionable(c)) continue;
            String t = ownText(c);
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(t);
                if (!str(c.getContentDescription()).isEmpty()) continue;
            }
            collectText(c, sb, depth + 1);
        }
    }

    static String name(AccessibilityNodeInfo n) {
        String t = ownText(n);
        if (!t.isEmpty() && !n.isEditable()) return t;
        if (n.isEditable()) return "";
        StringBuilder sb = new StringBuilder();
        collectText(n, sb, 0);
        return sb.toString();
    }

    static boolean hasSpeech(AccessibilityNodeInfo n) {
        if (!ownText(n).isEmpty() || hasOwnWidget(n)) return true;
        if (Build.VERSION.SDK_INT >= 26 && !str(n.getHintText()).isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        collectText(n, sb, 0);
        return sb.length() > 0;
    }

    /** NVDA's simple review mode: the objects swipes stop on, in reading order. */
    public static List<AccessibilityNodeInfo> stops(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        if (root != null) collectStops(root, false, out, 0);
        return out;
    }

    private static void collectStops(AccessibilityNodeInfo n, boolean insideStop, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 60 || out.size() > 3000) return;
        boolean visible = n.isVisibleToUser();
        boolean stop = false;
        if (visible) {
            boolean act = isActionable(n);
            if (act && !isContainer(n)) {
                stop = insideStop ? (hasOwnWidget(n) || !ownText(n).isEmpty() || hasSpeech(n)) : hasSpeech(n);
            } else if (!act && !insideStop && !ownText(n).isEmpty()) {
                stop = true;
            }
        }
        if (stop) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectStops(n.getChild(i), insideStop || stop, out, depth + 1);
        }
    }

    /** Finds what a finger on the screen means: the nearest actionable ancestor, or the text itself. */
    public static AccessibilityNodeInfo stopFor(AccessibilityNodeInfo n) {
        if (n == null) return null;
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (cur.isVisibleToUser() && isActionable(cur) && !isContainer(cur) && hasSpeech(cur)) return cur;
            cur = cur.getParent();
        }
        if (!ownText(n).isEmpty()) return n;
        return null;
    }

    static boolean isReportableContainer(AccessibilityNodeInfo n) {
        String cr = Nodes.chromeRoleNorm(n);
        switch (cr) {
            case "list": case "listbox": case "table": case "grid": case "toolbar": case "tablist":
            case "navigation": case "banner": case "main": case "contentinfo": case "complementary":
            case "search": case "form": case "dialog": case "alertdialog": case "menu": case "menubar":
            case "tree": case "radiogroup": case "header": case "footer":
                return true;
            default:
                break;
        }
        if (!cr.isEmpty()) return false;
        AccessibilityNodeInfo.CollectionInfo ci = n.getCollectionInfo();
        if (ci != null && (ci.getRowCount() > 1 || ci.getColumnCount() > 1)) return true;
        String s = simpleClass(n);
        return s.endsWith("Toolbar") || s.equals("TabLayout") || s.equals("TabWidget") || s.equals("RadioGroup")
                || s.equals("BottomNavigationView") || s.equals("NavigationBarView");
    }

    static String chromeRoleNorm(AccessibilityNodeInfo n) {
        String r = chromeRole(n);
        if (r.length() > 1 && r.charAt(0) == 'k' && Character.isUpperCase(r.charAt(1))) r = r.substring(1);
        return r.replace("_", "").replace("-", "").toLowerCase();
    }

    /** The containers around a node, outermost first. */
    public static List<AccessibilityNodeInfo> containers(AccessibilityNodeInfo n) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        if (n == null) return out;
        AccessibilityNodeInfo p = n.getParent();
        int guard = 0;
        while (p != null && guard++ < 40) {
            if (isReportableContainer(p)) out.add(0, p);
            p = p.getParent();
        }
        return out;
    }

    static boolean hasLink(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t instanceof Spanned) {
            Spanned s = (Spanned) t;
            return s.getSpans(0, s.length(), URLSpan.class).length > 0
                    || (s.getSpans(0, s.length(), ClickableSpan.class).length > 0 && s.length() < 120);
        }
        return false;
    }

    static boolean hasAction(AccessibilityNodeInfo n, AccessibilityNodeInfo.AccessibilityAction a) {
        List<AccessibilityNodeInfo.AccessibilityAction> l = n.getActionList();
        return l != null && l.contains(a);
    }

    public static JSONObject toJson(AccessibilityNodeInfo n) {
        JSONObject o = new JSONObject();
        try {
            o.put("cls", str(n.getClassName()));
            String cr = chromeRole(n);
            if (!cr.isEmpty()) o.put("chromeRole", cr);
            String rd = roleDescription(n);
            if (!rd.isEmpty()) o.put("roleDesc", rd);
            o.put("name", name(n));
            String text = str(n.getText());
            if (!text.isEmpty()) o.put("text", text);
            if (n.isEditable()) {
                o.put("editable", true);
                String label = "";
                AccessibilityNodeInfo lb = n.getLabeledBy();
                if (lb != null) label = ownText(lb);
                if (label.isEmpty()) label = str(n.getContentDescription());
                if (!label.isEmpty()) o.put("label", label);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                String hint = str(n.getHintText());
                if (!hint.isEmpty()) o.put("hint", hint);
                if (n.isShowingHintText()) o.put("showingHint", true);
            }
            if (n.isPassword()) o.put("password", true);
            if (n.isMultiLine()) o.put("multiLine", true);
            if (n.isCheckable()) {
                o.put("checkable", true);
                int checked = n.isChecked() ? 1 : 0;
                if (Build.VERSION.SDK_INT >= 36) {
                    try {
                        int c = n.getChecked();
                        checked = c == AccessibilityNodeInfo.CHECKED_STATE_PARTIAL ? 2 : (c == AccessibilityNodeInfo.CHECKED_STATE_TRUE ? 1 : 0);
                    } catch (Throwable ignored) {}
                }
                o.put("checked", checked);
            }
            if (n.isSelected()) o.put("selected", true);
            if (n.isClickable()) o.put("clickable", true);
            if (n.isLongClickable()) o.put("longClickable", true);
            if (n.isFocusable()) o.put("focusable", true);
            if (n.isFocused()) o.put("focused", true);
            o.put("enabled", n.isEnabled());
            if (Build.VERSION.SDK_INT >= 28 && n.isHeading()) o.put("heading", true);
            if (hasLink(n) && !n.isEditable()) o.put("link", true);
            AccessibilityNodeInfo.RangeInfo ri = n.getRangeInfo();
            if (ri != null) {
                JSONObject r = new JSONObject();
                r.put("min", ri.getMin());
                r.put("max", ri.getMax());
                r.put("cur", ri.getCurrent());
                r.put("type", ri.getType());
                o.put("range", r);
                boolean adj = hasAction(n, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                        || hasAction(n, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
                        || (Build.VERSION.SDK_INT >= 24 && hasAction(n, AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS));
                if (adj) o.put("rangeAdjustable", true);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                String sd = str(n.getStateDescription());
                if (!sd.isEmpty()) o.put("stateDesc", sd);
            }
            if (Build.VERSION.SDK_INT >= 28) {
                String tip = str(n.getTooltipText());
                if (!tip.isEmpty()) o.put("description", tip);
            }
            if (n.isContentInvalid()) {
                String err = str(n.getError());
                o.put("error", err.isEmpty() ? " " : err);
            }
            if (hasAction(n, AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)) o.put("expand", "collapsed");
            else if (hasAction(n, AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE)) o.put("expand", "expanded");
            AccessibilityNodeInfo.CollectionItemInfo cii = n.getCollectionItemInfo();
            if (cii != null) {
                o.put("isListItem", true);
                AccessibilityNodeInfo parent = n.getParent();
                AccessibilityNodeInfo.CollectionInfo pci = null;
                for (int i = 0; i < 3 && parent != null && pci == null; i++) {
                    pci = parent.getCollectionInfo();
                    if (pci == null) parent = parent.getParent();
                }
                if (pci != null) {
                    int rows = pci.getRowCount(), cols = pci.getColumnCount();
                    if (cols <= 1 && rows > 0) {
                        o.put("posIndex", cii.getRowIndex() + 1);
                        o.put("posTotal", rows);
                    } else if (rows <= 1 && cols > 0) {
                        o.put("posIndex", cii.getColumnIndex() + 1);
                        o.put("posTotal", cols);
                    }
                }
                if (cii.isSelected()) o.put("selected", true);
            }
            AccessibilityNodeInfo.CollectionInfo ci = n.getCollectionInfo();
            if (ci != null) {
                JSONObject c = new JSONObject();
                int rows = Math.max(ci.getRowCount(), 0), cols = Math.max(ci.getColumnCount(), 0);
                c.put("rows", rows);
                c.put("cols", cols);
                c.put("items", cols <= 1 ? rows : (rows <= 1 ? cols : rows * cols));
                o.put("collection", c);
            }
            if (Build.VERSION.SDK_INT >= 28) {
                String pane = str(n.getPaneTitle());
                if (!pane.isEmpty() && o.optString("name").isEmpty()) o.put("name", pane);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    /** Container JSON: only what is needed for "list with 5 items", not the descendants' text. */
    public static JSONObject containerJson(AccessibilityNodeInfo n) {
        JSONObject o = new JSONObject();
        try {
            o.put("cls", str(n.getClassName()));
            String cr = chromeRole(n);
            if (!cr.isEmpty()) o.put("chromeRole", cr);
            String rd = roleDescription(n);
            if (!rd.isEmpty()) o.put("roleDesc", rd);
            String nm = str(n.getContentDescription());
            if (nm.isEmpty() && Build.VERSION.SDK_INT >= 28) nm = str(n.getPaneTitle());
            if (!nm.isEmpty()) o.put("name", nm);
            AccessibilityNodeInfo.CollectionInfo ci = n.getCollectionInfo();
            if (ci != null) {
                JSONObject c = new JSONObject();
                int rows = Math.max(ci.getRowCount(), 0), cols = Math.max(ci.getColumnCount(), 0);
                c.put("rows", rows);
                c.put("cols", cols);
                c.put("items", cols <= 1 ? rows : (rows <= 1 ? cols : rows * cols));
                o.put("collection", c);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public static JSONArray containersJson(List<AccessibilityNodeInfo> list) {
        JSONArray a = new JSONArray();
        for (AccessibilityNodeInfo n : list) a.put(containerJson(n));
        return a;
    }

    /** Simple Java description, used only if Python has not started. */
    public static String fallbackDescribe(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        String nm = n.isEditable() ? str(n.getContentDescription()) : name(n);
        sb.append(nm);
        if (n.isEditable()) {
            sb.append(" edit ");
            sb.append(str(n.getText()).isEmpty() ? "blank" : str(n.getText()));
        } else if (n.isCheckable()) {
            sb.append(n.isChecked() ? " checked" : " not checked");
        } else if (simpleClass(n).contains("Button")) {
            sb.append(" button");
        }
        if (!n.isEnabled()) sb.append(" unavailable");
        return sb.toString().trim();
    }
}
