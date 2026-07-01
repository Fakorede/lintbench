package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_STYLE = "style";
    private static final String TAG_GRID_LAYOUT = "GridLayout";

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime. It's possible to specify these widths via styles as well. " +
            "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op: cleanup or final aggregation can be performed here if needed
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Skip non-view layout tags
        if (tag.equals("merge") || tag.equals("include") || tag.equals("requestFocus") ||
            tag.equals("tag") || tag.equals("fragment")) {
            return;
        }

        // GridLayout and its children do not require explicit dimensions
        if (isGridLayout(tag)) {
            return;
        }
        Element parent = (Element) element.getParentNode();
        if (parent != null && isGridLayout(parent.getTagName())) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        // Dimensions might be provided via a style reference
        if (element.hasAttribute(ATTR_STYLE)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required layout_width or layout_height attribute");
    }

    private boolean isGridLayout(@NonNull String tag) {
        return tag.equals(TAG_GRID_LAYOUT) || tag.endsWith(".GridLayout");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @Nullable PsiMethod method) {
        // No-op: Java/Kotlin source scanning not required for this XML-focused check
    }
}