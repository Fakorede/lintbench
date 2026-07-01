package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RequiredAttributeDetector extends LayoutDetector {

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_CLASS = "class";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing `layout_width` or `layout_height` attributes",
                    "Every view in a layout must explicitly specify `android:layout_width` "
                            + "and `android:layout_height`. If these attributes are missing, a "
                            + "`InflateException` is thrown at runtime when the view is measured. "
                            + "You can also supply these dimensions through the `style` attribute. "
                            + "The `GridLayout` class is a special case and does not require a size.",
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
        // No project-wide aggregation is required; reports are emitted per element.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isViewElement(element)) {
            return;
        }

        if (isGridLayout(element)) {
            return;
        }

        boolean hasWidth = hasAttribute(element, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = hasAttribute(element, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String tag = element.getTagName();
            String msg = "Missing `layout_width` or `layout_height` attributes on <" + tag + ">";
            context.report(ISSUE, element, msg);
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
        // Not needed for XML attribute checking.
    }

    private static boolean isViewElement(@NonNull Element element) {
        String tag = element.getTagName();
        // These tags are structural/layout markers, not View instances.
        if (tag.equals("layout")      // data-binding root
                || tag.equals("data")
                || tag.equals("variable")
                || tag.equals("import")
                || tag.equals("merge")
                || tag.equals("include")
                || tag.equals("requestFocus")
                || tag.equals("tag")) {
            return false;
        }
        return true;
    }

    private static boolean isGridLayout(@NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals("GridLayout") || tag.endsWith(".GridLayout")) {
            return true;
        }
        if (tag.equals("view")) {
            String cls = element.getAttribute(ATTR_CLASS);
            if (cls != null && cls.endsWith(".GridLayout")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAttribute(@NonNull Element element, @NonNull String localName) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
            if (name.equals(localName)) {
                return true;
            }
        }
        return false;
    }
}