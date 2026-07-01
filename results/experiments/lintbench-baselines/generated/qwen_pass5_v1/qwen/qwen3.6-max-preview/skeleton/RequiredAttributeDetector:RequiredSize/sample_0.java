package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
                    "There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime. " +
                    "It's possible to specify these widths via styles as well. GridLayout, as a special case, " +
                    "does not require you to specify a size.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals("merge") || tag.equals("include") || tag.equals("fragment") ||
            tag.equals("requestFocus") || tag.equals("tag") ||
            tag.equals("GridLayout") || tag.equals("androidx.gridlayout.widget.GridLayout")) {
            return;
        }

        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttribute("android:layout_width");
        boolean hasHeight = element.hasAttribute("android:layout_height");

        if (!hasWidth || !hasHeight) {
            String missing;
            if (!hasWidth && !hasHeight) {
                missing = "layout_width and layout_height";
            } else if (!hasWidth) {
                missing = "layout_width";
            } else {
                missing = "layout_height";
            }
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing required " + missing + " attribute");
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No-op
    }
}