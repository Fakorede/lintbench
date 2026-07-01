package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines=1 can lead to crashes",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
            "Earlier versions of lint recommended replacing `singleLine=true` with " +
            "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "ellipsize")
                && element.hasAttributeNS(SdkConstants.ANDROID_URI, "maxLines")) {
            String maxLines = element.getAttributeNS(SdkConstants.ANDROID_URI, "maxLines");
            if ("1".equals(maxLines)) {
                Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "maxLines");
                if (maxLinesAttr != null) {
                    context.report(
                            ISSUE,
                            maxLinesAttr,
                            context.getLocation(maxLinesAttr),
                            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices; use `singleLine=true` instead"
                    );
                }
            }
        }
    }
}