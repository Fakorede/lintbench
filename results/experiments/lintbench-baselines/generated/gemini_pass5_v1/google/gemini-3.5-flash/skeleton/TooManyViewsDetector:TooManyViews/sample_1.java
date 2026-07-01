package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing the "
                            + "number of views in this layout. The maximum view count defaults to 80 "
                            + "but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount = 0;
    private Element mRootElement = null;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mRootElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (isViewElement(element)) {
            mViewCount++;
            if (mRootElement == null) {
                mRootElement = element;
            }
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        int max = getMaxViewCount();
        if (mViewCount > max && mRootElement != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            String message = String.format("This layout has too many views (%d); the limit is %d", mViewCount, max);
            xmlContext.report(ISSUE, mRootElement, xmlContext.getNameLocation(mRootElement), message);
        }
    }

    private static boolean isViewElement(Element element) {
        String tagName = element.getTagName();
        switch (tagName) {
            case "layout":
            case "data":
            case "variable":
            case "import":
            case "merge":
            case "requestFocus":
            case "tag":
                return false;
            default:
                return true;
        }
    }

    private int getMaxViewCount() {
        String value = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ignore, fall back to default
            }
        }
        return 80;
    }
}