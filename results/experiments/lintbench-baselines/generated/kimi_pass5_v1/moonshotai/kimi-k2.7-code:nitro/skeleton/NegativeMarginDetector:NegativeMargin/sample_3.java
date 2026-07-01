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

    private static final Collection<String> MARGIN_ATTRIBUTES =
            Arrays.asList(
                    "layout_margin",
                    "layout_marginLeft",
                    "layout_marginRight",
                    "layout_marginStart",
                    "layout_marginEnd",
                    "layout_marginTop",
                    "layout_marginBottom",
                    "layout_marginHorizontal",
                    "layout_marginVertical");

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && isNegativeDimension(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Avoid negative margin values");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    private static boolean isNegativeDimension(String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.isEmpty() || value.startsWith("@") || value.startsWith("?")) {
            return false;
        }
        if (!value.startsWith("-")) {
            return false;
        }
        String rest = value.substring(1).trim();
        if (rest.isEmpty()) {
            return false;
        }

        int i = 0;
        boolean seenDigit = false;
        while (i < rest.length()) {
            char c = rest.charAt(i);
            if (c >= '0' && c <= '9') {
                seenDigit = true;
                i++;
            } else if (c == '.') {
                i++;
                while (i < rest.length()) {
                    c = rest.charAt(i);
                    if (c >= '0' && c <= '9') {
                        seenDigit = true;
                        i++;
                    } else {
                        break;
                    }
                }
                break;
            } else {
                break;
            }
        }

        if (!seenDigit) {
            return false;
        }

        String unit = rest.substring(i).trim();
        return unit.isEmpty()
                || unit.equals("dp")
                || unit.equals("dip")
                || unit.equals("px")
                || unit.equals("sp")
                || unit.equals("pt")
                || unit.equals("in")
                || unit.equals("mm");
    }
}