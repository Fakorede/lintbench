package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;

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

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                SdkConstants.ATTR_LAYOUT_MARGIN,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_END,
                "layout_marginHorizontal",
                "layout_marginVertical");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.charAt(0) == '-' && isDimension(value)) {
            String message = "Margin value should not be negative";
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    private static boolean isDimension(String value) {
        return value.matches("-?\\d+(\\.\\d+)?(dp|dip|px|pt|in|mm|sp)");
    }
}