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
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider"
                            + " using compound drawables or other tricks for reducing the number"
                            + " of views in this layout.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mCount;
    private int mMax;
    private boolean mReported;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCount = 0;
        mReported = false;

        String maxViewCount = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (maxViewCount != null) {
            try {
                mMax = Integer.parseInt(maxViewCount);
            } catch (NumberFormatException e) {
                mMax = 80;
            }
        } else {
            mMax = 80;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (mCount > mMax
                && !mReported
                && element == element.getOwnerDocument().getDocumentElement()) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    String.format(
                            "Layout has %1$d views, which is more than the %2$d maximum",
                            mCount, mMax));
            mReported = true;
        }
    }
}