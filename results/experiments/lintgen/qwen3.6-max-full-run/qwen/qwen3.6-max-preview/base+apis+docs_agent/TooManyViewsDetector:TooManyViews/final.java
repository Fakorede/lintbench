package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Element;

import java.util.Collection;

public class TooManyViewsDetector extends Detector implements XmlScanner {
    private int mViewCount;
    private static final String ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mViewCount = 0;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mViewCount++;
    }

    @Override
    public void afterCheckFile(Context context) {
        int max = DEFAULT_MAX;
        String env = System.getenv(ENV_VAR);
        if (env != null) {
            try {
                max = Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }

        if (mViewCount > max) {
            context.report(ISSUE, Location.create(context.file),
                    String.format("This layout has too many views (%1$d > %2$d)", mViewCount, max));
        }
    }
}