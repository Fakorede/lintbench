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
import com.intellij.psi.PsiField;
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
                    "According to the `Parcelable` interface documentation, \"Classes "
                            + "implementing the Parcelable interface must also have a static "
                            + "field called `CREATOR`, which is an object implementing the "
                            + "`Parcelable.Creator` interface.\"",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.getName() == null
                || declaration.isInterface()
                || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize")
                || declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        boolean hasCreator = false;
        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        if (creatorField != null) {
            hasCreator = true;
        } else {
            for (PsiClass innerClass : declaration.getInnerClasses()) {
                if ("Companion".equals(innerClass.getName()) || "CREATOR".equals(innerClass.getName())) {
                    if (innerClass.findFieldByName("CREATOR", false) != null) {
                        hasCreator = true;
                        break;
                    }
                }
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but is missing a `CREATOR` field");
        }
    }
}