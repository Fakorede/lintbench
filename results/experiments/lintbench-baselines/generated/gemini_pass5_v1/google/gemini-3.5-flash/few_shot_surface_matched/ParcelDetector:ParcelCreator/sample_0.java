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
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "ParcelCreator",
                            "Missing Parcelable CREATOR field",
                            "According to the `Parcelable` interface documentation, \"Classes "
                                    + "implementing the Parcelable interface must also have a static "
                                    + "field called `CREATOR`, which is an object implementing the "
                                    + "`Parcelable.Creator` interface.\"",
                            Category.CORRECTNESS,
                            5,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private static final String PARCELABLE_INTERFACE = "android.os.Parcelable";

    public ParcelDetector() {}

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(PARCELABLE_INTERFACE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || PARCELABLE_INTERFACE.equals(qualifiedName)) {
            return;
        }

        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        if (creatorField == null) {
            for (PsiClass innerClass : declaration.getInnerClasses()) {
                if (innerClass.findFieldByName("CREATOR", false) != null) {
                    return;
                }
            }
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field");
        }
    }
}