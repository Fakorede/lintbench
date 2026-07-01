package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String CLASS_PARCELABLE = "android.os.Parcelable";
    private static final String FIELD_CREATOR = "CREATOR";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "Classes implementing the `Parcelable` interface must also define a "
                            + "static field named `CREATOR` that implements "
                            + "`android.os.Parcelable.Creator`.",
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
        if (declaration.isInterface()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (!hasCreatorField(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "This class implements Parcelable but is missing the required "
                            + "`public static final CREATOR` field.");
        }
    }

    private static boolean hasCreatorField(UClass declaration) {
        for (UField field : declaration.getFields()) {
            if (FIELD_CREATOR.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }
}