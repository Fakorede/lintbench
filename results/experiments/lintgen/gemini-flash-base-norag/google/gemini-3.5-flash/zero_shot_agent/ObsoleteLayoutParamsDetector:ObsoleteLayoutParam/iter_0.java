package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move view "
                    + "code around without updating the layout params. This will cause useless "
                    + "attribute processing at runtime, and is misleading for others reading the "
                    + "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();
        String parentType = getStandardParentType(parentTag);

        if (parentType == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String localName = attribute.getLocalName();
            if (localName == null || !localName.startsWith("layout_")) {
                continue;
            }

            String errorMessage = getObsoleteErrorMessage(parentType, localName);
            if (errorMessage != null) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        errorMessage
                );
            }
        }
    }

    private static String getStandardParentType(String tag) {
        if (isParent(tag, "LinearLayout", "RadioGroup")) {
            return "LinearLayout";
        }
        if (isParent(tag, "RelativeLayout")) {
            return "RelativeLayout";
        }
        if (isParent(tag, "FrameLayout", "ScrollView", "HorizontalScrollView", "CardView", "NestedScrollView")) {
            return "FrameLayout";
        }
        if (isParent(tag, "ConstraintLayout")) {
            return "ConstraintLayout";
        }
        if (isParent(tag, "GridLayout")) {
            return "GridLayout";
        }
        if (isParent(tag, "CoordinatorLayout")) {
            return "CoordinatorLayout";
        }
        if (isParent(tag, "DrawerLayout")) {
            return "DrawerLayout";
        }
        if (isParent(tag, "TableRow")) {
            return "TableRow";
        }
        if (isParent(tag, "TableLayout")) {
            return "TableLayout";
        }
        return null;
    }

    private static boolean isParent(String tag, String... targets) {
        for (String target : targets) {
            if (tag.equals(target)) {
                return true;
            }
            if (target.indexOf('.') == -1 && tag.endsWith("." + target)) {
                return true;
            }
        }
        return false;
    }

    private static String getObsoleteErrorMessage(String parentType, String localName) {
        if ("layout_weight".equals(localName)) {
            if (!"LinearLayout".equals(parentType) && !"TableRow".equals(parentType)) {
                return "Invalid layout param: `layout_weight` can only be used with a `LinearLayout` parent";
            }
        }

        if ("layout_gravity".equals(localName)) {
            if ("RelativeLayout".equals(parentType)) {
                return "Invalid layout param: `layout_gravity` has no effect in `RelativeLayout`";
            }
            if ("ConstraintLayout".equals(parentType)) {
                return "Invalid layout param: `layout_gravity` has no effect in `ConstraintLayout`";
            }
        }

        if (isRelativeLayoutParam(localName)) {
            if (!"RelativeLayout".equals(parentType)) {
                return String.format("Invalid layout param: `%s` can only be used with a `RelativeLayout` parent", localName);
            }
        }

        if (isConstraintLayoutParam(localName)) {
            if (!"ConstraintLayout".equals(parentType)) {
                return String.format("Invalid layout param: `%s` can only be used with a `ConstraintLayout` parent", localName);
            }
        }

        if (isGridLayoutParam(localName)) {
            if (!"GridLayout".equals(parentType)) {
                if ("TableRow".equals(parentType) && ("layout_column".equals(localName) || "layout_span".equals(localName))) {
                    return null;
                }
                return String.format("Invalid layout param: `%s` can only be used with a `GridLayout` parent", localName);
            }
        }

        return null;
    }

    private static boolean isRelativeLayoutParam(String name) {
        return (name.startsWith("layout_align") && !name.equals("layout_anchorGravity"))
                || name.equals("layout_below")
                || name.equals("layout_above")
                || (name.startsWith("layout_to") && name.endsWith("Of"))
                || name.startsWith("layout_center");
    }

    private static boolean isConstraintLayoutParam(String name) {
        return name.startsWith("layout_constraint");
    }

    private static boolean isGridLayoutParam(String name) {
        return name.equals("layout_row")
                || name.equals("layout_column")
                || name.equals("layout_rowSpan")
                || name.equals("layout_columnSpan")
                || name.equals("layout_rowWeight")
                || name.equals("layout_columnWeight");
    }
}