package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String PARCELABLE = "android.os.Parcelable";

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the `Parcelable` interface documentation, \"Classes implementing "
                    + "the Parcelable interface must also have a static field called `CREATOR`, "
                    + "which is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    public ParcelDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        boolean hasCreator = false;
        for (UField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName()) && context.getEvaluator().isStatic(field)) {
                hasCreator = true;
                break;
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field");
        }
    }
}