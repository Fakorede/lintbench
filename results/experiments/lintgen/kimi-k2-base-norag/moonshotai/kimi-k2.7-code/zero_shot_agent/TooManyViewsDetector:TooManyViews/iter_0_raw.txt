package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;

import java.util.Collection;

public class TooManyViewsDetector extends ResourceXmlDetector {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for "
                    + "reducing the number of views in this layout.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxViews = -1;
    private int mCount;
    private Element mRoot;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCount = 0;
        mRoot = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mRoot == null) {
            mRoot = element;
        }
        mCount++;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        int max = getMaxViews();
        if (mCount > max && mRoot != null) {
            XmlContext xmlContext = (XmlContext) context;
            String message = String.format(
                    "Layout has too many views: %1$d (maximum allowed: %2$d)",
                    mCount, max);
            xmlContext.report(ISSUE, mRoot, xmlContext.getLocation(mRoot), message);
        }
    }

    private int getMaxViews() {
        if (mMaxViews == -1) {
            String value = System.getenv(ENV_MAX_VIEW_COUNT);
            if (value != null) {
                try {
                    mMaxViews = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    mMaxViews = DEFAULT_MAX_VIEW_COUNT;
                }
            } else {
                mMaxViews = DEFAULT_MAX_VIEW_COUNT;
            }
        }
        return mMaxViews;
    }
}