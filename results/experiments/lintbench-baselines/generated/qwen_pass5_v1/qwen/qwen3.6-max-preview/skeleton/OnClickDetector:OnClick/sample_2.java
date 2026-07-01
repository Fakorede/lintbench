package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context " +
                    "to invoke when the view is clicked. This name must correspond to a public method " +
                    "that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static class XmlRef {
        final XmlContext context;
        final Attr attr;

        XmlRef(XmlContext context, Attr attr) {
            this.context = context;
            this.attr = attr;
        }
    }

    private final Map<String, List<XmlRef>> mReferences = new HashMap<>();
    private final Set<String> mValidMethods = new HashSet<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<XmlRef>> entry : mReferences.entrySet()) {
            if (!mValidMethods.contains(entry.getKey())) {
                String message = "Corresponding method handler '`public void " + entry.getKey() + "(android.view.View)`' not found";
                for (XmlRef ref : entry.getValue()) {
                    Location location = ref.context.getLocation(ref.attr);
                    ref.context.report(ISSUE, location, message);
                }
            }
        }
        mReferences.clear();
        mValidMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            mReferences.computeIfAbsent(methodName, k -> new ArrayList<>()).add(new XmlRef(context, attribute));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.view.View",
                "androidx.appcompat.app.AppCompatActivity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 1) {
                    PsiType type = parameters.get(0).getType();
                    if (type != null && type.getCanonicalText().equals("android.view.View")) {
                        mValidMethods.add(method.getName());
                    }
                }
            }
        }
    }
}