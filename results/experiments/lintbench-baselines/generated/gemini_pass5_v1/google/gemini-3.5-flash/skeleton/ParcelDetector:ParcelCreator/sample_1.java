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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable "
                            + "interface must also have a static field called `CREATOR`, which is an object "
                            + "implementing the `Parcelable.Creator` interface.\"",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Exclude non-static inner classes
        if (psiClass.getContainingClass() != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        // Exclude classes annotated with @Parcelize
        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize")
                || declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        PsiField creatorField = psiClass.findFieldByName("CREATOR", false);
        if (creatorField == null) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not declare a `CREATOR` field");
            return;
        }

        if (!creatorField.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                    ISSUE,
                    creatorField,
                    context.getLocation(creatorField),
                    "The `CREATOR` field must be `static`");
            return;
        }

        if (!creatorField.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                    ISSUE,
                    creatorField,
                    context.getLocation(creatorField),
                    "The `CREATOR` field must be `public`");
            return;
        }

        PsiType type = creatorField.getType();
        boolean isCreator = false;
        if (type != null) {
            String canonical = type.getCanonicalText();
            if (canonical.startsWith("android.os.Parcelable.Creator")
                    || canonical.startsWith("android.os.Parcelable.ClassLoaderCreator")) {
                isCreator = true;
            } else {
                for (PsiType superType : type.getSuperTypes()) {
                    String superCanonical = superType.getCanonicalText();
                    if (superCanonical.startsWith("android.os.Parcelable.Creator")
                            || superCanonical.startsWith("android.os.Parcelable.ClassLoaderCreator")) {
                        isCreator = true;
                        break;
                    }
                }
            }
        }

        if (!isCreator) {
            context.report(
                    ISSUE,
                    creatorField,
                    context.getLocation(creatorField),
                    "The `CREATOR` field must implement `android.os.Parcelable.Creator`");
        }
    }
}