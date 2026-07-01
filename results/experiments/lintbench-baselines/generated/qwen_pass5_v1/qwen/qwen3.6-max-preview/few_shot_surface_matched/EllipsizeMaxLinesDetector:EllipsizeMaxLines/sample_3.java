package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining ellipsize and maxLines=1",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1` but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull String namespaceUri, @NonNull String localName) {
        return SdkConstants.TEXT_VIEW.equals(localName);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Attr ellipsizeAttr = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);
        if (ellipsizeAttr != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Use `singleLine=true` instead.");
        }
    }
}