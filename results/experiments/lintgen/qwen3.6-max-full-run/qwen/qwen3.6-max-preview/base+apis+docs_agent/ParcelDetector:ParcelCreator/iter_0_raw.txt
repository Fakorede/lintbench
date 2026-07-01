package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.isEnum() || node.isAnnotationType() || node.hasModifier(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }

                boolean hasCreator = false;
                for (UField field : node.getFields()) {
                    if ("CREATOR".equals(field.getName()) && field.hasModifier(PsiModifier.STATIC)) {
                        hasCreator = true;
                        break;
                    }
                }

                if (!hasCreator) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "This class implements `Parcelable` but does not provide a `CREATOR` field");
                }
            }
        };
    }
}