package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;
import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    EnumSet.of(Scope.JAVA_FILE)
            )
    );

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String ANDROID_CONTEXT = "android.content.Context";
    private static final String ANDROID_ATTRIBUTE_SET = "android.util.AttributeSet";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Skip interfaces, abstract classes, and anonymous classes
                if (node.isInterface()) {
                    return;
                }
                if (node.isAnonymous()) {
                    return;
                }

                PsiModifierList modifierList = node.getModifierList();
                if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                // Check if the class extends android.view.View (directly or indirectly)
                if (!extendsView(node)) {
                    return;
                }

                // Check if the class has at least one of the required constructors
                if (!hasViewConstructor(node)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "Custom view `" + node.getName() + "` is missing constructor used by tools: " +
                            "`(Context)` or `(Context, AttributeSet)` or `(Context, AttributeSet, int)`"
                    );
                }
            }
        };
    }

    private boolean extendsView(UClass cls) {
        // Walk the hierarchy to see if any superclass is android.view.View
        PsiClass psiClass = cls.getJavaPsi();
        return extendsView(psiClass, 0);
    }

    private boolean extendsView(PsiClass cls, int depth) {
        if (depth > 20 || cls == null) {
            return false;
        }
        String qualifiedName = cls.getQualifiedName();
        if (ANDROID_VIEW.equals(qualifiedName)) {
            return true;
        }
        PsiClass superClass = cls.getSuperClass();
        if (superClass == null || superClass.equals(cls)) {
            return false;
        }
        return extendsView(superClass, depth + 1);
    }

    private boolean hasViewConstructor(UClass cls) {
        for (UMethod method : cls.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }
            UastFacade facade = UastFacade.INSTANCE;
            PsiParameterList parameterList = method.getJavaPsi().getParameterList();
            PsiParameter[] params = parameterList.getParameters();

            if (params.length == 1) {
                // View(Context context)
                if (isContext(params[0].getType())) {
                    return true;
                }
            } else if (params.length == 2) {
                // View(Context context, AttributeSet attrs)
                if (isContext(params[0].getType()) && isAttributeSet(params[1].getType())) {
                    return true;
                }
            } else if (params.length == 3) {
                // View(Context context, AttributeSet attrs, int defStyle)
                if (isContext(params[0].getType()) &&
                        isAttributeSet(params[1].getType()) &&
                        isInt(params[2].getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isContext(PsiType type) {
        return typeMatches(type, ANDROID_CONTEXT);
    }

    private boolean isAttributeSet(PsiType type) {
        return typeMatches(type, ANDROID_ATTRIBUTE_SET);
    }

    private boolean isInt(PsiType type) {
        return PsiType.INT.equals(type);
    }

    private boolean typeMatches(PsiType type, String qualifiedName) {
        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();
            if (resolved != null) {
                String fqn = resolved.getQualifiedName();
                if (qualifiedName.equals(fqn)) {
                    return true;
                }
                // Check if resolved class is a subclass of the expected type
                return isSubclassOf(resolved, qualifiedName, 0);
            }
        }
        return false;
    }

    private boolean isSubclassOf(PsiClass cls, String qualifiedName, int depth) {
        if (depth > 20 || cls == null) {
            return false;
        }
        String fqn = cls.getQualifiedName();
        if (qualifiedName.equals(fqn)) {
            return true;
        }
        PsiClass superClass = cls.getSuperClass();
        if (superClass != null && !superClass.equals(cls)) {
            if (isSubclassOf(superClass, qualifiedName, depth + 1)) {
                return true;
            }
        }
        for (PsiClass iface : cls.getInterfaces()) {
            if (isSubclassOf(iface, qualifiedName, depth + 1)) {
                return true;
            }
        }
        return false;
    }
}