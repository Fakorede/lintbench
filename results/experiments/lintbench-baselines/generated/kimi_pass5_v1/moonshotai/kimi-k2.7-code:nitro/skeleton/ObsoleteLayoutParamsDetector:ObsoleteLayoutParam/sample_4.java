package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout param is not defined for the given layout, meaning it has no effect. "
                            + "This usually happens when you change the parent layout or move view code around without updating the layout params. "
                            + "This will cause useless attribute processing at runtime, and is misleading for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Set<String> BASE_PARAMS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("layout_width", "layout_height")));

    private static final Set<String> MARGIN_PARAMS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "layout_margin",
                                    "layout_marginLeft",
                                    "layout_marginRight",
                                    "layout_marginTop",
                                    "layout_marginBottom",
                                    "layout_marginStart",
                                    "layout_marginEnd",
                                    "layout_marginHorizontal",
                                    "layout_marginVertical")));

    private static final Map<String, Set<String>> VALID_PARAMS = new HashMap<>();

    static {
        register("FrameLayout", "layout_gravity");

        register("LinearLayout", "layout_weight", "layout_gravity");
        register("LinearLayoutCompat", "layout_weight", "layout_gravity");

        register(
                "RelativeLayout",
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
                "layout_toStartOf");

        register(
                "GridLayout",
                "layout_gravity",
                "layout_column",
                "layout_columnSpan",
                "layout_columnWeight",
                "layout_row",
                "layout_rowSpan",
                "layout_rowWeight");

        register("TableLayout", "layout_weight", "layout_gravity", "layout_column", "layout_span");
        register("TableRow", "layout_weight", "layout_gravity", "layout_column", "layout_span");
        register("RadioGroup", "layout_weight", "layout_gravity");
    }

    private static void register(String tag, String... extras) {
        Set<String> set = new HashSet<>(BASE_PARAMS);
        set.addAll(MARGIN_PARAMS);
        Collections.addAll(set, extras);
        VALID_PARAMS.put(tag, set);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Detector.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (!ANDROID_URI.equals(namespace)) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Element parent = getParentElement(element);
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if (parentTag == null || parentTag.isEmpty()) {
            return;
        }

        String simpleParentTag = getSimpleName(parentTag);
        Set<String> validParams = VALID_PARAMS.get(simpleParentTag);
        if (validParams == null) {
            return;
        }

        if (!validParams.contains(name)) {
            String message =
                    String.format("Invalid layout param in a `%1$s`: `%2$s`", simpleParentTag, name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // This detector works on attributes; no per-element work is needed.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No cleanup required.
    }

    private static Element getParentElement(Element element) {
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            return (Element) parent;
        }
        return null;
    }

    private static String getSimpleName(String tag) {
        int index = tag.lastIndexOf('.');
        return index == -1 ? tag : tag.substring(index + 1);
    }
}