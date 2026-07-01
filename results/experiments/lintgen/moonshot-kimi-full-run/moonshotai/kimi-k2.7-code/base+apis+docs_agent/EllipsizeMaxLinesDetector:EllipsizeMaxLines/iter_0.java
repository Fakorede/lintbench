package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.VALUE_1;
import static com.android.SdkConstants.VALUE_NONE;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TEXT_VIEW);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String maxLines = element.getAttributeNS(ANDROID_URI, ATTR_MAX_LINES);
        if (!VALUE_1.equals(maxLines)) {
            return;
        }

        if (!element.hasAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE)) {
            return;
        }

        String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        if (ellipsize == null || VALUE_NONE.equals(ellipsize)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Combining ellipsize and maxLines=1 can lead to crashes on some devices"
        );
    }
}