package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {
    private static final String CLASS_PARCELABLE = "android.os.Parcelable";
    private static final String CLASS_PARCELABLE_CREATOR = "android.os.Parcelable.Creator";
    private static final String FIELD_CREATOR = "CREATOR";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "Any class that directly implements the `Parcelable` interface must also define a "
                            + "static field named `CREATOR` whose type is `android.os.Parcelable.Creator`.",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_PARCELABLE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        PsiElement javaPsi = declaration.getJavaPsi();
        if (!(javaPsi instanceof PsiClass)) {
            return;
        }

        PsiClass psiClass = (PsiClass) javaPsi;
        if (psiClass.isInterface()
                || psiClass.isEnum()
                || psiClass.isAnnotationType()
                || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (!directlyImplementsParcelable(psiClass)) {
            return;
        }

        if (!hasCreatorField(psiClass)) {
            Location location = context.getLocation(declaration);
            context.report(
                    ISSUE,
                    declaration,
                    location,
                    "This class directly implements `Parcelable` but does not define a static `CREATOR` field of type `android.os.Parcelable.Creator`");
        }
    }

    private static boolean directlyImplementsParcelable(PsiClass psiClass) {
        for (PsiClassType type : psiClass.getImplementsListTypes()) {
            PsiClass resolved = type.resolve();
            if (resolved != null && CLASS_PARCELABLE.equals(resolved.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCreatorField(PsiClass psiClass) {
        PsiField field = psiClass.findFieldByName(FIELD_CREATOR, false);
        if (field == null || !field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        PsiType type = field.getType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }

        PsiClass resolved = ((PsiClassType) type).resolve();
        return resolved != null && CLASS_PARCELABLE_CREATOR.equals(resolved.getQualifiedName());
    }
}