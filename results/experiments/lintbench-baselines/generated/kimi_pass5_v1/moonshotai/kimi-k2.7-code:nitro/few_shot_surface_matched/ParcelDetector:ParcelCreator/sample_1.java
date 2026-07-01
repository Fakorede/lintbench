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
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String CREATOR = "CREATOR";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable CREATOR field",
                    "Classes implementing the Parcelable interface must also have a static field "
                            + "called CREATOR, which is an object implementing the "
                            + "Parcelable.Creator interface.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (PARCELABLE.equals(declaration.getQualifiedName())) {
            return;
        }
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        for (PsiField field : declaration.getFields()) {
            if (CREATOR.equals(field.getName()) && field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
        }
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not define a static CREATOR field");
    }
}