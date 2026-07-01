package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV_VAR = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting are bad for performance. Consider using a flatter "
                    + "layout such as `RelativeLayout` or `GridLayout`. The default maximum "
                    + "depth is 10 but can be configured with the environment variable "
                    + "`ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mDepth;
    private int mMaxDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(Context context) {
        mDepth = 0;
        mReported = false;
        mMaxDepth = DEFAULT_MAX_DEPTH;
        if (context instanceof XmlContext) {
            LintClient client = ((XmlContext) context).getClient();
            String value = client.getEnvironmentVariable(MAX_DEPTH_ENV_VAR);
            if (value != null) {
                try {
                    mMaxDepth = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // fall back to default
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mDepth++;
        if (!mReported && mDepth > mMaxDepth) {
            String message = String.format(
                    "Layout hierarchy is too deep (depth %1$d, maximum %2$d)",
                    mDepth, mMaxDepth);
            context.report(ISSUE, element, context.getLocation(element), message);
            mReported = true;
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mDepth--;
    }
}