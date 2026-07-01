package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    private static final int DEFAULT_MAX_VIEWS = 80;
    private static final String MAX_VIEWS_ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using compound drawables or other tricks for reducing the number of views in this layout.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private boolean mCheckThisFile;
    private int mViewCount;
    private Element mRootElement;

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        mViewCount = 0;
        mRootElement = null;
        mCheckThisFile = context.file != null
                && context.file.getParentFile() != null
                && context.file.getParentFile().getName().startsWith("layout");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!mCheckThisFile) {
            return;
        }

        if (mRootElement == null) {
            mRootElement = element;
        }

        if (!"requestFocus".equals(element.getTagName())) {
            mViewCount++;
        }
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (!mCheckThisFile || mRootElement == null) {
            return;
        }

        int maxViews = getMaxViews();
        if (mViewCount > maxViews) {
            Location location = ((XmlContext) context).getLocation(mRootElement);
            String message = String.format(
                    "Layout has too many views: %d (maximum allowed is %d)",
                    mViewCount, maxViews);
            context.report(ISSUE, location, message);
        }
    }

    private static int getMaxViews() {
        String envValue = System.getenv(MAX_VIEWS_ENV_VAR);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}