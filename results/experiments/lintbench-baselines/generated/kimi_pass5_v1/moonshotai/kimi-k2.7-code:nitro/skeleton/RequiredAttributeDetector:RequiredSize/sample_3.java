package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit android:layout_width and android:layout_height "
                            + "attribute. If you fail to do so, the layout cannot be inflated and an "
                            + "exception is thrown at runtime. These attributes may also be supplied "
                            + "through a style. GridLayout is a special case that does not require "
                            + "these attributes.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";

    private static final String GRID_LAYOUT = "GridLayout";
    private static final String GRID_LAYOUT_V7 = "android.support.v7.widget.GridLayout";
    private static final String GRID_LAYOUT_ANDROIDX = "androidx.gridlayout.widget.GridLayout";

    private static final List<String> SPECIAL_TAGS =
            Collections.unmodifiableList(
                    java.util.Arrays.asList(
                            "merge",
                            "layout",
                            "include",
                            "requestFocus",
                            "item"
                    ));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // XML issues are reported immediately in visitElement.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (isSpecialTag(tag) || isGridLayout(tag)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            StringBuilder message = new StringBuilder("Missing ");
            if (!hasWidth) {
                message.append("android:layout_width");
            }
            if (!hasWidth && !hasHeight) {
                message.append(" and ");
            }
            if (!hasHeight) {
                message.append("android:layout_height");
            }
            message.append(" attribute");
            if (!hasWidth && !hasHeight) {
                message.append("s");
            }

            Location location = context.getLocation(element);
            context.report(ISSUE, location, message.toString());
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
        // This detector currently reports missing sizes only in XML layouts.
    }

    private static boolean isSpecialTag(String tag) {
        for (String special : SPECIAL_TAGS) {
            if (special.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGridLayout(String tag) {
        return GRID_LAYOUT.equals(tag)
                || GRID_LAYOUT_V7.equals(tag)
                || GRID_LAYOUT_ANDROIDX.equals(tag);
    }
}