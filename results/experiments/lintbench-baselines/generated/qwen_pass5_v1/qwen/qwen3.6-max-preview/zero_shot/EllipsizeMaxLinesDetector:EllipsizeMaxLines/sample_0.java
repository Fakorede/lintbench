package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;

public class EllipsizeMaxLinesDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "EllipsizeMaxLines",
        "Combining `ellipsize` and `maxLines=1` can lead to crashes",
        "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1`, " +
        "but that should not be done when using `ellipsize`. Combining `ellipsize` and `maxLines=1` " +
        "can lead to crashes on some devices.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"1".equals(attribute.getValue())) {
            return;
        }
        Element element = attribute.getOwnerElement();
        Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        if (ellipsizeAttr != null) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes");
        }
    }
}