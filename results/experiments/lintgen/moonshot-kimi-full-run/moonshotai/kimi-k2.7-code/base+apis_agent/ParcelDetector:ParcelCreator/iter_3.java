package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String CREATOR_TYPE = "android.os.Parcelable.Creator";
    private static final String PARCELIZE_OLD = "kotlinx.android.parcel.Parcelize";
    private static final String PARCELIZE_NEW = "kotlinx.parcelize.Parcelize";
    private static final String JVM_FIELD = "kotlin.jvm.JvmField";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()
                || declaration.isEnum()
                || declaration.isAnnotationType()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (isParcelizeAnnotated(declaration)) {
            return;
        }

        CreatorField creator = findCreator(context, declaration);
        if (creator == null) {
            reportMissingCreator(context, declaration);
            return;
        }

        if (creator.isStatic) {
            return;
        }

        if (isKotlin(context) && !isJvmFieldAnnotated(creator.field)) {
            reportJvmField(context, creator.field);
        } else if (!isKotlin(context)) {
            reportMissingCreator(context, declaration);
        }
    }

    private static CreatorField findCreator(@NotNull JavaContext context, @NotNull UClass cls) {
        CreatorField creator = findCreatorInClass(context, cls);
        if (creator != null) {
            return creator;
        }
        for (UClass inner : cls.getInnerClasses()) {
            creator = findCreator(context, inner);
            if (creator != null) {
                return creator;
            }
        }
        return null;
    }

    private static CreatorField findCreatorInClass(
            @NotNull JavaContext context, @NotNull UClass cls) {
        for (UField field : cls.getFields()) {
            if (!"CREATOR".equals(field.getName())) {
                continue;
            }
            PsiClass typeClass = context.evaluator.getTypeClass(field.getType());
            if (typeClass == null) {
                continue;
            }
            if (!context.evaluator.extendsClass(typeClass, CREATOR_TYPE, false)) {
                continue;
            }
            boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
            return new CreatorField(field, isStatic);
        }
        return null;
    }

    private static boolean isParcelizeAnnotated(@NotNull UClass cls) {
        for (PsiAnnotation annotation : cls.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (PARCELIZE_OLD.equals(name) || PARCELIZE_NEW.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJvmFieldAnnotated(@NotNull UField field) {
        for (PsiAnnotation annotation : field.getAnnotations()) {
            if (JVM_FIELD.equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKotlin(@NotNull JavaContext context) {
        String name = context.file.getName();
        return name.endsWith(".kt") || name.endsWith(".kts");
    }

    private static void reportMissingCreator(
            @NotNull JavaContext context, @NotNull UClass declaration) {
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not provide a CREATOR field");
    }

    private static void reportJvmField(@NotNull JavaContext context, @NotNull UField field) {
        context.report(
                ISSUE,
                field,
                context.getNameLocation(field),
                "Field should be annotated with @JvmField");
    }

    private static class CreatorField {
        final UField field;
        final boolean isStatic;

        CreatorField(UField field, boolean isStatic) {
            this.field = field;
            this.isStatic = isStatic;
        }
    }

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable CREATOR field",
                    "According to the Parcelable documentation, classes implementing Parcelable must "
                            + "declare a static field named CREATOR of type android.os.Parcelable.Creator.",
                    Category.CORRECTNESS,
                    9,
                    Severity.ERROR,
                    new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));
}