package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "MemoryAllocationsInDrawingCode",
            "Avoid allocating objects during a drawing or layout operation.",
            "These are called frequently, so a smooth UI can be interrupted by garbage collection pauses caused by the object allocations. " +
                    "The way this is generally handled is to allocate the needed objects up front and to reuse them for each drawing operation." +
                    "Some methods allocate memory on your behalf (such as `Bitmap.create`), and these should be handled in the same way.",
            Category.PERFORMANCE,
            6, // Priority
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("create");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UElement element, @NonNull String methodName) {
        if (element instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) element;
            UFile uFile = context.getUastFile();
            if (uFile != null && isDrawingOrLayoutMethod(uFile)) {
                Location location = context.getLocation(call);
                context.report(ISSUE, call, location, "Avoid allocating objects during a drawing or layout operation.");
            }
        }
    }

    private boolean isDrawingOrLayoutMethod(@NonNull UFile uFile) {
        for (UMethod method : uFile.getTopLevelMethods()) {
            if ("onDraw".equals(method.getName()) || "onLayout".equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

}