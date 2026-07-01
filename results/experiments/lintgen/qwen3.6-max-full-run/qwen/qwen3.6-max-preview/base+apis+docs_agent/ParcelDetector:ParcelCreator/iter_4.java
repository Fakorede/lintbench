package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
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

    private static final String PARCELIZE_ANDROIDX = "androidx.parcelize.Parcelize";
    private static final String PARCELIZE_KOTLINX = "kotlinx.android.parcel.Parcelize";
    private static final String JVM_FIELD = "kotlin.jvm.JvmField";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface() || node.isEnum() || node.isAnnotationType()) {
                    return;
                }
                if (node.getName() == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }

                if (evaluator.hasAnnotation(node, PARCELIZE_ANDROIDX) ||
                    evaluator.hasAnnotation(node, PARCELIZE_KOTLINX)) {
                    return;
                }

                UField creatorField = findCreatorField(node);

                if (creatorField != null) {
                    boolean isKotlin = context.getPsiFile().getName().endsWith(".kt");
                    if (isKotlin && !evaluator.hasAnnotation(creatorField, JVM_FIELD)) {
                        context.report(ISSUE, creatorField, context.getNameLocation(creatorField),
                                "Field should be annotated with @JvmField");
                    }
                    return;
                }

                context.report(ISSUE, node, context.getNameLocation(node),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field");
            }

            private UField findCreatorField(UClass cls) {
                for (UField field : cls.getFields()) {
                    if ("CREATOR".equals(field.getName())) {
                        return field;
                    }
                }
                for (UClass inner : cls.getInnerClasses()) {
                    for (UField field : inner.getFields()) {
                        if ("CREATOR".equals(field.getName())) {
                            return field;
                        }
                    }
                }
                return null;
            }
        };
    }
}