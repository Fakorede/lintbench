package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import java.util.Arrays;
import java.util.List;

public class ExifInterfaceDetector extends Detector implements Detector.JavaPsiScanner {

    private static final String PLATFORM_EXIF_INTERFACE = "android.media.ExifInterface";
    private static final String SUPPORT_EXIF_INTERFACE = "androidx.exifinterface.media.ExifInterface";

    private static final String MESSAGE =
            "Use `" + SUPPORT_EXIF_INTERFACE + "` from the support library instead of `"
                    + PLATFORM_EXIF_INTERFACE + "`";

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known security bugs "
                            + "in older versions of Android. There is a new implementation available "
                            + "of this library in the support library, which is preferable.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Arrays.asList(
                PsiImportStatement.class, PsiImportStaticStatement.class, PsiJavaCodeReferenceElement.class);
    }

    @Override
    public JavaElementVisitor createPsiVisitor(final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitImportStatement(PsiImportStatement statement) {
                PsiClass resolved = statement.resolveTargetClass();
                if (resolved != null
                        && PLATFORM_EXIF_INTERFACE.equals(resolved.getQualifiedName())) {
                    context.report(ISSUE, statement, context.getLocation(statement), MESSAGE);
                }
            }

            @Override
            public void visitImportStaticStatement(PsiImportStaticStatement statement) {
                PsiElement resolved = statement.resolve();
                if (resolved instanceof PsiMember) {
                    PsiClass containingClass = ((PsiMember) resolved).getContainingClass();
                    if (containingClass != null
                            && PLATFORM_EXIF_INTERFACE.equals(containingClass.getQualifiedName())) {
                        context.report(ISSUE, statement, context.getLocation(statement), MESSAGE);
                    }
                }
            }

            @Override
            public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                PsiElement parent = reference.getParent();
                if (parent instanceof PsiImportStatementBase) {
                    return;
                }
                if (parent instanceof PsiJavaCodeReferenceElement
                        && reference == ((PsiJavaCodeReferenceElement) parent).getQualifier()) {
                    return;
                }

                PsiElement resolved = reference.resolve();
                if (resolved instanceof PsiClass) {
                    String qualifiedName = ((PsiClass) resolved).getQualifiedName();
                    if (PLATFORM_EXIF_INTERFACE.equals(qualifiedName)) {
                        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE);
                    }
                } else if (resolved instanceof PsiMember) {
                    PsiClass containingClass = ((PsiMember) resolved).getContainingClass();
                    if (containingClass != null
                            && PLATFORM_EXIF_INTERFACE.equals(containingClass.getQualifiedName())) {
                        context.report(ISSUE, reference, context.getLocation(reference), MESSAGE);
                    }
                }
            }
        };
    }
}