package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final Set<String> IGNORED_TAGS = new HashSet<>(Arrays.asList(
            "merge", "requestFocus", "data", "layout"
    ));

    public static final Issue TOO_DEEP_LAYOUT = Issue.create(
            "TooDeepLayout",
            "Layout has too many nested levels",
            "Layouts with too much nesting are bad for performance. Consider using a flatter layout such as `RelativeLayout` or `GridLayout`. The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final int mMaxDepth;
    private int mCurrentMaxDepth;
    private Element mDeepestElement;
    private XmlContext mContext;
    private boolean mCheckThisFile;

    public TooManyViewsDetector() {
        mMaxDepth = getMaxDepth();
    }

    private static int getMaxDepth() {
        String value = System.getenv(ENV_MAX_DEPTH);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        mCurrentMaxDepth = 0;
        mDeepestElement = null;
        mContext = null;
        File parent = context.file.getParentFile();
        mCheckThisFile = parent != null && parent.getName().startsWith("layout");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!mCheckThisFile) {
            return;
        }
        this.mContext = context;
        int depth = getDepth(element);
        if (depth > mCurrentMaxDepth) {
            mCurrentMaxDepth = depth;
            mDeepestElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (!mCheckThisFile || mDeepestElement == null || mContext == null) {
            return;
        }
        if (mCurrentMaxDepth > mMaxDepth) {
            Location location = mContext.getElementLocation(mDeepestElement);
            String message = String.format(
                    "Layout hierarchy is too deep: %1$d levels (max %2$d)",
                    mCurrentMaxDepth, mMaxDepth);
            mContext.report(TOO_DEEP_LAYOUT, location, message);
        }
    }

    private static int getDepth(@NotNull Element element) {
        int depth = 0;
        Node node = element;
        while (node instanceof Element) {
            String tag = ((Element) node).getTagName();
            if (!IGNORED_TAGS.contains(tag)) {
                depth++;
            }
            node = node.getParentNode();
        }
        return depth;
    }
}