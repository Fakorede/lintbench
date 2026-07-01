package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize and maxLines=1 can lead to crashes",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
            "Earlier versions of lint recommended replacing `singleLine=true` with " +
            "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String ellipsize = element.getAttributeNS(SdkConstants.ANDROID_URI, "ellipsize");
        if (ellipsize == null || ellipsize.isEmpty() || "none".equals(ellipsize)) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "maxLines")) {
            String maxLines = element.getAttributeNS(SdkConstants.ANDROID_URI, "maxLines");
            if ("1".equals(maxLines)) {
                Attr maxLinesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "maxLines");
                if (maxLinesAttr != null) {
                    context.report(
                            ISSUE,
                            maxLinesAttr,
                            context.getLocation(maxLinesAttr),
                            "Combining `ellipsize` and `maxLines=1` can lead to crashes; use `singleLine=true` instead"
                    );
                }
            }
        }
    }
}