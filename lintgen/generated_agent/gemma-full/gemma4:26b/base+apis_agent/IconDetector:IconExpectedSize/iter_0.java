package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BugPattern;
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

    private static final String ERROR_MESSAGE = "Icon has incorrect size specification. " +
            "Remove explicit 'android:width' or 'android:height' attributes to ensure the icon " +
            "correctly follows the predefined density-specific dimensions.";

    @NotNull
    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList("adaptive-icon", "bitmap");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        // We only want to enforce this rule for resources located in mipmap folders,
        // as these are the standard locations for launcher icons which follow density-specific sizing.
        if (context.getFolderType() == ResourceFolderType.MIPMAP) {
            String tagName = element.getLocalName();
            
            if ("adaptive-icon".equals(tagName) || "bitmap".equals(tagName)) {
                boolean hasWidth = element.hasAttribute("android:width") || element.hasAttribute("width");
                boolean hasHeight = element.hasAttribute("android:height") || element.hasAttribute("height");

                if (hasWidth || hasHeight) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            ERROR_MESSAGE
                    );
                }
            }
        }
    }
}