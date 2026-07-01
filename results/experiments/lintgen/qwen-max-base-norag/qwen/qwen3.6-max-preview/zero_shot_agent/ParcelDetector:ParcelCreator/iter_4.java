package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
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

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass cls) {
                if (cls.isInterface() || cls.isEnum() || cls.isAnnotationType() || cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(cls, "android.os.Parcelable", true)) {
                    return;
                }

                UField creatorField = cls.findFieldByName("CREATOR", false);
                boolean isKotlin = context.isKotlin(cls);

                if (creatorField == null) {
                    context.report(ISSUE, context.getLocation(cls),
                        "This class implements Parcelable but does not provide a CREATOR field");
                    return;
                }

                boolean isPublic = creatorField.hasModifierProperty(PsiModifier.PUBLIC);
                boolean isStatic = creatorField.hasModifierProperty(PsiModifier.STATIC);
                boolean isFinal = creatorField.hasModifierProperty(PsiModifier.FINAL);

                if (isKotlin && !isStatic) {
                    if (!context.getEvaluator().hasAnnotation(creatorField, "kotlin.jvm.JvmField")) {
                        context.report(ISSUE, context.getLocation(creatorField),
                            "Field should be annotated with @JvmField");
                        return;
                    }
                }

                if (!isPublic || !isStatic || !isFinal) {
                    context.report(ISSUE, context.getLocation(creatorField),
                        "The CREATOR field must be public static final");
                    return;
                }

                PsiType type = creatorField.getType();
                if (type != null) {
                    PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
                    if (typeClass != null) {
                        boolean validType = context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable.Creator", true) ||
                                            context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable.ClassLoaderCreator", true);
                        if (!validType) {
                            context.report(ISSUE, context.getLocation(creatorField),
                                "The CREATOR field must implement Parcelable.Creator");
                        }
                    }
                }
            }
        };
    }
}