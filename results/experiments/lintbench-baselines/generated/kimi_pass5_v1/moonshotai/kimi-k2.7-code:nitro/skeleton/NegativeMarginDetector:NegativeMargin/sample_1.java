package com.android.tools.lint.checks;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Collection<String> MARGIN_ATTRIBUTES = Arrays.asList(
            "layout_margin",
            "layout_marginLeft",
            "layout_marginTop",
            "layout_marginRight",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginVertical"
    );

    private static final Implementation IMPLEMENTATION =
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Ignore references and theme attributes such as @dimen/foo or ?attr/bar.
        if (value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        if (isNegativeDimension(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Avoid negative margin values");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // All relevant checks are performed in visitAttribute.
    }

    private static boolean isNegativeDimension(String value) {
        if (!value.startsWith("-")) {
            return false;
        }

        String number = value.substring(1);
        int length = number.length();
        boolean seenDigit = false;
        boolean seenDot = false;
        int i = 0;

        while (i < length) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') {
                seenDigit = true;
            } else if (c == '.') {
                if (seenDot) {
                    return false;
                }
                seenDot = true;
            } else {
                break;
            }
            i++;
        }

        if (!seenDigit) {
            return false;
        }

        if (i < length) {
            String unit = number.substring(i);
            if (!unit.isEmpty()
                    && !unit.equals("dp")
                    && !unit.equals("dip")
                    && !unit.equals("sp")
                    && !unit.equals("px")
                    && !unit.equals("pt")
                    && !unit.equals("in")
                    && !unit.equals("mm")) {
                return false;
            }
        }

        return true;
    }
}