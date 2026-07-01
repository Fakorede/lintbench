package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class NegativeMarginDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, or may be "
                            + "tempted to turn off child clipping to allow a view to escape its parent. "
                            + "Turning off child clipping to do this not only leads to poor graphical "
                            + "performance, it also results in wrong touch event handling since touch events "
                            + "are based strictly on a chain of parent-rect hit tests. Finally, making "
                            + "assumptions about the size of strings can lead to localization problems.",
                    Category.USABILITY,
                    4,
                    Severity.WARNING,
                    new Implementation(
                            NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
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
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            value = value.trim();
            if (value.startsWith("-")) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Margin values should be positive (" + value + ")");
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Handled via visitAttribute
    }
}