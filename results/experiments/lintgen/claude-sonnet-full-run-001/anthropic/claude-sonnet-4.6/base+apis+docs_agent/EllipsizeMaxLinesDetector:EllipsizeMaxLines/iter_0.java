package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and Maxlines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TEXT_VIEW = "TextView";

    public EllipsizeMaxLinesDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TEXT_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);

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
                            + "devices. Consider using `singleLine=true` instead.");
        }
    }
}