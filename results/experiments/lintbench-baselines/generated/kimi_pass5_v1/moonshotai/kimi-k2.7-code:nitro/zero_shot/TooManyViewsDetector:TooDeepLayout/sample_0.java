package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {
    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout "
                    + "(such as `RelativeLayout` or `GridLayout`).",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxDepth;
    private int mCurrentMaxDepth;
    private Element mDeepestElement;
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mContext = (XmlContext) context;
        mMaxDepth = getMaxDepth();
        mCurrentMaxDepth = 0;
        mDeepestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int depth = getDepth(element);
        if (depth > mCurrentMaxDepth) {
            mCurrentMaxDepth = depth;
            mDeepestElement = element;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mDeepestElement != null && mCurrentMaxDepth > mMaxDepth && mContext != null) {
            mContext.report(ISSUE, mDeepestElement, mContext.getLocation(mDeepestElement),
                    String.format("Layout has too many nested levels: depth=%d, maximum=%d",
                            mCurrentMaxDepth, mMaxDepth));
        }
    }

    private static int getDepth(Element element) {
        int depth = 0;
        while (element != null) {
            depth++;
            element = getParentElement(element);
        }
        return depth;
    }

    private static Element getParentElement(Element element) {
        if (element.getParentNode() instanceof Element) {
            return (Element) element.getParentNode();
        }
        return null;
    }

    private static int getMaxDepth() {
        String value = System.getenv(ENV_MAX_DEPTH);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }
        return DEFAULT_MAX_DEPTH;
    }
}