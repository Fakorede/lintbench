package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing "
                            + "the number of views in this layout.",
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
        return Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (isView(element)) {
            mViewCount++;
            if (mRootElement == null) {
                mRootElement = element;
            }
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (element == mRootElement) {
            int max = getMaxViewCount();
            if (mViewCount > max) {
                String message = String.format(
                        "Layout has too many views (%d); max is %d",
                        mViewCount, max);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }

    private boolean isView(Element element) {
        String tagName = element.getTagName();
        return !tagName.equals("layout")
                && !tagName.equals("data")
                && !tagName.equals("variable")
                && !tagName.equals("import")
                && !tagName.equals("merge");
    }

    private int getMaxViewCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // fall through to default
            }
        }
        return 80;
    }
}