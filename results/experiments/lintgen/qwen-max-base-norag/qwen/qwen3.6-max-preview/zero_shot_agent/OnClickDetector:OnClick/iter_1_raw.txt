package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.*;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<String, List<Location>> mOnClickMethods = new HashMap<>();

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mOnClickMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            mOnClickMethods.computeIfAbsent(methodName, k -> new ArrayList<>())
                           .add(context.getLocation(attribute));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod method) {
                String name = method.getName();
                if (mOnClickMethods.containsKey(name)) {
                    if (context.getEvaluator().isPublic(method)) {
                        List<UParameter> parameters = method.getUastParameters();
                        if (parameters.size() == 1) {
                            PsiType type = parameters.get(0).getType();
                            if (type != null && "android.view.View".equals(type.getCanonicalText())) {
                                mOnClickMethods.remove(name);
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, List<Location>> entry : mOnClickMethods.entrySet()) {
            String methodName = entry.getKey();
            String message = "Corresponding method handler '`public void " + methodName + "(android.view.View)`' not found";
            for (Location location : entry.getValue()) {
                context.report(ISSUE, location, message);
            }
        }
    }
}