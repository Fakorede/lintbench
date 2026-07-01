package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and Maxlines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
            "Earlier versions of lint recommended replacing `singleLine=true` with " +
            "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr ellipsizeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);
        Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MAX_LINES);

        if (ellipsizeAttr != null && maxLinesAttr != null) {
            String maxLines = maxLinesAttr.getValue();
            if ("1".equals(maxLines)) {
                context.report(
                        ISSUE,
                        maxLinesAttr,
                        context.getLocation(maxLinesAttr),
                        "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices; use `singleLine=\"true\"` instead"
                );
            }
        }
    }
}