package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ON_CLICK = "onClick";

    private final Map<String, List<Reference>> mReferences = new HashMap<>();
    private final Set<String> mHandlers = new HashSet<>();

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReferences.clear();
        mHandlers.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_NS.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@")) {
            return;
        }

        mReferences.computeIfAbsent(value, k -> new ArrayList<>())
                .add(new Reference(context, attribute));
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                if (isValidOnClickHandler(node)) {
                    mHandlers.add(node.getName());
                }
            }
        };
    }

    private static boolean isValidOnClickHandler(@NonNull UMethod method) {
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
        return parameterType != null && parameterType.equalsToText("android.view.View");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Reference>> entry : mReferences.entrySet()) {
            String name = entry.getKey();
            if (!mHandlers.contains(name)) {
                for (Reference reference : entry.getValue()) {
                    reference.context.report(
                            ISSUE,
                            reference.attribute,
                            reference.context.getLocation(reference.attribute),
                            "Corresponding method handler '" + name + "' not found");
                }
            }
        }
    }

    private static class Reference {
        final XmlContext context;
        final Attr attribute;

        Reference(@NonNull XmlContext context, @NonNull Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }
}