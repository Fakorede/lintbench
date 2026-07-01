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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UThisExpression;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_STYLE = "style";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ANDROID_GRID_LAYOUT = "android.widget.GridLayout";
    private static final String ANDROIDX_GRID_LAYOUT = "androidx.gridlayout.widget.GridLayout";
    private static final String LEGACY_GRID_LAYOUT = "android.support.v7.widget.GridLayout";

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height`"
                            + " attribute. If you fail to specify a size, an exception is thrown"
                            + " at runtime. These attributes can also be supplied via styles."
                            + " GridLayout is a special case and does not require an explicit"
                            + " size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            RequiredAttributeDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private final Set<String> mGridLayoutSubclasses = new HashSet<>();
    private final Set<String> mSelfSizedClasses = new HashSet<>();
    private final List<PendingView> mPendingViews = new ArrayList<>();

    private static class PendingView {
        final XmlContext context;
        final Element element;

        PendingView(XmlContext context, Element element) {
            this.context = context;
            this.element = element;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = getViewTag(element);

        if (tag.equals("merge")
                || tag.equals("include")
                || tag.equals("fragment")
                || tag.equals("requestFocus")
                || tag.equals("script")) {
            return;
        }

        if (isGridLayoutTag(tag)) {
            return;
        }

        if (tag.indexOf('.') != -1) {
            mPendingViews.add(new PendingView(context, element));
            return;
        }

        checkSize(context, element);
    }

    @Override
    public List<String> applicableSuperClasses() {
        List<String> classes = new ArrayList<>(3);
        classes.add(ANDROID_GRID_LAYOUT);
        classes.add(ANDROIDX_GRID_LAYOUT);
        classes.add(LEGACY_GRID_LAYOUT);
        return classes;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mGridLayoutSubclasses.add(qualifiedName);
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setLayoutParams");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        UExpression receiver = call.getReceiver();
        if (receiver != null && !(receiver instanceof UThisExpression)) {
            return;
        }

        UClass containingClass = null;
        UElement parent = call.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                containingClass = (UClass) parent;
                break;
            }
            parent = parent.getUastParent();
        }

        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName != null) {
                mSelfSizedClasses.add(qualifiedName);
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (PendingView pending : mPendingViews) {
            Element element = pending.element;
            String tag = getViewTag(element);

            if (isGridLayoutTag(tag)
                    || mGridLayoutSubclasses.contains(tag)
                    || mSelfSizedClasses.contains(tag)) {
                continue;
            }

            if (element.hasAttribute(ATTR_STYLE)) {
                continue;
            }

            checkSize(pending.context, element);
        }
        mPendingViews.clear();
    }

    private static void checkSize(XmlContext context, Element element) {
        boolean hasWidth = hasAttribute(element, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = hasAttribute(element, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        if (element.hasAttribute(ATTR_STYLE)) {
            return;
        }

        String message;
        if (!hasWidth && !hasHeight) {
            message = "Missing required layout_width and layout_height attributes";
        } else if (!hasWidth) {
            message = "Missing required layout_width attribute";
        } else {
            message = "Missing required layout_height attribute";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private static boolean hasAttribute(Element element, String name) {
        String value = element.getAttributeNS(ANDROID_URI, name);
        return value != null && !value.isEmpty();
    }

    private static String getViewTag(Element element) {
        String tag = element.getTagName();
        if (tag.equals("view")) {
            String className = element.getAttribute("class");
            if (!className.isEmpty()) {
                return className;
            }
        }
        return tag;
    }

    private static boolean isGridLayoutTag(String tag) {
        return tag.equals("GridLayout")
                || tag.equals(ANDROID_GRID_LAYOUT)
                || tag.equals(ANDROIDX_GRID_LAYOUT)
                || tag.equals(LEGACY_GRID_LAYOUT)
                || tag.endsWith(".GridLayout");
    }
}