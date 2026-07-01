package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and MaxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
                    "Earlier versions of lint recommended replacing `singleLine=true` with " +
                    "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MAX_LINES);
        Attr ellipsizeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELLIPSIZE);

        if (maxLinesAttr != null && ellipsizeAttr != null) {
            String maxLinesValue = maxLinesAttr.getValue();
            String ellipsizeValue = ellipsizeAttr.getValue();

            if ("1".equals(maxLinesValue) && !"none".equals(ellipsizeValue)) {
                context.report(
                        ISSUE,
                        maxLinesAttr,
                        context.getLocation(maxLinesAttr),
                        "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. " +
                                "Use `android:singleLine=\"true\"` instead, or remove `android:maxLines=\"1\"`."
                );
            }
        }
    }
}