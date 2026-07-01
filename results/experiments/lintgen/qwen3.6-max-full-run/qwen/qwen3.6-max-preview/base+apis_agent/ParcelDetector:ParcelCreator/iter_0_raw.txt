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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "ParcelCreator",
        "Missing Parcelable `CREATOR` field",
        "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isAbstract() || node.isInterface() || node.isAnnotationType()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }

                boolean hasCreator = false;
                for (UField field : node.getFields()) {
                    if ("CREATOR".equals(field.getName())) {
                        if (field.hasModifier(PsiModifier.PUBLIC)
                            && field.hasModifier(PsiModifier.STATIC)
                            && field.hasModifier(PsiModifier.FINAL)) {
                            hasCreator = true;
                            break;
                        }
                    }
                }

                if (!hasCreator) {
                    PsiElement nameElement = node.getNameIdentifier();
                    if (nameElement == null) {
                        nameElement = node;
                    }
                    context.report(ISSUE, node, context.getLocation(nameElement),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field");
                }
            }
        };
    }
}