package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

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

    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
        "layout_alignParentLeft", "layout_alignParentTop", "layout_alignParentRight", "layout_alignParentBottom",
        "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
        "layout_toLeftOf", "layout_toRightOf", "layout_above", "layout_below",
        "layout_alignBaseline", "layout_alignLeft", "layout_alignTop", "layout_alignRight", "layout_alignBottom",
        "layout_alignStart", "layout_alignEnd", "layout_toStartOf", "layout_toEndOf",
        "layout_alignParentStart", "layout_alignParentEnd", "layout_alignWithParentIfMissing"
    ));

    private static final Set<String> KNOWN_PARENTS = new HashSet<>(Arrays.asList(
        "LinearLayout", "RelativeLayout", "FrameLayout", "ConstraintLayout", "CoordinatorLayout", "TableLayout", "TableRow", "GridLayout"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();
        String parentShortName = getShortName(parentTag);

        if (!KNOWN_PARENTS.contains(parentShortName)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String namespace = attribute.getNamespaceURI();
            if (SdkConstants.ANDROID_URI.equals(namespace)) {
                String name = attribute.getLocalName();
                if (name != null && name.startsWith("layout_")) {
                    checkAttribute(context, attribute, name, parentShortName);
                }
            }
        }
    }

    private void checkAttribute(XmlContext context, Attr attribute, String name, String parentTag) {
        String expectedParent = null;

        if (RELATIVE_LAYOUT_PARAMS.contains(name)) {
            if (!"RelativeLayout".equals(parentTag)) {
                expectedParent = "RelativeLayout";
            }
        } else if (name.startsWith("layout_constraint")) {
            if (!"ConstraintLayout".equals(parentTag)) {
                expectedParent = "ConstraintLayout";
            }
        } else if (name.startsWith("layout_anchor") || "layout_behavior".equals(name) || "layout_keyline".equals(name)) {
            if (!"CoordinatorLayout".equals(parentTag)) {
                expectedParent = "CoordinatorLayout";
            }
        } else if ("layout_gravity".equals(name)) {
            if ("RelativeLayout".equals(parentTag) || "ConstraintLayout".equals(parentTag)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("`%s` is not valid in a `%s` parent", name, parentTag)
                );
            }
        } else if ("layout_weight".equals(name)) {
            if ("RelativeLayout".equals(parentTag) || "FrameLayout".equals(parentTag) || "ConstraintLayout".equals(parentTag) || "CoordinatorLayout".equals(parentTag) || "GridLayout".equals(parentTag)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("`%s` is not valid in a `%s` parent", name, parentTag)
                );
            }
        }

        if (expectedParent != null) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                String.format("Invalid layout param `%s` in a `%s` parent (expected `%s`)", name, parentTag, expectedParent)
            );
        }
    }

    private static String getShortName(String tagName) {
        int index = tagName.lastIndexOf('.');
        return index != -1 ? tagName.substring(index + 1) : tagName;
    }
}