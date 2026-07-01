package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UVariable;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using android.media.ExifInterface",
            "The `android.media.ExifInterface` implementation has some known security bugs in older versions of Android. "
                    + "There is a new implementation available of this library in the support library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UCallExpression.class,
                UQualifiedReferenceExpression.class,
                UImportStatement.class,
                UVariable.class
        );
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                String qName = node.getQualifiedName();
                if (qName != null && qName.startsWith(EXIF_INTERFACE)) {
                    report(context, node);
                }
            }

            @Override
            public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
                String qName = node.getQualifiedName();
                if (qName != null && qName.startsWith(EXIF_INTERFACE)) {
                    report(context, node);
                }
            }

            @Override
            public void visitImportStatement(UImportStatement node) {
                String qName = node.getQualifiedName();
                if (qName != null && qName.startsWith(EXIF_INTERFACE)) {
                    report(context, node);
                }
            }

            @Override
            public void visitVariable(UVariable node) {
                if (node.getType() != null) {
                    String type = node.getType().getCanonicalText();
                    if (type.startsWith(EXIF_INTERFACE)) {
                        report(context, node);
                    }
                }
            }
        };
    }

    private void report(JavaContext context, UElement node) {
        context.report(ISSUE, node, context.getLocation(node),
                "Use the AndroidX ExifInterface library instead of `android.media.ExifInterface`");
    }
}