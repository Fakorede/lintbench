package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, "
                            + "or may be tempted to turn off child clipping to allow a view to "
                            + "escape its parent. Turning off child clipping to do this not only "
                            + "leads to poor graphical performance, it also results in wrong touch "
                            + "event handling since touch events are based strictly on a chain of "
                            + "parent-rect hit tests. Finally, making assumptions about the size "
                            + "of strings can lead to localization problems.",
                    Category.USABILITY,
                    4,
                    Severity.WARNING,
                    new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    private static final String[] MARGIN_ATTRS = {
        ATTR_LAYOUT_MARGIN,
        ATTR_LAYOUT_MARGIN_LEFT,
        ATTR_LAYOUT_MARGIN_RIGHT,
        ATTR_LAYOUT_MARGIN_TOP,
        ATTR_LAYOUT_MARGIN_BOTTOM,
        ATTR_LAYOUT_MARGIN_START,
        ATTR_LAYOUT_MARGIN_END,
        ATTR_LAYOUT_MARGIN_HORIZONTAL,
        ATTR_LAYOUT_MARGIN_VERTICAL
    };

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(MARGIN_ATTRS);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && isNegativeMarginValue(value)) {
            report(context, attribute, attribute.getName(), value);
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_ITEM.equals(element.getTagName())) {
            return;
        }
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String attrName = nameAttr.getValue();
        if (!isMarginAttribute(attrName)) {
            return;
        }
        String value = element.getTextContent();
        if (value != null) {
            value = value.trim();
            if (isNegativeMarginValue(value)) {
                report(context, element, attrName, value);
            }
        }
    }

    private static boolean isMarginAttribute(String name) {
        String localName = name;
        int colon = name.indexOf(':');
        if (colon != -1) {
            localName = name.substring(colon + 1);
        }
        for (String margin : MARGIN_ATTRS) {
            if (margin.equals(localName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNegativeMarginValue(String value) {
        return value.startsWith("-") && !value.startsWith("-@");
    }

    private static void report(XmlContext context, Node scope, String attrName, String value) {
        context.report(
                ISSUE,
                scope,
                context.getLocation(scope),
                String.format("Margin values should not be negative (%1$s=%2$s)", attrName, value));
    }
}