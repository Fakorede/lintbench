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
import com.android.tools.lint.detector.api.XmlScannerConstants;
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
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_STYLE = "style";
    private static final String GRID_LAYOUT = "GridLayout";
    private static final String TAG_REQUEST_FOCUS = "requestFocus";
    private static final String TAG_MERGE = "merge";
    private static final String TAG_INCLUDE = "include";
    private static final String TAG_LAYOUT = "layout";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "Most views must specify explicit `android:layout_width` and "
                            + "`android:layout_height` attributes. The Android framework performs a "
                            + "runtime check and will throw an exception if a view is missing one of "
                            + "these sizes. The values can be set directly on the element or supplied "
                            + "through the `style` attribute. `GridLayout` is a special case and does "
                            + "not require explicit sizes on its children.",
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
        // No project-level aggregation is required for this check.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (isSpecialTag(tag)) {
            return;
        }

        if (isGridLayoutChild(element)) {
            return;
        }

        if (hasStyle(element)) {
            // The size may be defined in the referenced style; avoid false positives.
            return;
        }

        boolean hasWidth = hasAndroidAttribute(element, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = hasAndroidAttribute(element, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            StringBuilder message = new StringBuilder();
            message.append("Missing required ");
            if (!hasWidth && !hasHeight) {
                message.append("`android:layout_width` and `android:layout_height`");
            } else if (!hasWidth) {
                message.append("`android:layout_width`");
            } else {
                message.append("`android:layout_height`");
            }
            message.append(" attributes - most views must specify an explicit size");
            context.report(ISSUE, element, context.getLocation(element), message.toString());
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
        // No Java method-call analysis is performed by this detector.
    }

    private static boolean isSpecialTag(String tag) {
        return TAG_REQUEST_FOCUS.equals(tag)
                || TAG_MERGE.equals(tag)
                || TAG_INCLUDE.equals(tag)
                || TAG_LAYOUT.equals(tag);
    }

    private static boolean isGridLayoutChild(Element element) {
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            return GRID_LAYOUT.equals(parentElement.getTagName());
        }
        return false;
    }

    private static boolean hasStyle(Element element) {
        return element.hasAttribute(ATTR_STYLE);
    }

    private static boolean hasAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        return value != null && !value.isEmpty();
    }
}