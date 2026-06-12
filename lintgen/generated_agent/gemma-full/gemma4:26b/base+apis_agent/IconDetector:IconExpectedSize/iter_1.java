package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BugPattern;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconIncorrectSize",
            "Launcher icons in mipmap folders should not have explicit width or height attributes. " +
            "The size is predefined for each density and should be determined by the resource itself.",
            BugPattern.Severity.ERROR,
            BugPattern.BugCategory.CORRECTNESS,
            null,
            null,
            null,
            null
    );

    @Override
    public List<Scope> getApplicableScopes() {
        return Arrays.asList(Scope.RESOURCE_FILES);
    }

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
        // Check for width or height attributes in the current element.
        // We check both namespaced and non-namespaced versions to be safe.
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
}