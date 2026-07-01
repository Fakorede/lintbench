package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and MaxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String ellipsize = element.getAttributeNS(ANDROID_URI, "ellipsize");
        String maxLines = element.getAttributeNS(ANDROID_URI, "maxLines");
        if (ellipsize.isEmpty()
                || "none".equals(ellipsize)
                || maxLines.isEmpty()
                || !"1".equals(maxLines.trim())) {
            return;
        }

        Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, "maxLines");
        if (maxLinesAttr != null) {
            context.report(
                    ISSUE,
                    maxLinesAttr,
                    context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=1` can cause crashes on some devices; "
                            + "use `singleLine=\"true\"` instead.");
        }
    }
}