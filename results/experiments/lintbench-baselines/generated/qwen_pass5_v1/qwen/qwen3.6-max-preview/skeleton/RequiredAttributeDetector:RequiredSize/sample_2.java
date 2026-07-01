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
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
                    "There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime. " +
                    "It's possible to specify these widths via styles as well. GridLayout, as a special case, does not require you to specify a size.",
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
        // No project-level aggregation required for this detector
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        // Skip structural tags that do not represent actual views requiring layout params
        if (tag.equals("merge") || tag.equals("include") || tag.equals("requestFocus") || tag.equals("tag")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Dimensions may be provided via a style reference
        if (element.hasAttribute("style")) {
            return;
        }

        // GridLayout infers child sizes, so explicit dimensions are not required
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals("GridLayout") ||
                parentTag.equals("android.widget.GridLayout") ||
                parentTag.equals("androidx.gridlayout.widget.GridLayout")) {
                return;
            }
        }

        String message;
        if (!hasWidth && !hasHeight) {
            message = "Missing both `layout_width` and `layout_height` attributes";
        } else if (!hasWidth) {
            message = "Missing `layout_width` attribute";
        } else {
            message = "Missing `layout_height` attribute";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
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
        // Java method checking is not applicable for this XML attribute issue
    }
}