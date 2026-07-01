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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and Maxlines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
            "Earlier versions of lint recommended replacing `singleLine=true` with " +
            "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MAX_LINES);
        if (maxLinesAttr != null && "1".equals(maxLinesAttr.getValue())) {
            context.report(
                    ISSUE,
                    maxLinesAttr,
                    context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices; use `singleLine=\"true\"` instead"
            );
        }
    }
}