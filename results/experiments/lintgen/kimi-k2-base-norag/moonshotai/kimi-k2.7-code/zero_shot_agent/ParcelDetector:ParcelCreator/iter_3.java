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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParcelDetector extends Detector implements Detector.JavaPsiScanner {

    private static final String PARCELABLE_CLS = "android.os.Parcelable";
    private static final String CREATOR_CLS = "android.os.Parcelable.Creator";

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
            new Implementation(ParcelDetector.class, EnumSet.of(Scope.JAVA_FILE)));

    @Nullable
    @Override
    public List<Class<? extends PsiElement>> getApplicableTypes() {
        return Collections.singletonList(PsiClass.class);
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NotNull final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {
                checkParcelableClass(context, aClass);
            }
        };
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void checkClass(@NotNull JavaContext context, @NotNull PsiClass node) {
    }

    @Nullable
    @Override
    public List<String> applicableInterfaces() {
        return null;
    }

    @Override
    public void checkInterface(@NotNull JavaContext context, @NotNull PsiClass node) {
    }

    private static void checkParcelableClass(JavaContext context, PsiClass node) {
        if (node.getName() == null || node.isInterface()) {
            return;
        }
        if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (!context.getEvaluator().implementsInterface(node, PARCELABLE_CLS, false)) {
            return;
        }

        for (PsiField field : node.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                PsiType type = field.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null
                            && context.getEvaluator()
                                    .implementsInterface(resolved, CREATOR_CLS, false)) {
                        return;
                    }
                }
            }
        }

        context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "This class implements android.os.Parcelable but does not define a static "
                        + "CREATOR field of type android.os.Parcelable.Creator");
    }
}