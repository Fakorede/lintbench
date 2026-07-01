package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

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
        String localName = attribute.getLocalName();

        if (ATTR_ELLIPSIZE.equals(localName)) {
            // Check if maxLines=1 is also set on this element
            Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
            if (maxLinesAttr != null && "1".equals(maxLinesAttr.getValue())) {
                reportError(context, attribute, maxLinesAttr);
            }
        } else if (ATTR_MAX_LINES.equals(localName)) {
            // Only care about maxLines=1
            if (!"1".equals(attribute.getValue())) {
                return;
            }
            // Check if ellipsize is also set on this element
            Attr ellipsizeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ELLIPSIZE);
            if (ellipsizeAttr != null) {
                reportError(context, ellipsizeAttr, attribute);
            }
        }
    }

    private void reportError(
            @NonNull XmlContext context,
            @NonNull Attr ellipsizeAttr,
            @NonNull Attr maxLinesAttr) {
        // Report on the maxLines attribute since that's what should be changed/removed
        context.report(
                ISSUE,
                maxLinesAttr,
                context.getLocation(maxLinesAttr),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes on some "
                        + "devices. Do not combine these two attributes; use `singleLine=true` instead.",
                LintFix.create()
                        .name("Replace maxLines=1 with singleLine=true")
                        .composite(
                                LintFix.create()
                                        .set(ANDROID_URI, "singleLine", "true")
                                        .build(),
                                LintFix.create()
                                        .unset(ANDROID_URI, ATTR_MAX_LINES)
                                        .build()));
    }
}