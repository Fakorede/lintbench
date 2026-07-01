package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

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
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT_PREFIX = "layout_";

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String FRAME_LAYOUT = "FrameLayout";
    private static final String RELATIVE_LAYOUT = "RelativeLayout";
    private static final String TABLE_LAYOUT = "TableLayout";
    private static final String TABLE_ROW = "TableRow";
    private static final String GRID_LAYOUT = "GridLayout";
    private static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";

    private static final String[] COMMON_PARAMS = {
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
        "layout_marginVertical"
    };

    private static final java.util.Map<String, java.util.Set<String>> LAYOUT_PARAMS =
            createLayoutParams();

    private static java.util.Map<String, java.util.Set<String>> createLayoutParams() {
        java.util.Map<String, java.util.Set<String>> map =
                new java.util.HashMap<String, java.util.Set<String>>();

        add(map, LINEAR_LAYOUT, COMMON_PARAMS);
        add(map, LINEAR_LAYOUT,
                "layout_weight",
                "layout_gravity");

        add(map, FRAME_LAYOUT, COMMON_PARAMS);
        add(map, FRAME_LAYOUT,
                "layout_gravity");

        add(map, RELATIVE_LAYOUT, COMMON_PARAMS);
        add(map, RELATIVE_LAYOUT,
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

        add(map, TABLE_LAYOUT, COMMON_PARAMS);
        add(map, TABLE_LAYOUT,
                "layout_weight");

        add(map, TABLE_ROW, COMMON_PARAMS);
        add(map, TABLE_ROW,
                "layout_weight",
                "layout_column",
                "layout_span",
                "layout_gravity");

        add(map, GRID_LAYOUT, COMMON_PARAMS);
        add(map, GRID_LAYOUT,
                "layout_gravity",
                "layout_column",
                "layout_columnSpan",
                "layout_row",
                "layout_rowSpan");

        add(map, ABSOLUTE_LAYOUT, COMMON_PARAMS);
        add(map, ABSOLUTE_LAYOUT,
                "layout_x",
                "layout_y");

        map.put("ScrollView", map.get(FRAME_LAYOUT));
        map.put("HorizontalScrollView", map.get(FRAME_LAYOUT));
        map.put("RadioGroup", map.get(LINEAR_LAYOUT));

        return java.util.Collections.unmodifiableMap(map);
    }

    private static void add(java.util.Map<String, java.util.Set<String>> map, String tag,
            String... attrs) {
        java.util.Set<String> set = map.get(tag);
        if (set == null) {
            set = new java.util.HashSet<String>();
            map.put(tag, set);
        }
        for (String attr : attrs) {
            set.add(attr);
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (name == null || !name.startsWith(LAYOUT_PREFIX)) {
            return;
        }

        org.w3c.dom.Element child = attribute.getOwnerElement();
        if (child == null) {
            return;
        }

        org.w3c.dom.Node parentNode = child.getParentNode();
        if (!(parentNode instanceof org.w3c.dom.Element)) {
            return;
        }

        String parentTag = ((org.w3c.dom.Element) parentNode).getTagName();
        java.util.Set<String> allowed = LAYOUT_PARAMS.get(parentTag);
        if (allowed == null) {
            return;
        }

        if (allowed.contains(name)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Layout attribute " + name + " is not defined for layout " + parentTag
                        + "; it has no effect");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // Obsolete layout params are reported per attribute in visitAttribute.
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }
}