package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"\n" +
            "Reference: https://developer.android.com/reference/android/os/Parcelable.html",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.isEnum() || node.isAbstract()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, "android.os.Parcelable", false)) {
                    return;
                }

                if (hasCreatorField(evaluator, node)) {
                    return;
                }

                context.report(ISSUE, node, context.getNameLocation(node),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field");
            }
        };
    }

    private boolean hasCreatorField(@NonNull JavaEvaluator evaluator, @NonNull UClass node) {
        for (UField field : node.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                if (evaluator.isPublic(field) && evaluator.isStatic(field) && evaluator.isFinal(field)) {
                    return true;
                }
            }
        }
        return false;
    }
}