package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, "
                            + "an exception is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well. "
                            + "GridLayout, as a special case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";

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
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isView(element)) {
            return;
        }

        if (isGridLayoutParent(element)) {
            return;
        }

        if (hasStyle(element)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The `layout_width` attribute is required but missing");
        }
        if (!hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The `layout_height` attribute is required but missing");
        }
    }

    private boolean isView(@NonNull Element element) {
        String tag = element.getTagName();
        return !tag.equals("merge")
                && !tag.equals("layout")
                && !tag.equals("data")
                && !tag.equals("variable")
                && !tag.equals("import")
                && !tag.equals("requestFocus")
                && !tag.equals("tag")
                && !tag.equals("include");
    }

    private boolean isGridLayoutParent(@NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            return parentTag.equals("GridLayout")
                    || parentTag.endsWith(".GridLayout");
        }
        return false;
    }

    private boolean hasStyle(@NonNull Element element) {
        return element.hasAttribute("style");
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
        // No-op
    }
}