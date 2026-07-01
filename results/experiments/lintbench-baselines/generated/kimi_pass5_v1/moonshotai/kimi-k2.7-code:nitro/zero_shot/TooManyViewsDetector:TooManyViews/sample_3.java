package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends ResourceXmlScanner {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEWS = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for "
                    + "reducing the number of views in this layout.\n\n"
                    + "The maximum view count defaults to 80 but can be configured "
                    + "with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private int mViews;
    private int mDepth;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mDepth == 0) {
            mViews = 0;
        }
        mDepth++;
        mViews++;
    }

    @Override
    public void visitElementEnd(XmlContext context, Element element) {
        mDepth--;
        if (mDepth == 0) {
            int max = getMaxViews();
            if (mViews > max) {
                Location location = context.getNameLocation(element);
                context.report(
                        ISSUE,
                        location,
                        String.format("Layout has too many views: %1$d (max %2$d)", mViews, max)
                );
            }
        }
    }

    private static int getMaxViews() {
        String value = System.getenv(ENV_MAX_VIEW_COUNT);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}