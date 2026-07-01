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
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes " +
            "implementing the Parcelable interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass 
                || declaration.isInterface() 
                || declaration.isEnum()
                || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        // Skip if the class is annotated with @Parcelize (Kotlin Parcelize)
        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize") 
                || declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        boolean hasCreator = false;
        PsiField creatorField = declaration.findFieldByName("CREATOR", true);
        if (creatorField != null) {
            hasCreator = true;
        } else {
            // Check Kotlin Companion object
            for (PsiClass inner : declaration.getInnerClasses()) {
                if ("Companion".equals(inner.getName())) {
                    if (inner.findFieldByName("CREATOR", true) != null) {
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
                    "This class implements `Parcelable` but does not provide a `CREATOR` field"
            );
        }
    }
}