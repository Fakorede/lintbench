package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String MAX_VIEW_COUNT_ENV = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for "
                            + "performance. Consider using compound drawables or other "
                            + "tricks for reducing the number of views in this layout.\n\n"
                            + "The maximum view count defaults to "
                            + DEFAULT_MAX_VIEW_COUNT
                            + " but can be configured with the environment variable `"
                            + MAX_VIEW_COUNT_ENV
                            + "`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount;
    private int mMaxViewCount;

    @Override
    public void beforeCheckFile(Context context) {
        mViewCount = 0;
        mMaxViewCount = getMaxViewCount();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        if (isViewTag(element)) {
            mViewCount++;
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        if (element == context.document.getDocumentElement() && mViewCount > mMaxViewCount) {
            String message = String.format(
                    "This layout has %1$d views, which is more than the recommended maximum of %2$d",
                    mViewCount,
                    mMaxViewCount);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }

    private static boolean isViewTag(Element element) {
        String tag = element.getTagName();
        return !(tag.equals("include")
                || tag.equals("merge")
                || tag.equals("requestFocus")
                || tag.equals("tag")
                || tag.equals("layout")
                || tag.equals("data"));
    }

    private static int getMaxViewCount() {
        String value = System.getenv(MAX_VIEW_COUNT_ENV);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}