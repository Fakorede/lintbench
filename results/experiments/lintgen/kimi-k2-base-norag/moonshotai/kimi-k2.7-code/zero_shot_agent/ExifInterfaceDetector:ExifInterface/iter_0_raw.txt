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
import com.intellij.psi.PsiClass;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;

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
    public List<String> applicableToClassRefs() {
        return Collections.singletonList("android.media.ExifInterface");
    }

    @Override
    public void visitClassReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull PsiClass resolved) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `androidx.exifinterface.media.ExifInterface` from the support library instead of `android.media.ExifInterface`");
    }
}