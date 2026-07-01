package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ATTR_ELLIPSIZE = "ellipsize";

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and MaxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
            + "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` "
            + "but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull String tagName) {
        return "TextView".equals(tagName);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String maxLines = element.getAttributeNS(ANDROID_URI, ATTR_MAX_LINES);
        String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);

        if ("1".equals(maxLines) && ellipsize != null && !ellipsize.isEmpty() && !"none".equals(ellipsize)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Combining ellipsize and maxLines=1 can lead to crashes on some devices");
        }
    }
}