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
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, an exception "
                    + "is thrown at runtime. It's possible to specify these widths via styles as well. "
                    + "GridLayout, as a special case, does not require you to specify a size.",
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
        // No cross-file or project-wide aggregation needed for this check
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Skip non-view tags: views typically start with an uppercase letter or contain a dot (custom views)
        if (tag.isEmpty() || (tag.indexOf('.') == -1 && !Character.isUpperCase(tag.charAt(0)))) {
            return;
        }

        // Skip structural/data-binding tags that are not actual views
        if (tag.equals("merge") || tag.equals("include") || tag.equals("fragment")
                || tag.equals("requestFocus") || tag.equals("tag") || tag.equals("layout")
                || tag.equals("data") || tag.equals("variable") || tag.equals("import")) {
            return;
        }

        // GridLayout and its children do not require explicit width/height
        if (isGridLayout(tag)) {
            return;
        }
        Node parent = element.getParentNode();
        if (parent instanceof Element && isGridLayout(((Element) parent).getTagName())) {
            return;
        }

        // If a style is applied, it might define the dimensions, so skip static checking
        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, "layout_width");
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, "layout_height");

        if (!hasWidth || !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required layout_width or layout_height attribute");
        }
    }

    private boolean isGridLayout(@NonNull String tag) {
        return tag.equals("GridLayout") || tag.endsWith(".GridLayout");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Java/Kotlin code analysis not required for this XML-focused issue
    }
}