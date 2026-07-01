package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String PARCELABLE_CREATOR = "android.os.Parcelable.Creator";
    private static final String CREATOR_NAME = "CREATOR";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the Parcelable interface documentation, classes implementing "
                            + "Parcelable must also have a static field called CREATOR, which is "
                            + "an object implementing the Parcelable.Creator interface. This "
                            + "field is used by the Android framework to instantiate your class "
                            + "from a Parcel.",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null || !directlyImplementsParcelable(psiClass)) {
            return;
        }

        for (UField field : declaration.getUastFields()) {
            PsiField psiField = field.getJavaPsi();
            if (psiField != null
                    && CREATOR_NAME.equals(psiField.getName())
                    && psiField.hasModifierProperty(PsiModifier.STATIC)
                    && isParcelableCreator(psiField.getType())) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(psiClass),
                "This class implements Parcelable but does not have a `CREATOR` field");
    }

    private boolean directlyImplementsParcelable(@NonNull PsiClass psiClass) {
        for (PsiClassType type : psiClass.getImplementsListTypes()) {
            PsiClass resolved = type.resolve();
            if (resolved != null && PARCELABLE.equals(resolved.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isParcelableCreator(@NonNull PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        PsiClass resolved = ((PsiClassType) type).resolve();
        return resolved != null && PARCELABLE_CREATOR.equals(resolved.getQualifiedName());
    }
}