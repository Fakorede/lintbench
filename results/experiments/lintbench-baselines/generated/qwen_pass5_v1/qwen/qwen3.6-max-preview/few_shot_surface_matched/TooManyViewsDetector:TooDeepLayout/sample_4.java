package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout " +
                    "(such as RelativeLayout or GridLayout). The default maximum depth is 10 but can be " +
                    "configured with the environment variable ANDROID_LINT_MAX_DEPTH.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;
    private static int sMaxDepth = -1;

    private int mCurrentDepth = 0;

    private static int getMaxDepth() {
        if (sMaxDepth == -1) {
            String env = System.getenv(ENV_MAX_DEPTH);
            if (env != null) {
                try {
                    sMaxDepth = Integer.parseInt(env);
                } catch (NumberFormatException e) {
                    sMaxDepth = DEFAULT_MAX_DEPTH;
                }
            } else {
                sMaxDepth = DEFAULT_MAX_DEPTH;
            }
        }
        return sMaxDepth;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentDepth = 0;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mCurrentDepth++;
        if (mCurrentDepth > getMaxDepth()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: " + mCurrentDepth + " (max is " + getMaxDepth() + ")");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mCurrentDepth--;
    }
}