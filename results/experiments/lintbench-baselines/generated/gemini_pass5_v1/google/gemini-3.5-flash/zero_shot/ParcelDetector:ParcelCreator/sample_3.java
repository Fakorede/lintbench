package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the `Parcelable` interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.getName() == null) {
            return;
        }

        for (UAnnotation annotation : declaration.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if ("kotlinx.parcelize.Parcelize".equals(qualifiedName) ||
                    "kotlinx.android.parcel.Parcelize".equals(qualifiedName)) {
                return;
            }
        }

        if (!hasCreatorField(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field"
            );
        }
    }

    private boolean hasCreatorField(UClass uClass) {
        if (uClass.findFieldByName("CREATOR", false) != null) {
            return true;
        }
        for (UClass inner : uClass.getInnerClasses()) {
            String name = inner.getName();
            if ("Companion".equals(name) || "CREATOR".equals(name)) {
                if (inner.findFieldByName("CREATOR", false) != null) {
                    return true;
                }
                for (PsiClassType type : inner.getSuperTypes()) {
                    String canonical = type.getCanonicalText();
                    if (canonical != null && canonical.contains("Parcelable.Creator")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}