package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NegativeMarginDetector extends ResourceXmlDetector {

    private static final String ISSUE_ID = "NegativeMargin";

    private static final String EXPLANATION =
            "Margin values should be positive. Negative values are generally a sign that you are "
                    + "making assumptions about views surrounding the current one, or may be tempted "
                    + "to turn off child clipping to allow a view to escape its parent. Turning off "
                    + "child clipping to do this not only leads to poor graphical performance, it "
                    + "also results in wrong touch event handling since touch events are based "
                    + "strictly on a chain of parent-rect hit tests. Finally, making assumptions "
                    + "about the size of strings can lead to localization problems.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Negative Margins",
            EXPLANATION,
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> MARGIN_ATTRS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SdkConstants.ATTR_LAYOUT_MARGIN,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
            SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
            SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
            SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_END,
            "layout_marginHorizontal",
            "layout_marginVertical")));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        Set<String> attrs = new HashSet<>(MARGIN_ATTRS);
        attrs.add(SdkConstants.ATTR_NAME);
        return attrs;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
        }
        if (attrName != null && attrName.contains(":")) {
            attrName = attrName.substring(attrName.indexOf(':') + 1);
        }

        if (SdkConstants.ATTR_NAME.equals(attrName)) {
            Element element = attribute.getOwnerElement();
            if (element == null || !SdkConstants.TAG_ITEM.equals(element.getTagName())) {
                return;
            }

            Node parent = element.getParentNode();
            if (parent == null || !SdkConstants.TAG_STYLE.equals(parent.getNodeName())) {
                return;
            }

            String name = attribute.getValue();
            if (name == null) {
                return;
            }
            if (name.startsWith(SdkConstants.ANDROID_PREFIX)) {
                name = name.substring(SdkConstants.ANDROID_PREFIX.length());
            }

            if (!MARGIN_ATTRS.contains(name)) {
                return;
            }

            String value = element.getTextContent();
            if (value == null) {
                return;
            }
            value = value.trim();

            if (value.startsWith("-") && isDimension(value)) {
                context.report(ISSUE, element, context.getElementLocation(element),
                        "Margin value should not be negative");
            }
        } else if (MARGIN_ATTRS.contains(attrName)) {
            String value = attribute.getValue();
            if (value != null && value.startsWith("-") && isDimension(value)) {
                context.report(ISSUE, attribute, context.getValueLocation(attribute),
                        "Margin value should not be negative");
            }
        }
    }

    private static boolean isDimension(String value) {
        return value.matches("-?\\d+(\\.\\d+)?(dp|dip|px|pt|in|mm|sp)");
    }
}