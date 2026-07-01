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
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "ParcelCreator",
        "Missing Parcelable CREATOR field",
        "According to the Parcelable interface documentation, Classes implementing the Parcelable interface must also have a static field called CREATOR, which is an object implementing the Parcelable.Creator interface.",
        Category.CORRECTNESS,
        6,
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
                if (node.isInterface() || node.isAnnotationType() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(node, "android.os.Parcelable", true)) {
                    return;
                }

                if (node.findAnnotation("kotlinx.parcelize.Parcelize") != null ||
                    node.findAnnotation("androidx.parcelize.Parcelize") != null ||
                    node.findAnnotation("kotlinx.android.parcel.Parcelize") != null) {
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
                    for (UElement decl : node.getUastDeclarations()) {
                        if (decl instanceof UClass) {
                            UClass inner = (UClass) decl;
                            if ("Companion".equals(inner.getName())) {
                                for (UField field : inner.getFields()) {
                                    if ("CREATOR".equals(field.getName())) {
                                        creatorField = field;
                                        break;
                                    }
                                }
                                if (creatorField != null) break;
                            }
                        }
                    }
                }

                if (creatorField != null) {
                    boolean isPublic = creatorField.hasModifierProperty(PsiModifier.PUBLIC);
                    boolean isStatic = creatorField.hasModifierProperty(PsiModifier.STATIC);

                    if (!isStatic || !isPublic) {
                        context.report(ISSUE, context.getNameLocation(creatorField),
                            "This field should be annotated with @JvmField");
                    }
                    return;
                }

                context.report(ISSUE, context.getNameLocation(node),
                    "This class implements Parcelable but does not provide a CREATOR field");
            }
        };
    }
}