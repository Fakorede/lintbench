package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;

public class NegativeMarginDetector extends LayoutDetector {

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
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Collection<String> MARGIN_ATTRIBUTES = Arrays.asList(
            "layout_margin",
            "layout_marginLeft",
            "layout_marginTop",
            "layout_marginRight",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd"
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith("-")) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    "Margin values should be positive");
        }
    }
}