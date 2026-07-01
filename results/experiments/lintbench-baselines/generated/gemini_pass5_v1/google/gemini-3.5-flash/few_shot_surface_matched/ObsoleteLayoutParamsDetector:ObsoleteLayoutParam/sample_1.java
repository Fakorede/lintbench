package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.LAYOUT_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final java.util.Set<String> REL_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_toLeftOf", "layout_toRightOf", "layout_above", "layout_below",
            "layout_alignBaseline", "layout_alignLeft", "layout_alignTop", "layout_alignRight", "layout_alignBottom",
            "layout_alignParentLeft", "layout_alignParentTop", "layout_alignParentRight", "layout_alignParentBottom",
            "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
            "layout_alignStart", "layout_alignEnd", "layout_alignParentStart", "layout_alignParentEnd",
            "layout_toStartOf", "layout_toEndOf"
    ));

    private static final java.util.Set<String> LIN_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_weight"
    ));

    private static final java.util.Set<String> GRID_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_column", "layout_row", "layout_columnSpan", "layout_rowSpan", "layout_columnWeight", "layout_rowWeight"
    ));

    private static final java.util.Set<String> ABS_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_x", "layout_y"
    ));

    private static final java.util.Set<String> GRAVITY_PARAMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout_gravity"
    ));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(ALL);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(ALL);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String namespaceUri = attribute.getNamespaceURI();
        if (namespaceUri != null && !ANDROID_URI.equals(namespaceUri)) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        if (!name.startsWith("layout_")) {
            return;
        }

        if ("layout_width".equals(name) || "layout_height".equals(name) || name.startsWith("layout_margin")) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        org.w3c.dom.Node parentNode = element.getParentNode();
        if (!(parentNode instanceof org.w3c.dom.Element)) {
            return;
        }

        org.w3c.dom.Element parent = (org.w3c.dom.Element) parentNode;
        String parentTag = parent.getTagName();
        if ("merge".equals(parentTag)) {
            return;
        }

        String simpleParent = getSimpleName(parentTag);
        String badParent = null;

        if ("LinearLayout".equals(simpleParent) || "TableRow".equals(simpleParent) || "TableLayout".equals(simpleParent)) {
            if (REL_PARAMS.contains(name)) {
                badParent = "LinearLayout";
            } else if (GRID_PARAMS.contains(name)) {
                badParent = "GridLayout";
            } else if (ABS_PARAMS.contains(name)) {
                badParent = "AbsoluteLayout";
            }
        } else if ("RelativeLayout".equals(simpleParent)) {
            if (LIN_PARAMS.contains(name)) {
                badParent = "LinearLayout";
            } else if (GRID_PARAMS.contains(name)) {
                badParent = "GridLayout";
            } else if (ABS_PARAMS.contains(name)) {
                badParent = "AbsoluteLayout";
            } else if (GRAVITY_PARAMS.contains(name)) {
                badParent = "LinearLayout/FrameLayout";
            }
        } else if ("FrameLayout".equals(simpleParent) || "ScrollView".equals(simpleParent) || "HorizontalScrollView".equals(simpleParent)) {
            if (REL_PARAMS.contains(name)) {
                badParent = "RelativeLayout";
            } else if (GRID_PARAMS.contains(name)) {
                badParent = "GridLayout";
            } else if (ABS_PARAMS.contains(name)) {
                badParent = "AbsoluteLayout";
            } else if (LIN_PARAMS.contains(name)) {
                badParent = "LinearLayout";
            }
        } else if ("GridLayout".equals(simpleParent)) {
            if (REL_PARAMS.contains(name)) {
                badParent = "RelativeLayout";
            } else if (ABS_PARAMS.contains(name)) {
                badParent = "AbsoluteLayout";
            } else if (LIN_PARAMS.contains(name)) {
                badParent = "LinearLayout";
            }
        } else if ("ConstraintLayout".equals(simpleParent)) {
            if (REL_PARAMS.contains(name)) {
                badParent = "RelativeLayout";
            } else if (GRID_PARAMS.contains(name)) {
                badParent = "GridLayout";
            } else if (ABS_PARAMS.contains(name)) {
                badParent = "AbsoluteLayout";
            } else if (LIN_PARAMS.contains(name)) {
                badParent = "LinearLayout";
            } else if (GRAVITY_PARAMS.contains(name)) {
                badParent = "LinearLayout/FrameLayout";
            }
        }

        if (badParent != null) {
            String message = String.format(
                    "Layout attribute `%1$s` is ignored in a `%2$s` (it is for `%3$s`)",
                    attribute.getName(), simpleParent, badParent
            );
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // No-op, handled in visitAttribute
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // No-op
    }

    private static String getSimpleName(String tag) {
        int lastDot = tag.lastIndexOf('.');
        if (lastDot != -1) {
            return tag.substring(lastDot + 1);
        }
        return tag;
    }
}