package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider "
                            + "using compound drawables or other tricks for reducing the "
                            + "number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured "
                            + "with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class, Scope.LAYOUT_RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private int mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;
    private int mViewCount;
    private Element mRoot;

    public TooManyViewsDetector() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                mMaxViewCount = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mViewCount = 0;
        mRoot = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mRoot == null) {
            mRoot = element;
        }

        String tagName = element.getTagName();
        if (isView(tagName)) {
            mViewCount++;
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (element == mRoot) {
            if (mViewCount > mMaxViewCount) {
                String message = String.format(
                        "%s has more than %d views (%d)",
                        mRoot.getTagName(), mMaxViewCount, mViewCount);
                context.report(ISSUE, mRoot, context.getLocation(mRoot), message);
            }
        }
    }

    private static boolean isView(String tagName) {
        switch (tagName) {
            case "layout":
            case "data":
            case "variable":
            case "import":
            case "merge":
                return false;
            default:
                return true;
        }
    }
}