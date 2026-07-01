package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, "
                            + "an exception is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well. GridLayout, "
                            + "as a special case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            RequiredAttributeDetector.class,
                            java.util.EnumSet.of(Scope.LAYOUT_RESOURCE_FILE, Scope.JAVA_FILE)
                    )
            );

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if ("layout".equals(tag) || "data".equals(tag) || "variable".equals(tag) || "import".equals(tag)) {
            return;
        }
        if ("merge".equals(tag) || "include".equals(tag)) {
            return;
        }
        if (element.hasAttribute("style")) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent instanceof org.w3c.dom.Element) {
            String parentTag = ((org.w3c.dom.Element) parent).getTagName();
            if (parentTag.equals("GridLayout") || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        String ns = "http://schemas.android.com/apk/res/android";
        boolean hasWidth = element.hasAttributeNS(ns, "layout_width");
        boolean hasHeight = element.hasAttributeNS(ns, "layout_height");

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The required attributes `android:layout_width` and `android:layout_height` are missing";
            } else if (!hasWidth) {
                message = "The required attribute `android:layout_width` is missing";
            } else {
                message = "The required attribute `android:layout_height` is missing";
            }
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        // No-op
    }
}