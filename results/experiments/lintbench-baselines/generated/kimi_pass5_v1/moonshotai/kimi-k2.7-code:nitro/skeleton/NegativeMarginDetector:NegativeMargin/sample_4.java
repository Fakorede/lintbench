package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, "
                            + "or may be tempted to turn off child clipping to allow a view to escape "
                            + "its parent. Turning off child clipping to do this not only leads to poor "
                            + "graphical performance, it also results in wrong touch event handling since "
                            + "touch events are based strictly on a chain of parent-rect hit tests. "
                            + "Finally, making assumptions about the size of strings can lead to "
                            + "localization problems.",
                    Category.USABILITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
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
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && isNegativeMarginValue(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Margin values should not be negative");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not needed; negative margins are detected via visitAttribute.
    }

    private static boolean isNegativeMarginValue(String value) {
        String v = value.trim();
        if (v.isEmpty() || v.charAt(0) != '-') {
            return false;
        }

        int i = 1;
        int len = v.length();
        boolean seenDigit = false;
        while (i < len) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                seenDigit = true;
            } else if (c != '.') {
                break;
            }
            i++;
        }

        if (!seenDigit) {
            return false;
        }

        try {
            return Double.parseDouble(v.substring(0, i)) < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}