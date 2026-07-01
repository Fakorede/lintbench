package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_toRightOf", "layout_toLeftOf", "layout_above", "layout_below",
            "layout_alignBaseline", "layout_alignBottom", "layout_alignLeft",
            "layout_alignRight", "layout_alignTop", "layout_alignParentBottom",
            "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentTop",
            "layout_centerHorizontal", "layout_centerInParent", "layout_centerVertical",
            "layout_alignStart", "layout_alignEnd", "layout_alignParentStart",
            "layout_alignParentEnd", "layout_toStartOf", "layout_toEndOf"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !namespace.equals(com.android.SdkConstants.ANDROID_URI)) {
            if (!namespace.equals(com.android.SdkConstants.AUTO_URI)) {
                return;
            }
        }

        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        String message = null;

        if (RELATIVE_LAYOUT_PARAMS.contains(name)) {
            if (!isRelativeLayout(parentTag) && isKnownLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (only valid for `RelativeLayout`)", name, parentTag);
            }
        } else if ("layout_weight".equals(name)) {
            if (!isLinearLayout(parentTag) && isKnownLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (only valid for `LinearLayout`)", name, parentTag);
            }
        } else if ("layout_gravity".equals(name)) {
            if (isRelativeLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (RelativeLayout does not support gravity on children)", name, parentTag);
            } else if (isConstraintLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (ConstraintLayout does not support layout_gravity)", name, parentTag);
            }
        } else if (name.startsWith("layout_constraint")) {
            if (!isConstraintLayout(parentTag) && isKnownLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (only valid for `ConstraintLayout`)", name, parentTag);
            }
        } else if ("layout_row".equals(name) || "layout_column".equals(name) || "layout_rowSpan".equals(name) || "layout_columnSpan".equals(name)) {
            if (!isGridLayout(parentTag) && isKnownLayout(parentTag)) {
                message = String.format("Invalid layout param `%s` for parent `%s` (only valid for `GridLayout`)", name, parentTag);
            }
        }

        if (message != null) {
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Required override
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Required override
    }

    private static boolean isKnownLayout(String parentTag) {
        return isLinearLayout(parentTag)
                || isRelativeLayout(parentTag)
                || isFrameLayout(parentTag)
                || isConstraintLayout(parentTag)
                || isGridLayout(parentTag);
    }

    private static boolean isLinearLayout(String parent) {
        return "LinearLayout".equals(parent)
                || "android.widget.LinearLayout".equals(parent)
                || "RadioGroup".equals(parent)
                || "android.widget.RadioGroup".equals(parent)
                || "TableLayout".equals(parent)
                || "android.widget.TableLayout".equals(parent)
                || "TableRow".equals(parent)
                || "android.widget.TableRow".equals(parent);
    }

    private static boolean isRelativeLayout(String parent) {
        return "RelativeLayout".equals(parent)
                || "android.widget.RelativeLayout".equals(parent)
                || "PercentRelativeLayout".equals(parent)
                || "android.support.percent.PercentRelativeLayout".equals(parent)
                || "androidx.percentlayout.widget.PercentRelativeLayout".equals(parent);
    }

    private static boolean isFrameLayout(String parent) {
        return "FrameLayout".equals(parent)
                || "android.widget.FrameLayout".equals(parent)
                || "ScrollView".equals(parent)
                || "android.widget.ScrollView".equals(parent)
                || "HorizontalScrollView".equals(parent)
                || "android.widget.HorizontalScrollView".equals(parent)
                || "NestedScrollView".equals(parent)
                || "androidx.core.widget.NestedScrollView".equals(parent)
                || "CardView".equals(parent)
                || "androidx.cardview.widget.CardView".equals(parent);
    }

    private static boolean isConstraintLayout(String parent) {
        return "ConstraintLayout".equals(parent)
                || "androidx.constraintlayout.widget.ConstraintLayout".equals(parent)
                || "android.support.constraint.ConstraintLayout".equals(parent);
    }

    private static boolean isGridLayout(String parent) {
        return "GridLayout".equals(parent)
                || "android.widget.GridLayout".equals(parent)
                || "androidx.gridlayout.widget.GridLayout".equals(parent);
    }
}