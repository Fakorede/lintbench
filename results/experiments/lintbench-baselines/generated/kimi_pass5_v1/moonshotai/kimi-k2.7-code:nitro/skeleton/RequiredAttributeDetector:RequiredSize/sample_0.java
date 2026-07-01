package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_STYLE = "style";
    private static final String ATTR_CLASS = "class";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` "
                            + "attribute. There is a runtime check for this, so if you fail to "
                            + "specify a size, an exception is thrown at runtime. It is possible "
                            + "to specify these widths via styles as well. GridLayout, as a "
                            + "special case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // No cross-file analysis needed.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = getTagName(element);
        if (tagName == null) {
            return;
        }

        if (isNonViewTag(tagName)) {
            return;
        }

        if (isGridLayout(element)) {
            return;
        }

        if (element.hasAttribute(ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = hasAttribute(element, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = hasAttribute(element, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "Missing `layout_width` and `layout_height` attributes";
            } else if (!hasWidth) {
                message = "Missing `layout_width` attribute";
            } else {
                message = "Missing `layout_height` attribute";
            }

            Location location = context.getElementLocation(element);
            context.report(ISSUE, location, message);
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        // No Java call checks are needed for this issue.
    }

    private static String getTagName(Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }
        return tagName;
    }

    private static boolean isNonViewTag(String tagName) {
        return "include".equals(tagName)
                || "merge".equals(tagName)
                || "fragment".equals(tagName)
                || "requestFocus".equals(tagName)
                || "layout".equals(tagName);
    }

    private static boolean isGridLayout(Element element) {
        String tagName = getTagName(element);
        if ("GridLayout".equals(tagName)) {
            return true;
        }
        if ("view".equals(tagName) || "View".equals(tagName)) {
            String className = element.getAttribute(ATTR_CLASS);
            if (className != null) {
                return className.endsWith(".GridLayout") || "GridLayout".equals(className);
            }
        }
        return false;
    }

    private static boolean hasAttribute(Element element, String localName) {
        return element.hasAttributeNS(ANDROID_URI, localName);
    }
}