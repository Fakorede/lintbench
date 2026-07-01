package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Node;

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

        PsiField creatorField = null;
        UClass creatorClass = null;

        PsiField directField = declaration.findFieldByName("CREATOR", false);
        if (directField != null) {
            creatorField = directField;
            creatorClass = declaration;
        } else {
            for (UClass inner : declaration.getInnerClasses()) {
                PsiField innerField = inner.findFieldByName("CREATOR", false);
                if (innerField != null) {
                    creatorField = innerField;
                    creatorClass = inner;
                    break;
                }
            }
        }

        if (creatorField == null) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field"
            );
        } else if (creatorClass != declaration) {
            boolean isKotlin = context.getPsiFile() != null && context.getPsiFile().getName().endsWith(".kt");
            if (isKotlin) {
                context.report(
                        ISSUE,
                        creatorField,
                        context.getNameLocation(creatorField),
                        "Field should be annotated with @JvmField"
                );
            } else {
                context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field"
                );
            }
        }
    }
}