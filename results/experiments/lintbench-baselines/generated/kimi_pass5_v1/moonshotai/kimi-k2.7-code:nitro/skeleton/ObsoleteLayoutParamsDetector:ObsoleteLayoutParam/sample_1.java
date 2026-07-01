package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. "
                            + "This usually happens when you change the parent layout or move view code around "
                            + "without updating the layout params. This will cause useless attribute processing at "
                            + "runtime, and is misleading for others reading the layout so the parameter should be "
                            + "removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, Set<String>> LAYOUT_PARAMS;

    static {
        Map<String, Set<String>> map = new HashMap<>();

        map.put("LinearLayout", newSet("layout_weight", "layout_gravity"));
        map.put("LinearLayoutCompat", newSet("layout_weight", "layout_gravity"));

        map.put("FrameLayout", newSet("layout_gravity"));

        map.put(
                "RelativeLayout",
                newSet(
                        "layout_above",
                        "layout_alignBaseline",
                        "layout_alignBottom",
                        "layout_alignEnd",
                        "layout_alignLeft",
                        "layout_alignParentBottom",
                        "layout_alignParentEnd",
                        "layout_alignParentLeft",
                        "layout_alignParentRight",
                        "layout_alignParentStart",
                        "layout_alignParentTop",
                        "layout_alignRight",
                        "layout_alignStart",
                        "layout_alignTop",
                        "layout_alignWithParentIfMissing",
                        "layout_below",
                        "layout_centerHorizontal",
                        "layout_centerInParent",
                        "layout_centerVertical",
                        "layout_toEndOf",
                        "layout_toLeftOf",
                        "layout_toRightOf",
                        "layout_toStartOf"));

        map.put(
                "GridLayout",
                newSet(
                        "layout_column",
                        "layout_columnSpan",
                        "layout_gravity",
                        "layout_row",
                        "layout_rowSpan"));

        map.put("TableRow", newSet("layout_column", "layout_span", "layout_weight", "layout_gravity"));

        map.put("DrawerLayout", newSet("layout_gravity"));
        map.put("SlidingPaneLayout", newSet("layout_weight"));

        map.put(
                "CoordinatorLayout",
                newSet(
                        "layout_anchor",
                        "layout_anchorGravity",
                        "layout_behavior",
                        "layout_dodgeInsetEdges",
                        "layout_insetEdge",
                        "layout_keyline"));

        map.put("ConstraintLayout", Collections.<String>emptySet());

        LAYOUT_PARAMS = Collections.unmodifiableMap(map);
    }

    private final List<PendingAttr> mPending = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!isAndroidLayoutParam(attribute)) {
            return;
        }
        mPending.add(new PendingAttr(context, attribute.getOwnerElement(), attribute));
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // All processing is deferred until after the whole file has been visited.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (PendingAttr pending : mPending) {
            Element parent = getParentElement(pending.child);
            if (parent == null) {
                continue;
            }

            String parentClass = getLayoutClassName(parent);
            if (parentClass == null) {
                continue;
            }

            Set<String> valid = LAYOUT_PARAMS.get(parentClass);
            if (valid == null) {
                continue;
            }

            String name = pending.attribute.getLocalName();
            if (isAlwaysValid(name)) {
                continue;
            }

            if (!valid.contains(name)) {
                String message =
                        String.format("Invalid layout param in a `%1$s`: `%2$s`", parentClass, name);
                pending.context.report(
                        ISSUE,
                        pending.attribute,
                        pending.context.getLocation(pending.attribute),
                        message);
            }
        }
        mPending.clear();
    }

    private static boolean isAndroidLayoutParam(Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace == null || !namespace.equals(ANDROID_URI)) {
            return false;
        }
        String name = attribute.getLocalName();
        return name != null && name.startsWith("layout_");
    }

    private static boolean isAlwaysValid(String name) {
        return name.equals("layout_width")
                || name.equals("layout_height")
                || name.startsWith("layout_margin");
    }

    private static String getLayoutClassName(Element element) {
        String tag = element.getTagName();
        if (tag == null) {
            return null;
        }

        if (tag.equals("view")) {
            String cls = element.getAttribute("class");
            if (cls != null && !cls.isEmpty()) {
                return simplifyClassName(cls);
            }
            return null;
        }

        if (tag.equals("include")
                || tag.equals("merge")
                || tag.equals("fragment")
                || tag.equals("requestFocus")) {
            return null;
        }

        return simplifyClassName(tag);
    }

    private static String simplifyClassName(String className) {
        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }

    private static Element getParentElement(Element element) {
        if (element.getParentNode() instanceof Element) {
            return (Element) element.getParentNode();
        }
        return null;
    }

    private static Set<String> newSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static class PendingAttr {
        final XmlContext context;
        final Element child;
        final Attr attribute;

        PendingAttr(XmlContext context, Element child, Attr attribute) {
            this.context = context;
            this.child = child;
            this.attribute = attribute;
        }
    }
}