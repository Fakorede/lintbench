package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements com.android.tools.lint.detector.api.XmlScanner {

    public static final com.android.tools.lint.detector.api.Issue ISSUE = com.android.tools.lint.detector.api.Issue.create(
            "IconIncorrectSize",
            "Launcher icons in mipmap folders should not have explicit width or height attributes.",
            com.android.tools.lint.detector.api.BugCategory.CORRECTNESS,
            com.android.tools.lint.detector.api.Severity.ERROR,
            new com.android.tools.lint.detector.api.Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("adaptive-icon", "bitmap");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttribute("android:width") || 
            element.hasAttribute("width") || 
            element.hasAttribute("android:height") || 
            element.hasAttribute("height")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Launcher icons in mipmap folders should not have explicit width or height attributes."
            );
        }
    }
}