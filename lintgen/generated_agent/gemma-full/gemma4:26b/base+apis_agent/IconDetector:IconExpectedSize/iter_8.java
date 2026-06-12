package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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
            "Launcher icon has incorrect size",
            "Launcher icons in mipmap folders should not have explicit width or height attributes to ensure they scale correctly with density.",
            Category.CORRECTNESS,
            5,
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
        String[] sizeAttributes = {"android:width", "width", "android:height", "height"};
        for (String attr : sizeAttributes) {
            if (element.hasAttribute(attr)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Launcher icons in mipmap folders should not have explicit width or height attributes."
                );
                break;
            }
        }
    }
}