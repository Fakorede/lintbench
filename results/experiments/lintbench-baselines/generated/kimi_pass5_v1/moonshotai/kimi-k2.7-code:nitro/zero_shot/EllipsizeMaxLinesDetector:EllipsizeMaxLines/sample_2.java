package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1`, but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        if (ellipsize == null || ellipsize.isEmpty()) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                        + "Use `singleLine=true` instead of `maxLines=1` when using `ellipsize`.");
    }
}