package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ELLIPSIZE, ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();

        String ellipsizeValue = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        String maxLinesValue = element.getAttributeNS(ANDROID_URI, ATTR_MAX_LINES);

        if (ellipsizeValue == null || ellipsizeValue.isEmpty()) {
            return;
        }
        if (maxLinesValue == null || maxLinesValue.isEmpty()) {
            return;
        }

        if (!"1".equals(maxLinesValue)) {
            return;
        }

        // Both ellipsize and maxLines=1 are present; report on the attribute being visited
        // to avoid double-reporting, only report when visiting the maxLines attribute
        String localName = attribute.getLocalName();
        if (ATTR_MAX_LINES.equals(localName)) {
            Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE);
            if (ellipsizeAttr != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(attribute),
                        "Combining `ellipsize` and `maxLines=1` can lead to crashes on "
                                + "some devices. Use `singleLine=true` instead.");
            }
        }
    }
}