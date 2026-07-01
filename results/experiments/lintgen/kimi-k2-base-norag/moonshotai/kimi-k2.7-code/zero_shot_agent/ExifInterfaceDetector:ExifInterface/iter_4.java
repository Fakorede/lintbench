package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. There is a new implementation available of this library in the support library, which is preferable.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Override
    public void visitUElement(@NonNull JavaContext context, @NonNull UElement node) {
        UImportStatement importStatement = (UImportStatement) node;
        UReferenceExpression reference = importStatement.getImportReference();
        if (reference == null) {
            return;
        }

        String fqcn = reference.asRenderString();
        if ("android.media.ExifInterface".equals(fqcn)) {
            context.report(
                    ISSUE,
                    importStatement,
                    context.getLocation(importStatement),
                    "Use `androidx.exifinterface.media.ExifInterface` from the support library instead of `android.media.ExifInterface`");
        }
    }
}