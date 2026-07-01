package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaElementVisitor;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParcelDetector extends Detector implements Detector.JavaPsiScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the Parcelable documentation, classes implementing the Parcelable interface "
                    + "must also have a static field called CREATOR, which is an object implementing "
                    + "the Parcelable.Creator interface. See "
                    + "https://developer.android.com/reference/android/os/Parcelable.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiClass.class);
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public JavaElementVisitor createPsiVisitor(@NotNull final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass psiClass) {
                super.visitClass(psiClass);

                if (psiClass == null
                        || psiClass.getName() == null
                        || psiClass.isInterface()
                        || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator()
                        .implementsInterface(psiClass, "android.os.Parcelable", false)) {
                    return;
                }

                for (PsiField field : psiClass.getFields()) {
                    if (!"CREATOR".equals(field.getName())
                            || !field.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }

                    PsiType type = field.getType();
                    if (type instanceof PsiClassType) {
                        PsiClass typeClass = ((PsiClassType) type).resolve();
                        if (typeClass != null
                                && context.getEvaluator()
                                        .implementsInterface(
                                                typeClass, "android.os.Parcelable.Creator", false)) {
                            return;
                        }
                    }
                }

                context.report(
                        ISSUE,
                        psiClass,
                        context.getNameLocation(psiClass),
                        "This class implements android.os.Parcelable but does not define a static "
                                + "CREATOR field of type android.os.Parcelable.Creator");
            }
        };
    }
}