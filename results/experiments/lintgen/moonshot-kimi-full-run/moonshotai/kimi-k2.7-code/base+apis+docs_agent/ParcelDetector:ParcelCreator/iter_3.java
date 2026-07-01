package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {
    private static final String CLASS_PARCELABLE = "android.os.Parcelable";
    private static final String CLASS_CREATOR = "android.os.Parcelable.Creator";
    private static final String FIELD_CREATOR = "CREATOR";
    private static final String COMPANION = "Companion";
    private static final String JVM_FIELD = "kotlin.jvm.JvmField";
    private static final String PARCELIZE = "kotlinx.parcelize.Parcelize";
    private static final String LEGACY_PARCELIZE = "kotlinx.android.parcel.Parcelize";

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, classes implementing the "
                            + "`Parcelable` interface must also have a static field called `CREATOR`, "
                            + "which is an object implementing the `Parcelable.Creator` interface.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psi = declaration.getJavaPsi();
        if (psi == null) {
            return;
        }

        if (psi.isInterface() || psi.isAnnotationType() || psi.isEnum()) {
            return;
        }
        if (psi.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (isParcelize(declaration)) {
            return;
        }

        if (hasValidCreatorField(psi)) {
            return;
        }

        for (PsiClass inner : psi.getInnerClasses()) {
            if (COMPANION.equals(inner.getName())) {
                PsiField creator = findCreatorField(inner);
                if (creator != null) {
                    if (!hasJvmField(creator)) {
                        context.report(
                                ISSUE,
                                creator,
                                context.getNameLocation(creator),
                                "Field should be annotated with @JvmField");
                    }
                    return;
                }
                break;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not provide a CREATOR field");
    }

    private static boolean isParcelize(@NonNull UClass cls) {
        return cls.findAnnotation(PARCELIZE) != null
                || cls.findAnnotation(LEGACY_PARCELIZE) != null;
    }

    private static boolean hasValidCreatorField(@NonNull PsiClass cls) {
        for (PsiField field : cls.getFields()) {
            if (isValidCreatorField(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidCreatorField(@NonNull PsiField field) {
        if (!FIELD_CREATOR.equals(field.getName())) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        return isCreatorType(field.getType());
    }

    @Nullable
    private static PsiField findCreatorField(@NonNull PsiClass cls) {
        for (PsiField field : cls.getFields()) {
            if (FIELD_CREATOR.equals(field.getName()) && isCreatorType(field.getType())) {
                return field;
            }
        }
        return null;
    }

    private static boolean isCreatorType(@NonNull PsiType type) {
        PsiClass cls = PsiTypesUtil.getPsiClass(type);
        if (cls != null && InheritanceUtil.isInheritor(cls, CLASS_CREATOR)) {
            return true;
        }
        String canonical = type.getCanonicalText();
        return CLASS_CREATOR.equals(canonical) || canonical.startsWith(CLASS_CREATOR + "<");
    }

    private static boolean hasJvmField(@NonNull PsiField field) {
        return field.getAnnotation(JVM_FIELD) != null;
    }
}