package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, "
                            + "an exception is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well. "
                            + "GridLayout, as a special case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            RequiredAttributeDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("view") || tagName.equals("merge") || tagName.equals("include") || tagName.equals("requestFocus")) {
            return;
        }

        // Check if inside a GridLayout
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode instanceof org.w3c.dom.Element) {
            String parentTag = ((org.w3c.dom.Element) parentNode).getTagName();
            if (parentTag.equals("GridLayout") || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_width");
        boolean hasHeight = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_height");

        if (!hasWidth || !hasHeight) {
            if (Character.isLowerCase(tagName.charAt(0)) && !tagName.contains(".") && !tagName.equals("fragment")) {
                return;
            }

            String message;
            if (!hasWidth && !hasHeight) {
                message = "The view is missing required `layout_width` and `layout_height` attributes";
            } else if (!hasWidth) {
                message = "The view is missing required `layout_width` attribute";
            } else {
                message = "The view is missing required `layout_height` attribute";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UCallExpression node,
            @com.android.annotations.NonNull PsiMethod method) {
        // No-op
    }
}