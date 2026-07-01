package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends Detector implements Detector.XmlScanner {

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

    public static final Issue TOO_DEEP_LAYOUT = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting are bad for performance. Consider using a flatter "
                    + "layout such as RelativeLayout or GridLayout. The default maximum depth is "
                    + DEFAULT_MAX_DEPTH
                    + " but can be configured with the ANDROID_LINT_MAX_DEPTH environment variable.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private final int mMaxDepth;
    private boolean mReported;

    public TooManyViewsDetector() {
        String maxDepth = System.getenv(ENV_MAX_DEPTH);
        int configured = DEFAULT_MAX_DEPTH;
        if (maxDepth != null && !maxDepth.isEmpty()) {
            try {
                configured = Integer.parseInt(maxDepth);
            } catch (NumberFormatException ignored) {
            }
        }
        mMaxDepth = configured;
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
        mReported = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mReported) {
            return;
        }

        int depth = computeDepth(element);
        if (depth > mMaxDepth) {
            mReported = true;
            context.report(
                    TOO_DEEP_LAYOUT,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Layout hierarchy is too deep (depth %1$d, maximum %2$d). "
                                    + "Consider using a flatter layout such as RelativeLayout or GridLayout.",
                            depth,
                            mMaxDepth));
        }
    }

    private static int computeDepth(Element element) {
        int depth = 0;
        Node node = element;
        while (node instanceof Element) {
            depth++;
            node = node.getParentNode();
        }
        return depth;
    }
}