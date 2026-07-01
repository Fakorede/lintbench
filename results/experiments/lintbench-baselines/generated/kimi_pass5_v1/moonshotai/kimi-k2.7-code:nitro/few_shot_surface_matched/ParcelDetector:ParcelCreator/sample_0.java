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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String PARCELABLE_CREATOR = "android.os.Parcelable.Creator";

    public static final Issue ISSUE =
            Issue.create(
                            "ParcelCreator",
                            "Missing CREATOR field",
                            "Classes implementing the Parcelable interface must also have a "
                                    + "non-null static field called CREATOR of type "
                                    + "Parcelable.Creator.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ParcelDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        for (PsiField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName()) && field.hasModifierProperty(PsiModifier.STATIC)) {
                PsiType type = field.getType();
                if (type instanceof PsiClassType) {
                    PsiClassType classType = (PsiClassType) type;
                    PsiClass resolved = classType.resolve();
                    if (resolved != null && PARCELABLE_CREATOR.equals(resolved.getQualifiedName())) {
                        return;
                    }
                }
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not define a required static CREATOR "
                        + "field of type Parcelable.Creator");
    }
}