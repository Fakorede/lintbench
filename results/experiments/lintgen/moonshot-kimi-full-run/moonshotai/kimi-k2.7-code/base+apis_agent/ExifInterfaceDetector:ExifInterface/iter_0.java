package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String ANDROIDX_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has known security bugs "
                            + "in older versions of Android. Prefer the support library "
                            + "implementation (`"
                            + ANDROIDX_EXIF_INTERFACE
                            + "`) instead.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(EXIF_INTERFACE);
    }

    @Override
    public void visitConstructor(
            @NotNull JavaContext context,
            @NotNull UCallExpression call,
            @NotNull PsiMethod constructor) {
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Use `"
                        + ANDROIDX_EXIF_INTERFACE
                        + "` instead of `"
                        + EXIF_INTERFACE
                        + "`");
    }
}