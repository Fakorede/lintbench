package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class TooManyViewsDetector extends ResourceXmlDetector implements Detector.XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";

    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mMaxDepthFound;
    private Element mDeepestElement;

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout such as `RelativeLayout` or `GridLayout`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        String max = System.getenv(MAX_DEPTH_ENV);
        if (max != null && !max.isEmpty()) {
            try {
                mMaxDepth = Integer.parseInt(max);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mMaxDepthFound = 0;
        mDeepestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int depth = getDepth(element);
        if (depth > mMaxDepthFound) {
            mMaxDepthFound = depth;
            mDeepestElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mMaxDepthFound > mMaxDepth && mDeepestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mDeepestElement,
                    xmlContext.getLocation(mDeepestElement),
                    "Layout has a depth of " + mMaxDepthFound
                            + " (maximum allowed is " + mMaxDepth + ")");
        }
    }

    private static int getDepth(@NonNull Element element) {
        int depth = 1;
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            depth++;
            parent = parent.getParentNode();
        }
        return depth;
    }
}