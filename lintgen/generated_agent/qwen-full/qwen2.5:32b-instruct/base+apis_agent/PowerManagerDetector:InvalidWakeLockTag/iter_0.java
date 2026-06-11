package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UCallExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.JavaScanner {

    public static final Issue INVALID_WAKE_LOCK_TAG = Issue.create(
            "InvalidWakeLockTag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation.",
            "Wake lock tags should be descriptive and follow a consistent naming convention. This helps with debugging and understanding the purpose of each wake lock.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(JavaContext context, UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null && "newWakeLock".equals(method.getName())) {
                    checkWakeLockTag(context, node);
                }
            }
        };
    }

    private void checkWakeLockTag(@NonNull JavaContext context, @NonNull UCallExpression callExpr) {
        List<? extends UElement> args = callExpr.getValueArguments();
        if (args.size() > 1 && args.get(1) instanceof ULiteralExpression) {
            String tag = ((ULiteralExpression) args.get(1)).getValue().toString();
            if (!isValidWakeLockTag(tag)) {
                Location location = context.getLocation(callExpr);
                context.report(INVALID_WAKE_LOCK_TAG, callExpr, location,
                        "Invalid wake lock tag: " + tag);
            }
        }
    }

    private boolean isValidWakeLockTag(@NonNull String tag) {
        // Example validation logic
        return tag.matches("[a-zA-Z0-9_]+");
    }
}