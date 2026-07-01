package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("TextView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "maxLines");
        if (maxLinesAttr != null && "1".equals(maxLinesAttr.getValue())) {
            String ellipsize = element.getAttributeNS(SdkConstants.ANDROID_URI, "ellipsize");
            if (ellipsize != null && !ellipsize.isEmpty() && !"none".equals(ellipsize)) {
                context.report(ISSUE, context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. " +
                    "Use `singleLine=\"true\"` instead.");
            }
        }
    }
}