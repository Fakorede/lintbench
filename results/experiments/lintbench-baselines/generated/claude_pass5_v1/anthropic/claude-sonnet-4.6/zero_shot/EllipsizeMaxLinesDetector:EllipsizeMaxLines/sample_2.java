package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and Maxlines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TEXT_VIEW = "TextView";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ELLIPSIZE);
        Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_MAX_LINES);

        if (ellipsizeAttr == null || maxLinesAttr == null) {
            return;
        }

        String maxLinesValue = maxLinesAttr.getValue();
        if ("1".equals(maxLinesValue)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some "
                            + "devices. Consider using `singleLine=true` instead."
            );
        }
    }
}