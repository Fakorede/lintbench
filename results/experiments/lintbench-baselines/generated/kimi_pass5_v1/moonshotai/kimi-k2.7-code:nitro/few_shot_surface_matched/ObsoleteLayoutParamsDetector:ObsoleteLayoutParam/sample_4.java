package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or "
                            + "move view code around without updating the layout params. This will "
                            + "cause useless attribute processing at runtime, and is misleading "
                            + "for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, Set<String>> VALID_PARAMS;

    static {
        Map<String, Set<String>> map = new HashMap<>();

        Set<String> commonWithMargins =
                set(
                        "layout_width",
                        "layout_height",
                        "layout_margin",
                        "layout_marginLeft",
                        "layout_marginTop",
                        "layout_marginRight",
                        "layout_marginBottom",
                        "layout_marginStart",
                        "layout_marginEnd",
                        "layout_marginHorizontal",
                        "layout_marginVertical");

        map.put("LinearLayout", add(commonWithMargins, "layout_weight", "layout_gravity"));
        map.put("FrameLayout", add(commonWithMargins, "layout_gravity"));
        map.put(
                "RelativeLayout",
                add(
                        commonWithMargins,
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
                add(
                        commonWithMargins,
                        "layout_row",
                        "layout_rowSpan",
                        "layout_column",
                        "layout_columnSpan",
                        "layout_gravity"));
        map.put("TableRow", add(commonWithMargins, "layout_weight", "layout_column"));
        map.put("AbsoluteLayout", set("layout_width", "layout_height", "layout_x", "layout_y"));
        map.put(
                "CoordinatorLayout",
                add(
                        commonWithMargins,
                        "layout_behavior",
                        "layout_anchor",
                        "layout_anchorGravity",
                        "layout_keyline",
                        "layout_dodgeInsetEdges",
                        "layout_insetEdge",
                        "layout_scrollFlags",
                        "layout_scrollInterpolator"));

        VALID_PARAMS = Collections.unmodifiableMap(map);
    }

    @Nullable private Map<Element, String> mParentTagMap;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mParentTagMap == null) {
            mParentTagMap = new IdentityHashMap<>();
        }
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            mParentTagMap.put(element, ((Element) parent).getTagName());
        } else {
            mParentTagMap.put(element, null);
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        String parentTag = getParentTag(owner);
        if (parentTag == null || "merge".equals(parentTag)) {
            return;
        }

        Set<String> valid = getValidLayoutParams(parentTag);
        if (valid == null) {
            return;
        }

        if (!valid.contains(name)) {
            String message =
                    String.format("Invalid layout param in a `%1$s`: `%2$s`", parentTag, name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mParentTagMap != null) {
            mParentTagMap.clear();
        }
    }

    @Nullable
    private String getParentTag(@NonNull Element element) {
        String parentTag = mParentTagMap != null ? mParentTagMap.get(element) : null;
        if (parentTag == null) {
            Node parent = element.getParentNode();
            if (parent instanceof Element) {
                parentTag = ((Element) parent).getTagName();
            }
        }

        if ("view".equals(parentTag)) {
            Node grandparent = element.getParentNode().getParentNode();
            if (grandparent instanceof Element) {
                parentTag = ((Element) grandparent).getTagName();
            } else {
                return null;
            }
        }

        return parentTag;
    }

    @Nullable
    private static Set<String> getValidLayoutParams(@NonNull String parentTag) {
        Set<String> params = VALID_PARAMS.get(parentTag);
        if (params != null) {
            return params;
        }
        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            return VALID_PARAMS.get(parentTag.substring(dot + 1));
        }
        return null;
    }

    @SafeVarargs
    private static Set<String> add(Set<String> base, String... extra) {
        Set<String> result = new HashSet<>(base);
        Collections.addAll(result, extra);
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> set(String... values) {
        Set<String> set = new HashSet<>(values.length);
        Collections.addAll(set, values);
        return Collections.unmodifiableSet(set);
    }
}