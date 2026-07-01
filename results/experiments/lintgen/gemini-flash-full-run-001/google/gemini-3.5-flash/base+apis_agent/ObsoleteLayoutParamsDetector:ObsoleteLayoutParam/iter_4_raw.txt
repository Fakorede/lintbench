package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
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
        if (parentTag.equals("merge")) {
            parentTag = parent.getAttributeNS("http://schemas.android.com/tools", "parentTag");
            if (parentTag == null || parentTag.isEmpty()) {
                return;
            }
        }

        String parentClass = parentTag;
        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            parentTag = parentTag.substring(dot + 1);
        }

        boolean isGridLayout = parentTag.equals("GridLayout")
                || parentClass.endsWith(".GridLayout");
        boolean isRelativeLayout = parentTag.equals("RelativeLayout")
                || parentClass.endsWith(".RelativeLayout");
        boolean isLinearLayout = parentTag.equals("LinearLayout")
                || parentClass.endsWith(".LinearLayout");
        boolean isFrameLayout = parentTag.equals("FrameLayout")
                || parentClass.endsWith(".FrameLayout")
                || parentTag.equals("ScrollView")
                || parentClass.endsWith(".ScrollView")
                || parentTag.equals("HorizontalScrollView")
                || parentClass.endsWith(".HorizontalScrollView");
        boolean isTableLayout = parentTag.equals("TableLayout")
                || parentClass.endsWith(".TableLayout");
        boolean isTableRow = parentTag.equals("TableRow")
                || parentClass.endsWith(".TableRow");

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                String nodeName = attribute.getNodeName();
                if (nodeName != null && nodeName.startsWith("android:layout_")) {
                    name = nodeName.substring("android:".length());
                } else {
                    continue;
                }
            } else if (!name.startsWith("layout_")) {
                continue;
            }

            String namespace = attribute.getNamespaceURI();
            if (namespace != null) {
                if (!"http://schemas.android.com/apk/res/android".equals(namespace)) {
                    continue;
                }
            } else {
                String nodeName = attribute.getNodeName();
                if (nodeName == null || !nodeName.startsWith("android:layout_")) {
                    continue;
                }
            }

            if (isRelativeLayout) {
                if ("layout_gravity".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `RelativeLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                } else if ("layout_weight".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `RelativeLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (isLinearLayout) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `LinearLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (isFrameLayout) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `FrameLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                } else if ("layout_weight".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `FrameLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (isTableLayout) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `TableLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                } else if ("layout_width".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `TableLayout`: `android:%1$s` (always MATCH_PARENT)",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (isTableRow) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `TableRow`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                } else if ("layout_width".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `TableRow`: `android:%1$s` (always MATCH_PARENT)",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }

            if (!isGridLayout) {
                if ("layout_row".equals(name)
                        || "layout_column".equals(name)
                        || "layout_rowSpan".equals(name)
                        || "layout_columnSpan".equals(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `%1$s`: `android:%2$s`",
                            parentTag, name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
        }
    }

    private static boolean isRelativeLayoutParam(String name) {
        return "layout_alignLeft".equals(name)
                || "layout_alignRight".equals(name)
                || "layout_alignTop".equals(name)
                || "layout_alignBottom".equals(name)
                || "layout_alignParentLeft".equals(name)
                || "layout_alignParentRight".equals(name)
                || "layout_alignParentTop".equals(name)
                || "layout_alignParentBottom".equals(name)
                || "layout_centerHorizontal".equals(name)
                || "layout_centerVertical".equals(name)
                || "layout_centerInParent".equals(name)
                || "layout_toLeftOf".equals(name)
                || "layout_toRightOf".equals(name)
                || "layout_above".equals(name)
                || "layout_below".equals(name)
                || "layout_alignBaseline".equals(name)
                || "layout_alignStart".equals(name)
                || "layout_alignEnd".equals(name)
                || "layout_alignParentStart".equals(name)
                || "layout_alignParentEnd".equals(name)
                || "layout_toStartOf".equals(name)
                || "layout_toEndOf".equals(name)
                || "layout_alignWithParentIfMissing".equals(name);
    }
}