package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
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
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, "
                            + "an exception is thrown at runtime. It's possible to specify these widths "
                            + "via styles as well. GridLayout, as a special case, does not require you "
                            + "to specify a size.",
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
        return java.util.Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (tagName.isEmpty() || isNonView(tagName)) {
            return;
        }

        // Skip if parent is GridLayout
        org.w3c.dom.Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String parentTagName = parent.getTagName();
            if (parentTagName.equals("GridLayout") || parentTagName.endsWith(".GridLayout")) {
                return;
            }
        }

        // Skip if there's a style attribute
        if (element.hasAttribute("style")) {
            return;
        }

        String namespace = "http://schemas.android.com/apk/res/android";
        boolean hasWidth = element.hasAttributeNS(namespace, "layout_width");
        boolean hasHeight = element.hasAttributeNS(namespace, "layout_height");

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The view is missing both `layout_width` and `layout_height` attributes";
            } else if (!hasWidth) {
                message = "The view is missing the `layout_width` attribute";
            } else {
                message = "The view is missing the `layout_height` attribute";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private boolean isNonView(String tagName) {
        switch (tagName) {
            case "merge":
            case "include":
            case "requestFocus":
            case "tag":
            case "intent":
            case "meta-data":
            case "annotation":
                return true;
            default:
                if (Character.isLowerCase(tagName.charAt(0))) {
                    return !tagName.equals("view") && !tagName.equals("fragment");
                }
                return false;
        }
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