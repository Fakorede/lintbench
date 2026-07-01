package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiField;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the `Parcelable` interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",
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
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration instanceof UAnonymousClass) {
            return;
        }

        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize") ||
            declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        if (creatorField != null) {
            return;
        }

        UClass companion = null;
        boolean hasCreatorInnerClass = false;
        for (UClass inner : declaration.getInnerClasses()) {
            String name = inner.getName();
            if ("CREATOR".equals(name)) {
                hasCreatorInnerClass = true;
                break;
            }
            if (context.getEvaluator().isCompanionObject(inner) || "Companion".equals(name)) {
                companion = inner;
            }
        }

        if (hasCreatorInnerClass) {
            return;
        }

        if (companion != null) {
            PsiField companionCreator = companion.findFieldByName("CREATOR", false);
            if (companionCreator != null) {
                if (!companionCreator.hasAnnotation("kotlin.jvm.JvmField")) {
                    context.report(
                            ISSUE,
                            companionCreator,
                            context.getNameLocation(companionCreator),
                            "Field should be annotated with @JvmField"
                    );
                }
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not provide a CREATOR field"
        );
    }
}