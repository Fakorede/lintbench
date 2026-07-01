package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;

public class NegativeMarginDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that you are making assumptions about views surrounding the current one, or may be tempted to turn off child clipping to allow a view to escape its parent. Turning off child clipping to do this not only leads to poor graphical performance, it also results in wrong touch event handling since touch events are based strictly on a chain of parent-rect hit tests. Finally, making assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Collection<String> MARGIN_ATTRIBUTES = Arrays.asList(
            "layout_margin",
            "layout_marginBottom",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginLeft",
            "layout_marginRight",
            "layout_marginStart",
            "layout_marginTop",
            "layout_marginVertical"
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        if (isNegativeDimension(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Margin values should not be negative"
            );
        }
    }

    private static boolean isNegativeDimension(String value) {
        if (!value.startsWith("-")) {
            return false;
        }

        int length = value.length();
        if (length < 2) {
            return false;
        }

        int i = 1;
        boolean sawDigit = false;
        boolean sawDot = false;

        while (i < length) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sawDigit = true;
                i++;
            } else if (c == '.' && !sawDot) {
                sawDot = true;
                i++;
            } else {
                break;
            }
        }

        if (!sawDigit) {
            return false;
        }

        if (i == length) {
            return true;
        }

        String unit = value.substring(i);
        return isDimensionUnit(unit);
    }

    private static boolean isDimensionUnit(String unit) {
        return unit.equals("px")
                || unit.equals("dp")
                || unit.equals("dip")
                || unit.equals("sp")
                || unit.equals("pt")
                || unit.equals("in")
                || unit.equals("mm");
    }
}