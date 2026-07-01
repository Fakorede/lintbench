package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider using"
                            + " compound drawables or other tricks for reducing the number of"
                            + " views in this layout. The maximum view count defaults to 80 but"
                            + " can be configured with the environment variable"
                            + " `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";

    private int mCount;
    private int mMax;
    private boolean mCheckLayout;
    private org.w3c.dom.Element mRoot;

    @Override
    public void beforeCheckFile(Context context) {
        super.beforeCheckFile(context);
        mCount = 0;
        mRoot = null;
        mCheckLayout = context.getResourceFolderType() == ResourceFolderType.LAYOUT;
        mMax = DEFAULT_MAX_VIEW_COUNT;
        String maxProperty = System.getenv(ENV_MAX_VIEW_COUNT);
        if (maxProperty != null) {
            try {
                mMax = Integer.parseInt(maxProperty);
            } catch (NumberFormatException e) {
                // Keep the default value.
            }
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!mCheckLayout) {
            return;
        }
        if (mRoot == null) {
            mRoot = element;
        }
        mCount++;
    }

    @Override
    public void visitElementAfter(XmlContext context, org.w3c.dom.Element element) {
        if (element == mRoot && mCount > mMax) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Layout has too many views: %1$d (max %2$d)",
                            mCount,
                            mMax));
        }
    }
}