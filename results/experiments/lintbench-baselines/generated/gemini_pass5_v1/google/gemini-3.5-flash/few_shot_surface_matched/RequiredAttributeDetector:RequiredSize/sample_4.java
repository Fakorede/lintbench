package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, Scope.JAVA_AND_RESOURCE_FILES);

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, "
                            + "an exception is thrown at runtime. It's possible to specify these widths "
                            + "via styles as well. GridLayout, as a special case, does not require you "
                            + "to specify a size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    public RequiredAttributeDetector() {}

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    @com.android.annotations.Nullable
    public java.util.Collection<String> getApplicableElements() {
        return null; // Visit all elements
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("merge".equals(tagName)) {
            return;
        }
        if ("requestFocus".equals(tagName) || "tag".equals(tagName) || "binding".equals(tagName)
                || "data".equals(tagName) || "import".equals(tagName) || "variable".equals(tagName)) {
            return;
        }
        if (element.hasAttribute("style")) {
            return;
        }

        // Check parent
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode instanceof org.w3c.dom.Element) {
            String parentTag = ((org.w3c.dom.Element) parentNode).getTagName();
            if ("GridLayout".equals(parentTag) || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        if ("GridLayout".equals(tagName) || tagName.endsWith(".GridLayout")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_width");
        boolean hasHeight = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_height");

        if ("include".equals(tagName)) {
            if (hasWidth && !hasHeight) {
                context.report(ISSUE, element, context.getLocation(element), "Missing `layout_height` attribute");
            } else if (!hasWidth && hasHeight) {
                context.report(ISSUE, element, context.getLocation(element), "Missing `layout_width` attribute");
            }
            return;
        }

        if (!hasWidth && !hasHeight) {
            context.report(ISSUE, element, context.getLocation(element), "Missing `layout_width` and `layout_height` attributes");
        } else if (!hasWidth) {
            context.report(ISSUE, element, context.getLocation(element), "Missing `layout_width` attribute");
        } else if (!hasHeight) {
            context.report(ISSUE, element, context.getLocation(element), "Missing `layout_height` attribute");
        }
    }

    @Override
    @com.android.annotations.Nullable
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitMethodCall(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node, @com.android.annotations.NonNull PsiMethod method) {
        // No-op
    }
}