package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconIncorrectSize",
            "Launcher icons in mipmap folders should not have explicit width or height attributes.",
            null,
            Severity.ERROR,
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