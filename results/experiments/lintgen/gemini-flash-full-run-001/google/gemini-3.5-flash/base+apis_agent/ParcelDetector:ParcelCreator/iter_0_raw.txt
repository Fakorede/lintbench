package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "Classes implementing the `Parcelable` interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",
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
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        // Anonymous classes or non-static inner classes cannot have static CREATOR fields
        if (declaration.getName() == null) {
            return;
        }
        if (declaration.getContainingClass() != null && !context.getEvaluator().isStatic(declaration)) {
            return;
        }

        // Skip classes annotated with @Parcelize (Kotlin Parcelize extension)
        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize") ||
                declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
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
        if (uClass.findFieldByName("CREATOR", true) != null) {
            return true;
        }
        for (UClass inner : uClass.getInnerClasses()) {
            if ("CREATOR".equals(inner.getName())) {
                return true;
            }
            if (inner.findFieldByName("CREATOR", true) != null) {
                return true;
            }
        }
        return false;
    }
}