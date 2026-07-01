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
            String parentTagAttr = parent.getAttributeNS("http://schemas.android.com/tools", "parentTag");
            if (parentTagAttr == null || parentTagAttr.isEmpty()) {
                parentTagAttr = parent.getAttribute("tools:parentTag");
            }
            if (parentTagAttr != null && !parentTagAttr.isEmpty()) {
                parentTag = parentTagAttr;
            } else {
                return;
            }
        }

        int dot = parentTag.lastIndexOf('.');
        if (dot != -1) {
            parentTag = parentTag.substring(dot + 1);
        }

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

            if (parentTag.equals("RelativeLayout")) {
                if (name.equals("layout_gravity") || name.equals("layout_weight")) {
                    String message = String.format(
                            "Invalid layout parameter in a `RelativeLayout`: `android:%1$s`",
                            name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (parentTag.equals("LinearLayout")
                    || parentTag.equals("GridLayout")
                    || parentTag.equals("TableRow")
                    || parentTag.equals("TableLayout")) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `%1$s`: `android:%2$s`",
                            parentTag, name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            } else if (parentTag.equals("FrameLayout")
                    || parentTag.equals("ScrollView")
                    || parentTag.equals("HorizontalScrollView")) {
                if (isRelativeLayoutParam(name)) {
                    String message = String.format(
                            "Invalid layout parameter in a `%1$s`: `android:%2$s`",
                            parentTag, name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                } else if (name.equals("layout_weight")) {
                    String message = String.format(
                            "Invalid layout parameter in a `%1$s`: `android:%2$s`",
                            parentTag, name);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
        }
    }

    private static boolean isRelativeLayoutParam(String name) {
        return name.equals("layout_alignLeft")
                || name.equals("layout_alignRight")
                || name.equals("layout_alignTop")
                || name.equals("layout_alignBottom")
                || name.equals("layout_alignParentLeft")
                || name.equals("layout_alignParentRight")
                || name.equals("layout_alignParentTop")
                || name.equals("layout_alignParentBottom")
                || name.equals("layout_centerHorizontal")
                || name.equals("layout_centerVertical")
                || name.equals("layout_centerInParent")
                || name.equals("layout_toLeftOf")
                || name.equals("layout_toRightOf")
                || name.equals("layout_above")
                || name.equals("layout_below")
                || name.equals("layout_alignBaseline")
                || name.equals("layout_alignStart")
                || name.equals("layout_alignEnd")
                || name.equals("layout_alignParentStart")
                || name.equals("layout_alignParentEnd")
                || name.equals("layout_toStartOf")
                || name.equals("layout_toEndOf");
    }
}