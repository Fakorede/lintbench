package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
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
                            java.util.EnumSet.of(Scope.LAYOUT, Scope.JAVA_FILE)));

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull com.android.tools.lint.detector.api.Context context) {
        // No-op
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull com.android.tools.lint.detector.api.XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("merge".equals(tagName)
                || "include".equals(tagName)
                || "view".equals(tagName)
                || "requestFocus".equals(tagName)
                || "tag".equals(tagName)
                || "layout".equals(tagName)
                || "data".equals(tagName)
                || "variable".equals(tagName)
                || "import".equals(tagName)) {
            return;
        }

        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode instanceof org.w3c.dom.Element) {
            String parentTag = ((org.w3c.dom.Element) parentNode).getTagName();
            if ("GridLayout".equals(parentTag) || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        if (element.hasAttribute("style")) {
            return;
        }

        String ns = "http://schemas.android.com/apk/res/android";
        boolean hasWidth = element.hasAttributeNS(ns, "layout_width");
        boolean hasHeight = element.hasAttributeNS(ns, "layout_height");

        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The view is missing both layout_width and layout_height attributes");
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The view is missing layout_width attribute");
        } else if (!hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The view is missing layout_height attribute");
        }
    }

    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Arrays.asList("addView", "setLayoutParams");
    }

    @Override
    public void visitMethodCall(
            @com.android.annotations.NonNull com.android.tools.lint.detector.api.JavaContext context,
            @com.android.annotations.NonNull org.jetbrains.uast.UCallExpression node,
            @com.android.annotations.NonNull com.intellij.psi.PsiMethod method) {
        // No-op for standard layout attribute checking in Java source code
    }
}