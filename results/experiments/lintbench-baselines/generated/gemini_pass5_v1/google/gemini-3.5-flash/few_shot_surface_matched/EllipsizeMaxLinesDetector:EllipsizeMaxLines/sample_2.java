package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1` but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class,
                            Scope.LAYOUT_RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("ellipsize");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String namespace = "http://schemas.android.com/apk/res/android";
        if (element.hasAttributeNS(namespace, "maxLines")) {
            String maxLines = element.getAttributeNS(namespace, "maxLines");
            if ("1".equals(maxLines)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices; "
                                + "use `singleLine=true` instead");
            }
        }
    }
}