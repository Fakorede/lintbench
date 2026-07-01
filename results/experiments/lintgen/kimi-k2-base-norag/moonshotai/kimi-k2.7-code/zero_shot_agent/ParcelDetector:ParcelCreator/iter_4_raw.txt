package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String PARCELABLE_CLS = "android.os.Parcelable";
    private static final String CREATOR_CLS = "android.os.Parcelable.Creator";

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the Parcelable documentation, classes implementing the Parcelable interface "
                    + "must also have a static field called CREATOR, which is an object implementing "
                    + "the Parcelable.Creator interface. See "
                    + "https://developer.android.com/reference/android/os/Parcelable.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, EnumSet.of(Scope.JAVA_FILE)));

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                checkParcelableClass(context, node);
            }
        };
    }

    private static void checkParcelableClass(JavaContext context, UClass node) {
        if (node.getName() == null) {
            return;
        }

        PsiClass psiClass = node.getJavaPsi();
        if (psiClass == null
                || psiClass.isInterface()
                || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (!context.getEvaluator().implementsInterface(psiClass, PARCELABLE_CLS, false)) {
            return;
        }

        for (UField field : node.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                PsiType type = field.getType();
                if (type instanceof PsiClassType) {
                    PsiClass resolved = ((PsiClassType) type).resolve();
                    if (resolved != null
                            && context.getEvaluator()
                                    .implementsInterface(resolved, CREATOR_CLS, false)) {
                        return;
                    }
                }
            }
        }

        context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "This class implements android.os.Parcelable but does not define a static "
                        + "CREATOR field of type android.os.Parcelable.Creator");
    }
}