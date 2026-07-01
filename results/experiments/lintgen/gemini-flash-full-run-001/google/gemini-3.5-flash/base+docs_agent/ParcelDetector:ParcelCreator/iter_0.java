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
import org.jetbrains.uast.UAnonymousClass;
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
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration instanceof UAnonymousClass) {
            return;
        }

        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize") ||
            declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        if (!hasCreator(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but is missing a public static `CREATOR` field"
            );
        }
    }

    private boolean hasCreator(UClass uClass) {
        if (uClass.findFieldByName("CREATOR", false) != null) {
            return true;
        }
        for (UClass innerClass : uClass.getInnerClasses()) {
            String name = innerClass.getName();
            if (name != null && (name.equals("Companion") || name.equals("CREATOR"))) {
                if (innerClass.findFieldByName("CREATOR", false) != null) {
                    return true;
                }
                if (name.equals("CREATOR")) {
                    return true;
                }
            }
        }
        return false;
    }
}