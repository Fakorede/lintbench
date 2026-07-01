package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the `Parcelable` interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration instanceof UAnonymousClass) {
            return;
        }

        if (declaration.isEnum()) {
            return;
        }

        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize") ||
            declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        boolean isValid = false;
        if (creatorField != null) {
            boolean isPublic = creatorField.hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = creatorField.hasModifierProperty(PsiModifier.STATIC);
            PsiClass containingClass = creatorField.getContainingClass();
            if (isPublic && isStatic && containingClass != null && context.getEvaluator().areSignaturesEqual(containingClass, declaration)) {
                isValid = true;
            }
        }

        if (isValid) {
            return;
        }

        PsiField companionField = null;
        for (PsiClass inner : declaration.getInnerClasses()) {
            PsiField f = inner.findFieldByName("CREATOR", false);
            if (f != null) {
                companionField = f;
                break;
            }
        }

        if (companionField == null && creatorField != null) {
            PsiClass containingClass = creatorField.getContainingClass();
            if (containingClass != null && !context.getEvaluator().areSignaturesEqual(containingClass, declaration)) {
                companionField = creatorField;
            }
        }

        if (companionField != null) {
            context.report(
                    ISSUE,
                    companionField,
                    context.getNameLocation(companionField),
                    "Field should be annotated with @JvmField"
            );
            return;
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not provide a CREATOR field"
        );
    }
}