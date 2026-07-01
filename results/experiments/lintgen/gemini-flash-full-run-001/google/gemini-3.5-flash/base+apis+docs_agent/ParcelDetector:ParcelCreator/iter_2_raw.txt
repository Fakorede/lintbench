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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
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
            "According to the `Parcelable` interface documentation, \"Classes implementing "
                    + "the Parcelable interface must also have a static field called `CREATOR`, "
                    + "which is an object implementing the `Parcelable.Creator` interface.\"",
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
        // Only check concrete classes
        if (declaration.isInterface()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)
                || declaration.getName() == null) {
            return;
        }

        // Kotlin @Parcelize generates the CREATOR field automatically
        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize")
                || declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        PsiField creatorField = null;

        // Check if the class itself has a CREATOR field
        for (PsiField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                creatorField = field;
                break;
            }
        }

        // In Kotlin, the CREATOR field might be inside a companion object
        if (creatorField == null) {
            for (PsiClass innerClass : declaration.getInnerClasses()) {
                for (PsiField field : innerClass.getFields()) {
                    if ("CREATOR".equals(field.getName())) {
                        creatorField = field;
                        break;
                    }
                }
                if (creatorField != null) {
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
        } else {
            boolean isPublic = creatorField.hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = creatorField.hasModifierProperty(PsiModifier.STATIC);
            if (!isPublic || !isStatic) {
                if (isKotlin(context)) {
                    context.report(
                            ISSUE,
                            creatorField,
                            context.getNameLocation(creatorField),
                            "Field should be annotated with @JvmField"
                    );
                } else {
                    context.report(
                            ISSUE,
                            creatorField,
                            context.getNameLocation(creatorField),
                            "The `CREATOR` field must be public and static"
                    );
                }
            }
        }
    }

    private static boolean isKotlin(JavaContext context) {
        String path = context.getPsiFile() != null ? context.getPsiFile().getName() : "";
        return path.endsWith(".kt") || path.endsWith(".kts");
    }
}