package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    private static final String ATTR_LAYOUT_COLUMN = "layout_column";
    private static final String ATTR_LAYOUT_SPAN = "layout_span";
    private static final String ATTR_LAYOUT_ROW = "layout_row";
    private static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";
    private static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";

    private static final List<String> RELATIVE_LAYOUT_ATTRS = Arrays.asList(
            "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentTop", "layout_alignParentBottom",
            "layout_alignParentStart", "layout_alignParentEnd", "layout_centerHorizontal", "layout_centerVertical",
            "layout_centerInParent", "layout_alignLeft", "layout_alignRight", "layout_alignTop", "layout_alignBottom",
            "layout_alignStart", "layout_alignEnd", "layout_alignBaseline", "layout_toLeftOf", "layout_toRightOf",
            "layout_toStartOf", "layout_toEndOf", "layout_above", "layout_below"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> attrs = new ArrayList<>();
        attrs.add(ATTR_LAYOUT_WEIGHT);
        attrs.add(ATTR_LAYOUT_GRAVITY);
        attrs.addAll(RELATIVE_LAYOUT_ATTRS);
        attrs.add(ATTR_LAYOUT_COLUMN);
        attrs.add(ATTR_LAYOUT_SPAN);
        attrs.add(ATTR_LAYOUT_ROW);
        attrs.add(ATTR_LAYOUT_ROW_SPAN);
        attrs.add(ATTR_LAYOUT_COLUMN_SPAN);
        return attrs;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        String simpleParentTag = parentTag.substring(parentTag.lastIndexOf('.') + 1);

        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
            if (attrName.contains(":")) {
                attrName = attrName.substring(attrName.indexOf(':') + 1);
            }
        }

        boolean isObsolete = false;
        String suggestedParent = null;

        if (simpleParentTag.equals("LinearLayout")) {
            if (RELATIVE_LAYOUT_ATTRS.contains(attrName)) {
                isObsolete = true;
                suggestedParent = "RelativeLayout";
            } else if (attrName.equals(ATTR_LAYOUT_COLUMN) || attrName.equals(ATTR_LAYOUT_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_ROW) || attrName.equals(ATTR_LAYOUT_ROW_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_COLUMN_SPAN)) {
                isObsolete = true;
                suggestedParent = "GridLayout or TableLayout";
            }
        } else if (simpleParentTag.equals("RelativeLayout")) {
            if (attrName.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
                suggestedParent = "LinearLayout";
            } else if (attrName.equals(ATTR_LAYOUT_GRAVITY)) {
                isObsolete = true;
                suggestedParent = "LinearLayout or FrameLayout";
            } else if (attrName.equals(ATTR_LAYOUT_COLUMN) || attrName.equals(ATTR_LAYOUT_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_ROW) || attrName.equals(ATTR_LAYOUT_ROW_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_COLUMN_SPAN)) {
                isObsolete = true;
                suggestedParent = "GridLayout or TableLayout";
            }
        } else if (simpleParentTag.equals("FrameLayout")) {
            if (attrName.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
                suggestedParent = "LinearLayout";
            } else if (RELATIVE_LAYOUT_ATTRS.contains(attrName)) {
                isObsolete = true;
                suggestedParent = "RelativeLayout";
            } else if (attrName.equals(ATTR_LAYOUT_COLUMN) || attrName.equals(ATTR_LAYOUT_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_ROW) || attrName.equals(ATTR_LAYOUT_ROW_SPAN) ||
                       attrName.equals(ATTR_LAYOUT_COLUMN_SPAN)) {
                isObsolete = true;
                suggestedParent = "GridLayout or TableLayout";
            }
        } else if (simpleParentTag.equals("GridLayout")) {
            if (attrName.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
                suggestedParent = "LinearLayout";
            } else if (RELATIVE_LAYOUT_ATTRS.contains(attrName)) {
                isObsolete = true;
                suggestedParent = "RelativeLayout";
            }
        } else if (simpleParentTag.equals("ScrollView") || simpleParentTag.equals("HorizontalScrollView")) {
            if (attrName.equals(ATTR_LAYOUT_WEIGHT)) {
                isObsolete = true;
                suggestedParent = "LinearLayout";
            } else if (RELATIVE_LAYOUT_ATTRS.contains(attrName)) {
                isObsolete = true;
                suggestedParent = "RelativeLayout";
            }
        }

        if (isObsolete) {
            String message = String.format(
                    "The attribute `android:%s` is not useful on a child of `%s`%s",
                    attrName,
                    parentTag,
                    suggestedParent != null ? " (did you mean to use `" + suggestedParent + "`?)" : ""
            );
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handled via visitAttribute for precision, but required by interface
    }

    @Override
    public void afterCheckRootProject(@NonNull XmlContext context) {
        // Optional cleanup after analysis completion
    }
}