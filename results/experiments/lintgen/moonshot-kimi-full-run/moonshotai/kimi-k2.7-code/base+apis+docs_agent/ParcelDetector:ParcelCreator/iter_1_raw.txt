package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class ParcelDetector extends Detector implements SourceCodeScanner {
    private static final String CLASS_PARCELABLE = "android.os.Parcelable";
    private static final String CLASS_CREATOR = "android.os.Parcelable.Creator";
    private static final String FIELD_CREATOR = "CREATOR";
    private static final String COMPANION = "Companion";

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

                if (!InheritanceUtil.isInheritor(node, CLASS_PARCELABLE)) {
                    return;
                }

                if (hasCreatorField(node)) {
                    return;
                }

                context.report(
                        ISSUE,
                        node,
                        context.getLocation((PsiElement) node),
                        "This class implements Parcelable but is missing the required `CREATOR` field");
            }
        };
    }

    private static boolean hasCreatorField(@NonNull PsiClass cls) {
        for (PsiField field : cls.getFields()) {
            if (isCreatorField(field)) {
                return true;
            }
        }

        for (PsiClass inner : cls.getInnerClasses()) {
            if (COMPANION.equals(inner.getName())) {
                for (PsiField field : inner.getFields()) {
                    if (isCreatorField(field)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isCreatorField(@NonNull PsiField field) {
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
        if (typeClass != null) {
            if (InheritanceUtil.isInheritor(typeClass, CLASS_CREATOR)) {
                return true;
            }
            String qName = typeClass.getQualifiedName();
            if (CLASS_CREATOR.equals(qName)) {
                return true;
            }
        }

        String canonicalText = type.getCanonicalText();
        return canonicalText.equals(CLASS_CREATOR) || canonicalText.startsWith(CLASS_CREATOR + "<");
    }
}