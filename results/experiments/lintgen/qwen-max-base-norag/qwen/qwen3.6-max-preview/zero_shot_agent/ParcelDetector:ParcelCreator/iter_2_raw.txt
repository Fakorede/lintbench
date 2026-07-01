package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "ParcelCreator",
        "Missing Parcelable `CREATOR` field",
        "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
        Category.CORRECTNESS,
        8,
        Severity.FATAL,
        new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass cls) {
        if (cls.isInterface() || cls.isEnum() || cls.isAnnotationType() || cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.implementsInterface(cls, "android.os.Parcelable", false)) {
            return;
        }

        UField creatorField = null;
        for (UField field : cls.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                creatorField = field;
                break;
            }
        }

        if (creatorField != null && isValidCreator(evaluator, creatorField)) {
            return;
        }

        context.report(ISSUE, cls, context.getLocation(cls),
            "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }

    private boolean isValidCreator(@NotNull JavaEvaluator evaluator, @NotNull UField field) {
        if (!field.hasModifierProperty(PsiModifier.PUBLIC) ||
            !field.hasModifierProperty(PsiModifier.STATIC) ||
            !field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }

        PsiType type = field.getType();
        if (type == null) {
            return false;
        }

        PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
        if (typeClass == null) {
            return false;
        }

        return evaluator.implementsInterface(typeClass, "android.os.Parcelable.Creator", false) ||
               evaluator.implementsInterface(typeClass, "android.os.Parcelable.ClassLoaderCreator", false);
    }
}