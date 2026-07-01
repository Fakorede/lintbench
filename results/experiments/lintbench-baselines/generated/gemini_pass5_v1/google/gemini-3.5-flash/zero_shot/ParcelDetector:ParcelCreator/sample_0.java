package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the `Parcelable` interface documentation, \"Classes " +
            "implementing the Parcelable interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            return;
        }
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip classes annotated with @Parcelize (Kotlin Parcelize)
        if (declaration.findAnnotation("kotlinx.parcelize.Parcelize") != null ||
            declaration.findAnnotation("kotlinx.android.parcel.Parcelize") != null) {
            return;
        }

        boolean hasCreator = false;
        for (PsiField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    hasCreator = true;
                    break;
                }
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field"
            );
        }
    }
}