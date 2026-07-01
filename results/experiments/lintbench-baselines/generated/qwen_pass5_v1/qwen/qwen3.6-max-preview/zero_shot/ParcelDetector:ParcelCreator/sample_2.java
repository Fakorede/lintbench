package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "ParcelCreator",
        "Missing Parcelable `CREATOR` field",
        "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
        Category.CORRECTNESS,
        8,
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
                if (node.isInterface() || node.isEnum() || node.isAnnotationType()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                if (!context.getEvaluator().implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }
                if (hasCreatorField(node, context)) {
                    return;
                }
                context.report(ISSUE, node, context.getNameLocation(node),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field");
            }
        };
    }

    private boolean hasCreatorField(@NotNull UClass uClass, @NotNull JavaContext context) {
        for (UField field : uClass.getFields()) {
            if (!"CREATOR".equals(field.getName())) {
                continue;
            }
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                continue;
            }
            if (!modifierList.hasModifierProperty(PsiModifier.STATIC) ||
                !modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            PsiType type = field.getType();
            if (context.getEvaluator().extendsClass(type, "android.os.Parcelable.Creator", true)) {
                return true;
            }
        }
        return false;
    }
}