package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, classes implementing the "
                            + "`Parcelable` interface must also have a static field called `CREATOR`, "
                            + "which is an object implementing the `Parcelable.Creator` interface. "
                            + "This field is used by the framework to instantiate the class from a `Parcel`.",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()
                || declaration.isEnum()
                || declaration.isAnonymous()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (hasCreatorField(declaration)) {
            return;
        }

        Location location = context.getLocation(declaration);
        context.report(
                ISSUE,
                location,
                "This class implements `Parcelable` but does not define a static `CREATOR` field.");
    }

    private static boolean hasCreatorField(@NonNull UClass declaration) {
        for (UField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }
}