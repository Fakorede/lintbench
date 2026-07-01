package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.SdkConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class NegativeMarginDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margin",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, or may be "
                            + "tempted to turn off child clipping to allow a view to escape its parent. "
                            + "Turning off child clipping to do this not only leads to poor graphical "
                            + "performance, it also results in wrong touch event handling since touch events "
                            + "are based strictly on a chain of parent-rect hit tests. Finally, making "
                            + "assumptions about the size of strings can lead to localization problems.",
                    Category.USABILITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NegativeMarginDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                SdkConstants.ATTR_LAYOUT_MARGIN,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@") || value.startsWith("?")) {
            return;
        }
        if (value.startsWith("-")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Margin values should be positive");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Element-level traversal is handled via the attribute visitor callbacks.
    }
}