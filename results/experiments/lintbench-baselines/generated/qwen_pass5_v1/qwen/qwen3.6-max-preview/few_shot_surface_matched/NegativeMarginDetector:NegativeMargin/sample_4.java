package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class NegativeMarginDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margin",
            "Margin values should be positive. Negative values are generally a sign that you are making assumptions about views surrounding the current one, or may be tempted to turn off child clipping to allow a view to escape its parent. Turning off child clipping to do this not only leads to poor graphical performance, it also results in wrong touch event handling since touch events are based strictly on a chain of parent-rect hit tests. Finally, making assumptions about the size of strings can lead to localization problems.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
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
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.trim().startsWith("-")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Margin values should be positive");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No element-specific checks required for this issue
    }
}