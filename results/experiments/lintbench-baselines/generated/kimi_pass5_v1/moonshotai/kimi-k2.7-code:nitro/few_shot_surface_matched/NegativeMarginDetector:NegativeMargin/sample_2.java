package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that you are "
                            + "making assumptions about views surrounding the current one, or may be "
                            + "tempted to turn off child clipping to allow a view to escape its parent. "
                            + "Turning off child clipping to do this not only leads to poor graphical "
                            + "performance, it also results in wrong touch event handling since touch "
                            + "events are based strictly on a chain of parent-rect hit tests. Finally, "
                            + "making assumptions about the size of strings can lead to localization "
                            + "problems.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Collection<String> MARGIN_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "layout_margin",
                            "layout_marginBottom",
                            "layout_marginEnd",
                            "layout_marginHorizontal",
                            "layout_marginLeft",
                            "layout_marginRight",
                            "layout_marginStart",
                            "layout_marginTop",
                            "layout_marginVertical"));

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
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        String v = value.trim();
        if (v.length() > 1
                && v.charAt(0) == '-'
                && !v.startsWith("@")
                && !v.startsWith("?")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Margin values should not be negative");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // No element-level checks are required.
    }
}