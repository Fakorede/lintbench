package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.visitor.UElementHandler;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnClickDetector extends ResourceXmlDetector implements Detector.UastScanner {

    private static final String CLASS_VIEW = "android.view.View";

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE))
    );

    private final Set<String> mOnClickMethods = new HashSet<>();
    private final List<OnClickReference> mPendingReferences = new ArrayList<>();

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                if (isValidOnClickMethod(node)) {
                    String name = node.getName();
                    if (name != null) {
                        mOnClickMethods.add(name);
                    }
                }
            }
        };
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        mPendingReferences.add(new OnClickReference(value, context.getLocation(attribute)));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (OnClickReference reference : mPendingReferences) {
            if (!mOnClickMethods.contains(reference.name)) {
                String message = "onClick handler method '" + reference.name
                        + "' does not exist; expected a public method taking exactly one View argument";
                context.report(ISSUE, reference.location, message);
            }
        }

        mPendingReferences.clear();
        mOnClickMethods.clear();
    }

    private static boolean isValidOnClickMethod(@NonNull UMethod method) {
        if (method.isConstructor()) {
            return false;
        }

        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.VOID.equals(returnType)) {
            return false;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 1) {
            return false;
        }

        PsiType parameterType = parameters.get(0).getType();
        return parameterType != null && parameterType.equalsToText(CLASS_VIEW);
    }

    private static final class OnClickReference {
        final String name;
        final Location location;

        OnClickReference(@NonNull String name, @NonNull Location location) {
            this.name = name;
            this.location = location;
        }
    }
}