package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import com.intellij.psi.PsiModifier;
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
            "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String PARCELABLE_FQN = "android.os.Parcelable";
    private static final String CREATOR_NAME = "CREATOR";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(Context context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, PARCELABLE_FQN, true)) {
                    return;
                }

                for (UField field : node.getFields()) {
                    if (CREATOR_NAME.equals(field.getName())) {
                        if (field.hasModifierProperty(PsiModifier.PUBLIC) &&
                            field.hasModifierProperty(PsiModifier.STATIC)) {
                            return;
                        }
                    }
                }

                context.report(ISSUE, context.getNameLocation(node),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field");
            }
        };
    }
}