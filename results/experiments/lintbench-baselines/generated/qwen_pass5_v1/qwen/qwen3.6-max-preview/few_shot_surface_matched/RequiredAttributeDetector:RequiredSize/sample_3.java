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

public class RequiredAttributeDetector extends LayoutDetector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime. " +
            "It's possible to specify these widths via styles as well. GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, java.util.EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    public boolean appliesTo(com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(com.android.annotations.NonNull Context context) {
        // No project-wide aggregation required
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(com.android.annotations.NonNull XmlContext context, com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (tag.equals("GridLayout") || tag.equals("androidx.gridlayout.widget.GridLayout") ||
            tag.equals("merge") || tag.equals("include") || tag.equals("fragment")) {
            return;
        }

        if (element.hasAttribute("style")) {
            return;
        }

        String androidUri = "http://schemas.android.com/apk/res/android";
        boolean hasWidth = element.hasAttributeNS(androidUri, "layout_width");
        boolean hasHeight = element.hasAttributeNS(androidUri, "layout_height");

        if (!hasWidth || !hasHeight) {
            String missing = !hasWidth ? "layout_width" : "layout_height";
            context.report(ISSUE, element, context.getLocation(element),
                    "This view must specify `android:" + missing + "` or it will throw a runtime exception");
        }
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethodCall(com.android.annotations.NonNull JavaContext context, com.android.annotations.NonNull UCallExpression call, com.android.annotations.NonNull PsiMethod method) {
        // No Java method checks required for this issue
    }
}