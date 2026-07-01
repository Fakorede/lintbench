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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UImportStatement;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known "
                            + "security bugs in older versions of Android. There is a new "
                            + "implementation available of this library in the support "
                            + "library, which is preferable.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

    public ExifInterfaceDetector() {}

    @Override
    public List<String> applicableConstructorTypes() {
        return Collections.singletonList(ANDROID_MEDIA_EXIF_INTERFACE);
    }

    @Override
    public void visitConstructor(
            JavaContext context, UCallExpression call, PsiMethod method) {
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Avoid using `android.media.ExifInterface`; use "
                        + "`androidx.exifinterface.media.ExifInterface` from the support library "
                        + "instead");
    }

    @Override
    public List<String> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class.getName());
    }
}