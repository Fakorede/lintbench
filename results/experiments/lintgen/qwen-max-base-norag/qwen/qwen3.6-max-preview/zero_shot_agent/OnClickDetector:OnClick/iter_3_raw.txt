package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
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
            new Implementation(OnClickDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<Location>> mOnClickMethods = new HashMap<>();

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mOnClickMethods.clear();
    }

    @Nullable
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

    @Nullable
    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
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