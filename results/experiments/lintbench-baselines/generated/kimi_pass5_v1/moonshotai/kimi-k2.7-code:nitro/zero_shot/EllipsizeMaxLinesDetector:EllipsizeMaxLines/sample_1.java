package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ELLIPSIZE_NONE = "none";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TEXT_VIEW,
                SdkConstants.EDIT_TEXT,
                SdkConstants.BUTTON,
                SdkConstants.CHECK_BOX,
                SdkConstants.RADIO_BUTTON,
                SdkConstants.TOGGLE_BUTTON,
                SdkConstants.SWITCH,
                SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String maxLines = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_MAX_LINES);
        if (!SdkConstants.VALUE_1.equals(maxLines)) {
            return;
        }

        String ellipsize = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);
        if (ellipsize.isEmpty() || ELLIPSIZE_NONE.equals(ellipsize)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices"
        );
    }
}