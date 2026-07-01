package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ParcelDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing CREATOR field",
            "Classes implementing the Parcelable interface must also have a static field called CREATOR, which is an object implementing the Parcelable.Creator interface.",
            "https://developer.android.com/reference/android/os/Parcelable.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(node, "android.os.Parcelable", false)) {
                    return;
                }

                for (PsiField field : node.getFields()) {
                    if ("CREATOR".equals(field.getName())
                            && field.hasModifierProperty(PsiModifier.STATIC)) {
                        String type = field.getType().getCanonicalText();
                        if (type != null && type.startsWith("android.os.Parcelable.Creator")) {
                            return;
                        }
                    }
                }

                context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This class implements Parcelable but is missing a static CREATOR field of type Parcelable.Creator"
                );
            }
        };
    }
}