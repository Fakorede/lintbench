package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "EllipsizeMaxLines",
        "Combining ellipsize and maxLines=1 can lead to crashes",
        "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` but that should not be done when using `ellipsize`. Combining these attributes can cause crashes on some devices.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if ("1".equals(value)) {
            Element element = attribute.getOwnerElement();
            String ellipsize = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);
            if (ellipsize != null && !ellipsize.isEmpty() && !"none".equals(ellipsize) && !ellipsize.startsWith("@")) {
                context.report(ISSUE, context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. Use `singleLine=true` instead.");
            }
        }
    }
}