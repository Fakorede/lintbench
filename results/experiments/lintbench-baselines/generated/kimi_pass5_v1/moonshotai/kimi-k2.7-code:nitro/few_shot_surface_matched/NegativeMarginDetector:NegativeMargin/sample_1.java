package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class NegativeMarginDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, "
                            + "or may be tempted to turn off child clipping to allow a view to escape "
                            + "its parent. Turning off child clipping to do this not only leads to "
                            + "poor graphical performance, it also results in wrong touch event "
                            + "handling since touch events are based strictly on a chain of "
                            + "parent-rect hit tests. Finally, making assumptions about the size of "
                            + "strings can lead to localization problems.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                "layout_margin",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_marginTop",
                "layout_marginBottom",
                "layout_marginStart",
                "layout_marginEnd",
                "layout_marginHorizontal",
                "layout_marginVertical");
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (isNegative(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Margin values should not be negative");
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // No element-level checks are required; margin validation is handled in visitAttribute.
    }

    private boolean isNegative(String value) {
        if (value == null) {
            return false;
        }
        int i = 0;
        int length = value.length();
        while (i < length && Character.isWhitespace(value.charAt(i))) {
            i++;
        }
        if (i >= length || value.charAt(i) != '-') {
            return false;
        }
        i++;
        if (i >= length) {
            return false;
        }
        char c = value.charAt(i);
        return c == '.' || (c >= '0' && c <= '9');
    }
}