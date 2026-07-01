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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UFile;

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
        if (declaration.isInterface() || declaration.isEnum()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (isParcelizeAnnotated(declaration)) {
            return;
        }

        boolean isKotlin = isKotlin(context);

        UField staticCreator = findCreatorField(declaration, true);
        if (staticCreator != null) {
            if (isKotlin && !isJvmFieldAnnotated(staticCreator)) {
                reportJvmField(context, staticCreator);
            }
            return;
        }

        if (isKotlin) {
            UField nonStaticCreator = findCreatorField(declaration, false);
            if (nonStaticCreator != null) {
                if (!isJvmFieldAnnotated(nonStaticCreator)) {
                    reportJvmField(context, nonStaticCreator);
                }
                return;
            }
        }

        reportMissingCreator(context, declaration);
    }

    private static UField findCreatorField(@NotNull UClass cls, boolean requireStatic) {
        UField field = findCreatorFieldInClass(cls, requireStatic);
        if (field != null) {
            return field;
        }
        for (UClass inner : cls.getInnerClasses()) {
            field = findCreatorFieldInClass(inner, requireStatic);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private static UField findCreatorFieldInClass(@NotNull UClass cls, boolean requireStatic) {
        for (UField field : cls.getFields()) {
            if (!"CREATOR".equals(field.getName())) {
                continue;
            }
            if (requireStatic && !field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (!(field.getType() instanceof PsiClassType)) {
                continue;
            }
            PsiClassType classType = (PsiClassType) field.getType();
            if (classType.resolve() == null) {
                continue;
            }
            if (CREATOR_TYPE.equals(classType.resolve().getQualifiedName())) {
                return field;
            }
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
        UFile uFile = context.getUastFile();
        if (uFile == null) {
            return false;
        }
        PsiFile psi = uFile.getPsi();
        return psi != null && psi.getName().endsWith(".kt");
    }

    private static void reportMissingCreator(@NotNull JavaContext context, @NotNull UClass declaration) {
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not provide a CREATOR field"
        );
    }

    private static void reportJvmField(@NotNull JavaContext context, @NotNull UField field) {
        context.report(
                ISSUE,
                field,
                context.getNameLocation(field),
                "Field should be annotated with @JvmField"
        );
    }

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the Parcelable documentation, classes implementing Parcelable must "
                    + "declare a static field named CREATOR of type android.os.Parcelable.Creator.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );
}