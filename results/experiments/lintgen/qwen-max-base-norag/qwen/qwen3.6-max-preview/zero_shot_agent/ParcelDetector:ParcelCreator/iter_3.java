package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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

        if (!context.getEvaluator().implementsInterface(cls, "android.os.Parcelable", false)) {
            return;
        }

        UField creatorField = null;
        for (UField field : cls.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                creatorField = field;
                break;
            }
        }

        boolean valid = false;
        if (creatorField != null) {
            if (creatorField.hasModifierProperty(PsiModifier.PUBLIC) &&
                creatorField.hasModifierProperty(PsiModifier.STATIC) &&
                creatorField.hasModifierProperty(PsiModifier.FINAL)) {
                PsiType type = creatorField.getType();
                if (type != null) {
                    PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
                    if (typeClass != null) {
                        valid = context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable.Creator", false) ||
                                context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable.ClassLoaderCreator", false);
                    }
                }
            }
        }

        if (valid) {
            return;
        }

        context.report(ISSUE, context.getLocation((UElement) cls),
            "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}