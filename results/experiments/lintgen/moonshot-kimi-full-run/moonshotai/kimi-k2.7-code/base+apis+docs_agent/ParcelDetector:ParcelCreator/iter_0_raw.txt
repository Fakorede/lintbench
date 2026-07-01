package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {
    private static final String CLASS_PARCELABLE = "android.os.Parcelable";
    private static final String CLASS_CREATOR = "android.os.Parcelable.Creator";
    private static final String FIELD_CREATOR = "CREATOR";

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, classes implementing the "
                            + "`Parcelable` interface must also have a static field called `CREATOR`, "
                            + "which is an object implementing the `Parcelable.Creator` interface.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.isAnnotationType() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(node, CLASS_PARCELABLE, false)) {
                    return;
                }

                if (hasCreatorField(node, evaluator)) {
                    return;
                }

                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This class implements Parcelable but is missing the required `CREATOR` field");
            }
        };
    }

    private static boolean hasCreatorField(@NonNull UClass cls, @NonNull JavaEvaluator evaluator) {
        for (UField field : cls.getFields()) {
            if (isCreatorField(field, evaluator)) {
                return true;
            }
        }

        UClass companion = cls.getCompanionObject();
        if (companion != null) {
            for (UField field : companion.getFields()) {
                if (isCreatorField(field, evaluator)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isCreatorField(@NonNull UField field, @NonNull JavaEvaluator evaluator) {
        if (!FIELD_CREATOR.equals(field.getName())) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }

        PsiType type = field.getType();
        PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
        if (typeClass != null && evaluator.inheritsFrom(typeClass, CLASS_CREATOR, false)) {
            return true;
        }

        String canonicalText = type.getCanonicalText();
        return canonicalText.equals(CLASS_CREATOR) || canonicalText.startsWith(CLASS_CREATOR + "<");
    }
}