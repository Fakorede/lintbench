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
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
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

        boolean hasCreator = false;

        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        if (creatorField != null) {
            hasCreator = true;
        } else {
            for (PsiClass innerClass : declaration.getInnerClasses()) {
                String name = innerClass.getName();
                if (name != null) {
                    if (name.equals("Companion")) {
                        if (innerClass.findFieldByName("CREATOR", false) != null) {
                            hasCreator = true;
                            break;
                        }
                        if (context.getEvaluator().implementsInterface(innerClass, "android.os.Parcelable.Creator", false)) {
                            hasCreator = true;
                            break;
                        }
                    } else if (name.equals("CREATOR")) {
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
                    "This class implements `Parcelable` but is missing a `CREATOR` field"
            );
        }
    }
}