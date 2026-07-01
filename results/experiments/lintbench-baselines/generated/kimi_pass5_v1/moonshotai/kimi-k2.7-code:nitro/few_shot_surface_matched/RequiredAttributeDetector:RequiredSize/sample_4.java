package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.USuperExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height`"
                            + " attribute. There is a runtime check for this, so if you fail to"
                            + " specify a size, an exception is thrown at runtime.\n"
                            + "\n"
                            + "It's possible to specify these widths via styles as well."
                            + " GridLayout, as a special case, does not require you to specify a"
                            + " size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            RequiredAttributeDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LAYOUT_WIDTH = "layout_width";
    private static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    private static final String ATTR_CLASS = "class";

    private static final String GRID_LAYOUT = "android.widget.GridLayout";
    private static final String GRID_LAYOUT_V7 = "android.support.v7.widget.GridLayout";
    private static final String GRID_LAYOUT_ANDROIDX = "androidx.gridlayout.widget.GridLayout";

    private static final Set<String> NON_VIEW_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "merge",
                            "include",
                            "requestFocus",
                            "eat-comment",
                            "item",
                            "layout",
                            "data",
                            "variable",
                            "import"));

    private final List<PendingIssue> mPending = new ArrayList<>();
    private final Set<String> mGridLayoutClasses =
            new HashSet<>(
                    Arrays.asList(GRID_LAYOUT, GRID_LAYOUT_V7, GRID_LAYOUT_ANDROIDX));

    private static final class PendingIssue {
        final XmlContext context;
        final Element element;
        final boolean missingWidth;
        final boolean missingHeight;

        PendingIssue(
                XmlContext context,
                Element element,
                boolean missingWidth,
                boolean missingHeight) {
            this.context = context;
            this.element = element;
            this.missingWidth = missingWidth;
            this.missingHeight = missingHeight;
        }
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String name = file.getName();
        if (name.endsWith(".java") || name.endsWith(".kt")) {
            return true;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String folder = parent.getName();
        return folder.startsWith("layout") && name.endsWith(".xml");
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (PendingIssue issue : mPending) {
            Element element = issue.element;

            if (!isViewElement(element)) {
                continue;
            }
            if (isGridLayout(element)) {
                continue;
            }

            Node parent = element.getParentNode();
            if (parent instanceof Element && isGridLayout((Element) parent)) {
                continue;
            }

            issue.context.report(
                    ISSUE,
                    element,
                    issue.context.getLocation(element),
                    "Missing layout_width or layout_height attributes");
        }
        mPending.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isViewElement(element)) {
            return;
        }

        boolean missingWidth = !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean missingHeight = !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (missingWidth || missingHeight) {
            mPending.add(new PendingIssue(context, element, missingWidth, missingHeight));
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("GridLayout");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!method.isConstructor()) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null || !isKnownGridLayout(qualifiedName)) {
            return;
        }

        if (node.getReceiver() instanceof USuperExpression) {
            UClass caller = UastUtils.getParentOfType(node, UClass.class, false);
            if (caller != null && caller.getQualifiedName() != null) {
                mGridLayoutClasses.add(caller.getQualifiedName());
            }
        }
    }

    private boolean isViewElement(Element element) {
        String tag = element.getTagName();
        if (tag == null) {
            return false;
        }

        if ("view".equals(tag)) {
            return element.hasAttributeNS(ANDROID_URI, ATTR_CLASS);
        }

        return !NON_VIEW_TAGS.contains(tag);
    }

    private boolean isGridLayout(Element element) {
        String tag = element.getTagName();
        if (tag == null) {
            return false;
        }
        return mGridLayoutClasses.contains(tag);
    }

    private boolean isKnownGridLayout(String name) {
        return GRID_LAYOUT.equals(name)
                || GRID_LAYOUT_V7.equals(name)
                || GRID_LAYOUT_ANDROIDX.equals(name);
    }
}