package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.SdkConstants;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that " +
            "you are making assumptions about views surrounding the current one, or may be " +
            "tempted to turn off child clipping to allow a view to escape its parent. " +
            "Turning off child clipping to do this not only leads to poor graphical " +
            "performance, it also results in wrong touch event handling since touch events " +
            "are based strictly on a chain of parent-rect hit tests. Finally, making " +
            "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    NegativeMarginDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                SdkConstants.ATTR_LAYOUT_MARGIN,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_END,
                "layout_marginHorizontal",
                "layout_marginVertical"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !SdkConstants.ANDROID_URI.equals(namespace)) {
            return;
        }

        String value = attribute.getValue();
        if (value != null) {
            value = value.trim();
            if (value.startsWith("-")) {
                if (value.length() > 1 && (Character.isDigit(value.charAt(1)) || value.charAt(1) == '.')) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Margin values should be positive"
                    );
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("dimen", "item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("dimen".equals(tagName)) {
            checkDimenElement(context, element);
        } else if ("item".equals(tagName)) {
            String type = element.getAttribute("type");
            String name = element.getAttribute("name");
            if ("dimen".equals(type) || isMarginAttribute(name)) {
                checkDimenElement(context, element);
            }
        }
    }

    private boolean isMarginAttribute(String name) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("android:")) {
            name = name.substring("android:".length());
        }
        return "layout_margin".equals(name)
                || "layout_marginLeft".equals(name)
                || "layout_marginTop".equals(name)
                || "layout_marginRight".equals(name)
                || "layout_marginBottom".equals(name)
                || "layout_marginStart".equals(name)
                || "layout_marginEnd".equals(name)
                || "layout_marginHorizontal".equals(name)
                || "layout_marginVertical".equals(name);
    }

    private void checkDimenElement(XmlContext context, Element element) {
        String value = element.getTextContent();
        if (value == null || value.isEmpty()) {
            org.w3c.dom.Node firstChild = element.getFirstChild();
            if (firstChild != null) {
                value = firstChild.getNodeValue();
            }
        }
        if (value != null) {
            value = value.trim();
            if (value.startsWith("-")) {
                if (value.length() > 1 && (Character.isDigit(value.charAt(1)) || value.charAt(1) == '.')) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Margin values should be positive"
                    );
                }
            }
        }
    }
}