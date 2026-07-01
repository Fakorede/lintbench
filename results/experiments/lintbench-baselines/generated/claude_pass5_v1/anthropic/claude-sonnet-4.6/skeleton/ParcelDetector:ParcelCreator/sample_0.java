package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, \"Classes implementing "
                            + "the Parcelable interface must also have a static field called "
                            + "`CREATOR`, which is an object implementing the "
                            + "`Parcelable.Creator` interface.\"",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String PARCELABLE_CLASS = "android.os.Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to implement CREATOR themselves
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip interfaces
        if (declaration.isInterface()) {
            return;
        }

        // Check if the class itself (not inherited) has a CREATOR field
        PsiField[] fields = declaration.getFields();
        for (PsiField field : fields) {
            if (CREATOR_FIELD.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                // Found the CREATOR field, no issue
                return;
            }
        }

        // Also check if any superclass (other than Object) already defines CREATOR
        // (but only if the superclass also implements Parcelable, meaning it's handled there)
        PsiClass superClass = declaration.getSuperClass();
        while (superClass != null) {
            String qualifiedName = superClass.getQualifiedName();
            if (qualifiedName == null || qualifiedName.equals("java.lang.Object")) {
                break;
            }

            // Check if the superclass implements Parcelable
            boolean superImplementsParcelable = false;
            for (PsiClass iface : superClass.getInterfaces()) {
                if (PARCELABLE_CLASS.equals(iface.getQualifiedName())) {
                    superImplementsParcelable = true;
                    break;
                }
            }

            if (superImplementsParcelable) {
                // The superclass handles Parcelable; check if it has CREATOR
                for (PsiField field : superClass.getFields()) {
                    if (CREATOR_FIELD.equals(field.getName())
                            && field.hasModifierProperty(PsiModifier.STATIC)) {
                        // Superclass has CREATOR - but subclasses should still define their own
                        // We still need to report this class as missing its own CREATOR
                        // Actually per Android docs, each concrete class needs its own CREATOR
                        // So we break and report the issue
                        break;
                    }
                }
            }

            superClass = superClass.getSuperClass();
        }

        // Report the missing CREATOR field
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}