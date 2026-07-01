package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_VERTICAL;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;

public class NegativeMarginDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that "
                    + "you are making assumptions about views surrounding the current one, or may be "
                    + "tempted to turn off child clipping to allow a view to escape its parent. "
                    + "Turning off child clipping to do this not only leads to poor graphical "
                    + "performance, it also results in wrong touch event handling since touch events "
                    + "are based strictly on a chain of parent-rect hit tests. Finally, making "
                    + "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.ALL_RESOURCE_FILES));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_LAYOUT_MARGIN,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_TOP,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_BOTTOM,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END,
                ATTR_LAYOUT_MARGIN_HORIZONTAL,
                ATTR_LAYOUT_MARGIN_VERTICAL);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.length() < 2) {
            return;
        }

        if (value.charAt(0) != '-') {
            return;
        }

        char second = value.charAt(1);
        if (!Character.isDigit(second) && second != '.') {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Margin values should not be negative");
    }
}