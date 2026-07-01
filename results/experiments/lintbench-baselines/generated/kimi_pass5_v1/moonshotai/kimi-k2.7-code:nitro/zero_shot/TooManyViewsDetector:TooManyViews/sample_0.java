package com.android.tools.lint.checks;

import java.util.Collection;

import org.w3c.dom.Element;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

public class TooManyViewsDetector extends ResourceXmlDetector {
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final String MAX_VIEW_COUNT_ENV = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int MAX_VIEW_COUNT = getMaxViewCount();

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for reducing the number of views in this layout.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private int mViewCount;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mViewCount = 0;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mViewCount++;
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mViewCount > MAX_VIEW_COUNT) {
            Element root = context.document.getDocumentElement();
            Location location = root != null
                    ? context.getElementLocation(root)
                    : context.getFileLocation();
            context.report(
                    ISSUE,
                    location,
                    String.format(
                            "Layout has %1$d views, which exceeds the maximum of %2$d",
                            mViewCount,
                            MAX_VIEW_COUNT));
        }
    }

    private static int getMaxViewCount() {
        String value = System.getenv(MAX_VIEW_COUNT_ENV);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}