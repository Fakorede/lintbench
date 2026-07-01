package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES);

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "onClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context "
                            + "to invoke when the view is clicked. This name must correspond to a public method "
                            + "that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mDeclaredClicks = new HashMap<>();
    private final Set<String> mFoundMethods = new HashSet<>();
    private boolean mHasProcessedAnyClass = false;

    public OnClickDetector() {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasProcessedAnyClass) {
            return;
        }
        for (Map.Entry<String, List<Location>> entry : mDeclaredClicks.entrySet()) {
            String methodName = entry.getKey();
            if (!mFoundMethods.contains(methodName)) {
                for (Location location : entry.getValue()) {
                    context.report(
                            ISSUE,
                            location,
                            "Corresponding method `public void " + methodName + "(View)` not found in Activity"
                    );
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value.isEmpty() || value.startsWith("@string/")) {
            return;
        }
        List<Location> locations = mDeclaredClicks.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mDeclaredClicks.put(value, locations);
        }
        locations.add(context.getLocation(attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasProcessedAnyClass = true;
        for (PsiMethod method : declaration.getAllMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)
                    && method.getParameterList().getParametersCount() == 1) {
                PsiParameter parameter = method.getParameterList().getParameters()[0];
                if (parameter.getType().getCanonicalText().equals("android.view.View")) {
                    mFoundMethods.add(method.getName());
                }
            }
        }
    }
}