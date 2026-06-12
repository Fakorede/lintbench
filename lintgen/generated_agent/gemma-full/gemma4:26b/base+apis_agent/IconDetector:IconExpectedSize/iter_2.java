package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BugCategory;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconIncorrectSize",
            "Launcher icons in mipmap folders should not have explicit width or height attributes.",
            Severity.ERROR,
            BugCategory.CORRECTNESS,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList("adaptive-icon", "bitmap");
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        boolean hasWidth = element.hasAttribute("android:width") || element.hasAttribute("width");
        boolean hasHeight = element.hasAttribute("android:height") || element.hasAttribute("height");

        if (hasWidth || hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Launcher icons in mipmap folders should not have explicit width or height attributes."
            );
        }
    }

    public static class Implementation implements Detector.Implementation {
        @Override
        public Detector create(Context context) {
            return new IconDetector();
        }

        @Override
        public String getDescription() {
            return "Detects incorrect icon sizes in mipmap folders.";
        }
    }
}