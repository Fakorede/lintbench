package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;

import java.util.Collection;
import java.util.List;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime. " +
            "It's possible to specify these widths via styles as well. GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Required override per specification. No global aggregation needed.
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null; // Visit all XML elements
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String tag = element.getTagName();

        // Skip non-view container tags and the special-cased GridLayout
        if (tag.equals("GridLayout") || tag.endsWith(".GridLayout") ||
            tag.equals("merge") || tag.equals("include") ||
            tag.equals("requestFocus") || tag.equals("tag")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, "layout_width");
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, "layout_height");
        boolean hasStyle = element.hasAttribute("style");

        // If a style is applied, assume dimensions might be defined there.
        // Otherwise, enforce explicit layout dimensions.
        if (!hasStyle && (!hasWidth || !hasHeight)) {
            String missing;
            if (!hasWidth && !hasHeight) {
                missing = "layout_width and layout_height";
            } else if (!hasWidth) {
                missing = "layout_width";
            } else {
                missing = "layout_height";
            }
            context.report(ISSUE, element, context.getLocation(element),
                    "This view is missing a required " + missing + " attribute");
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null; // Visit all method calls
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        // Source code scanning interface requirement.
        // This detector focuses on XML layout attributes, so no Java/Kotlin analysis is performed.
    }
}