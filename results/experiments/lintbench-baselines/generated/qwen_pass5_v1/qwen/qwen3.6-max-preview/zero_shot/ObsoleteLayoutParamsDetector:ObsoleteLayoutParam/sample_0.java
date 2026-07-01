package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        if (SdkConstants.TAG_MERGE.equals(parentTag)) {
            return;
        }

        String parentSimple = getSimpleClassName(parentTag);

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (!(attrNode instanceof Attr)) {
                continue;
            }

            Attr attr = (Attr) attrNode;
            if (!SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }

            String localName = attr.getLocalName();
            if (!localName.startsWith("layout_")) {
                continue;
            }
            if (localName.equals("layout_width") || localName.equals("layout_height")) {
                continue;
            }
            if (localName.startsWith("layout_margin")) {
                continue;
            }

            if (!isAllowed(parentSimple, localName)) {
                String message = String.format(
                        "Invalid layout param in a `%1$s`: `%2$s`", parentTag, localName);
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    @NotNull
    private static String getSimpleClassName(@NotNull String tagName) {
        int dotIndex = tagName.lastIndexOf('.');
        return dotIndex != -1 ? tagName.substring(dotIndex + 1) : tagName;
    }

    private static boolean isAllowed(@NotNull String parentSimple, @NotNull String attrName) {
        switch (parentSimple) {
            case "LinearLayout":
            case "TableLayout":
                return attrName.equals("layout_gravity") || attrName.equals("layout_weight");
            case "FrameLayout":
            case "DrawerLayout":
                return attrName.equals("layout_gravity");
            case "RelativeLayout":
                return attrName.startsWith("layout_align") ||
                       attrName.startsWith("layout_to") ||
                       attrName.startsWith("layout_center") ||
                       attrName.equals("layout_above") ||
                       attrName.equals("layout_below");
            case "GridLayout":
                return attrName.equals("layout_row") ||
                       attrName.equals("layout_column") ||
                       attrName.equals("layout_rowSpan") ||
                       attrName.equals("layout_columnSpan") ||
                       attrName.equals("layout_rowWeight") ||
                       attrName.equals("layout_columnWeight") ||
                       attrName.equals("layout_gravity");
            case "ConstraintLayout":
                return attrName.startsWith("layout_constraint") ||
                       attrName.startsWith("layout_goneMargin") ||
                       attrName.startsWith("layout_editor_") ||
                       attrName.equals("layout_marginBaseline");
            case "TableRow":
                return attrName.equals("layout_column") ||
                       attrName.equals("layout_span") ||
                       attrName.equals("layout_gravity");
            case "CoordinatorLayout":
                return attrName.equals("layout_anchor") ||
                       attrName.equals("layout_anchorGravity") ||
                       attrName.equals("layout_behavior") ||
                       attrName.equals("layout_dodgeInsetEdges") ||
                       attrName.equals("layout_insetEdge") ||
                       attrName.equals("layout_keyline") ||
                       attrName.equals("layout_gravity");
            case "AbsoluteLayout":
                return attrName.equals("layout_x") || attrName.equals("layout_y");
            default:
                return true;
        }
    }
}