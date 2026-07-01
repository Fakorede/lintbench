package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements Detector.UastScanner {

    private static final String ANDROID_OS_PARCELABLE = "android.os.Parcelable";
    private static final String ANDROID_OS_PARCELABLE_CREATOR = "android.os.Parcelable.Creator";

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the Parcelable interface must also have a static field " +
                    "called CREATOR, which is an object implementing the Parcelable.Creator interface.",
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
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(node, ANDROID_OS_PARCELABLE, true)) {
                    return;
                }

                if (hasCreatorField(node)) {
                    return;
                }

                context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Parcelable requires a CREATOR field");
            }
        };
    }

    private static boolean hasCreatorField(@NonNull UClass node) {
        for (UField field : node.getFields()) {
            if (!"CREATOR".equals(field.getName())) {
                continue;
            }
            if (!field.hasModifierProperty(PsiModifier.STATIC)
                    || !field.hasModifierProperty(PsiModifier.FINAL)) {
                continue;
            }
            PsiType type = field.getType();
            if (type == null) {
                continue;
            }
            String canonicalText = type.getCanonicalText(false);
            if (canonicalText.startsWith(ANDROID_OS_PARCELABLE_CREATOR)) {
                return true;
            }
        }
        return false;
    }
}