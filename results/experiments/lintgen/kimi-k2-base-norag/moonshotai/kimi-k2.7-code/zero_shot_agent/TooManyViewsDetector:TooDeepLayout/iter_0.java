package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlElement;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;

public class TooManyViewsDetector extends ResourceXmlDetector {

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mDepth;
    private XmlElement mDeepestElement;
    private int mDeepestDepth;

    public static final Issue TOO_DEEP_LAYOUT = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout "
                    + "(such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 "
                    + "but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckEachProject(Context context) {
        mMaxDepth = DEFAULT_MAX_DEPTH;
        String maxDepth = System.getenv(ENV_MAX_DEPTH);
        if (maxDepth != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepth);
            } catch (NumberFormatException e) {
                // Ignore invalid values and keep the default.
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mDepth = 0;
        mDeepestElement = null;
        mDeepestDepth = 0;
    }

    @Override
    public void visitElement(XmlContext context, XmlElement element) {
        mDepth++;
        if (mDepth > mMaxDepth && mDepth > mDeepestDepth) {
            mDeepestDepth = mDepth;
            mDeepestElement = element;
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, XmlElement element) {
        mDepth--;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mDeepestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            String message = String.format(
                    "Layout hierarchy is too deep (depth %1$d, maximum %2$d)",
                    mDeepestDepth, mMaxDepth);
            xmlContext.report(TOO_DEEP_LAYOUT, mDeepestElement,
                    xmlContext.getLocation(mDeepestElement), message);
        }
    }
}