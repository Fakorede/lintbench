package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices."
                            + " Earlier versions of lint recommended replacing `singleLine=true`"
                            + " with `maxLines=1` but that should not be done when using"
                            + " `ellipsize`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ATTR_ELLIPSIZE = "ellipsize";

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(ATTR_MAX_LINES, ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (!ATTR_MAX_LINES.equals(attribute.getLocalName())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || !"1".equals(value.trim())) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        if (element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE) != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining ellipsize and maxLines=1 can lead to crashes on some devices");
        }
    }
}