package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final Set<String> RELATIVE_LAYOUT_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "layout_alignParentTop", "layout_alignParentBottom", "layout_alignParentLeft", "layout_alignParentRight",
            "layout_alignParentStart", "layout_alignParentEnd", "layout_centerHorizontal", "layout_centerVertical",
            "layout_centerInParent", "layout_toLeftOf", "layout_toRightOf", "layout_toStartOf", "layout_toEndOf",
            "layout_above", "layout_below", "layout_alignBaseline", "layout_alignTop", "layout_alignBottom",
            "layout_alignLeft", "layout_alignRight", "layout_alignStart", "layout_alignEnd", "layout_alignWithParentIfMissing"
    )));

    private static final Set<String> GRID_LAYOUT_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "layout_row", "layout_rowSpan", "layout_column", "layout_columnSpan", "layout_rowWeight", "layout_columnWeight"
    )));

    private static final Set<String> TABLE_ROW_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "layout_column", "layout_span"
    )));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

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

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode instanceof Attr) {
                Attr attr = (Attr) attrNode;
                String localName = attr.getLocalName();
                if (localName != null && localName.startsWith("layout_")) {
                    if (isObsolete(parentTag, localName)) {
                        String message = String.format(
                                "The `%s` layout parameter is useless on a child of `%s`",
                                localName, parentTag);
                        context.report(ISSUE, attr, context.getLocation(attr), message);
                    }
                }
            }
        }
    }

    private static boolean isObsolete(String parentTag, String paramName) {
        String shortParent = parentTag;
        int lastDot = parentTag.lastIndexOf('.');
        if (lastDot != -1) {
            shortParent = parentTag.substring(lastDot + 1);
        }

        if (shortParent.equals("LinearLayout")) {
            return RELATIVE_LAYOUT_PARAMS.contains(paramName)
                    || GRID_LAYOUT_PARAMS.contains(paramName)
                    || TABLE_ROW_PARAMS.contains(paramName);
        }

        if (shortParent.equals("RelativeLayout")) {
            return "layout_gravity".equals(paramName)
                    || "layout_weight".equals(paramName)
                    || GRID_LAYOUT_PARAMS.contains(paramName)
                    || TABLE_ROW_PARAMS.contains(paramName);
        }

        if (shortParent.equals("FrameLayout")
                || shortParent.equals("ScrollView")
                || shortParent.equals("HorizontalScrollView")
                || shortParent.equals("NestedScrollView")) {
            return "layout_weight".equals(paramName)
                    || RELATIVE_LAYOUT_PARAMS.contains(paramName)
                    || GRID_LAYOUT_PARAMS.contains(paramName)
                    || TABLE_ROW_PARAMS.contains(paramName);
        }

        if (shortParent.equals("ConstraintLayout")) {
            return "layout_gravity".equals(paramName)
                    || "layout_weight".equals(paramName)
                    || RELATIVE_LAYOUT_PARAMS.contains(paramName)
                    || GRID_LAYOUT_PARAMS.contains(paramName)
                    || TABLE_ROW_PARAMS.contains(paramName);
        }

        if (shortParent.equals("CoordinatorLayout")) {
            return "layout_weight".equals(paramName)
                    || RELATIVE_LAYOUT_PARAMS.contains(paramName)
                    || GRID_LAYOUT_PARAMS.contains(paramName)
                    || TABLE_ROW_PARAMS.contains(paramName);
        }

        if (shortParent.equals("TableRow")) {
            return RELATIVE_LAYOUT_PARAMS.contains(paramName)
                    || "layout_row".equals(paramName)
                    || "layout_rowSpan".equals(paramName)
                    || "layout_columnSpan".equals(paramName)
                    || "layout_rowWeight".equals(paramName)
                    || "layout_columnWeight".equals(paramName);
        }

        return false;
    }
}