package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing the Parcelable interface must also have a static field called `CREATOR`, which is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.isAbstract()) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }

                UField creatorField = null;
                for (UField field : node.getFields()) {
                    if ("CREATOR".equals(field.getName())) {
                        creatorField = field;
                        break;
                    }
                }

                if (creatorField == null) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "This class implements `Parcelable` but does not provide a `CREATOR` field");
                    return;
                }

                if (!creatorField.isPublic() || !creatorField.isStatic() || !creatorField.isFinal()) {
                    context.report(ISSUE, creatorField, context.getLocation(creatorField),
                            "The `CREATOR` field must be public, static, and final");
                    return;
                }

                PsiType type = creatorField.getType();
                PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
                boolean isCreator = typeClass != null &&
                        (context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable$Creator", true) ||
                         context.getEvaluator().implementsInterface(typeClass, "android.os.Parcelable$ClassLoaderCreator", true));

                if (!isCreator) {
                    context.report(ISSUE, creatorField, context.getLocation(creatorField),
                            "The `CREATOR` field must implement `Parcelable.Creator`");
                }
            }
        };
    }
}