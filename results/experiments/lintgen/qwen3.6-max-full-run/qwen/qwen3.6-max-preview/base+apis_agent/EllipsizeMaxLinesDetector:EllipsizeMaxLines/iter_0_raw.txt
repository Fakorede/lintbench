package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "EllipsizeMaxLines",
        "Combining ellipsize and maxLines=1 can lead to crashes",
        "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
        "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` " +
        "but that should not be done when using `ellipsize`.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE)) {
            context.report(ISSUE, context.getLocation(attribute),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes. Use `singleLine=true` instead.");
        }
    }
}