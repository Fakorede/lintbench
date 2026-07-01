package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.XmlScannerConstants.ALL;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final int MAX_VIEW_COUNT;

    static {
        int max = DEFAULT_MAX_VIEW_COUNT;
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                max = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // fall back to default
            }
        }
        MAX_VIEW_COUNT = max;
    }

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for reducing "
                    + "the number of views in this layout.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private int mCount;
    private Element mRoot;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCount = 0;
        mRoot = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mCount++;
        if (mRoot == null) {
            mRoot = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mCount > MAX_VIEW_COUNT && mRoot != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    xmlContext.getElementLocation(mRoot),
                    "Layout has too many views (" + mCount
                            + ", maximum allowed: " + MAX_VIEW_COUNT + ")"
            );
        }
    }
}