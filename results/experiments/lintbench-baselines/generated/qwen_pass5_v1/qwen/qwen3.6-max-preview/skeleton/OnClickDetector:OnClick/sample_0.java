package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements Detector.UastScanner {

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

    private final List<XmlOnClickInfo> xmlOnClicks = new ArrayList<>();
    private final Set<String> validMethods = new HashSet<>();

    private static class XmlOnClickInfo {
        final XmlContext context;
        final Attr attribute;
        final String methodName;

        XmlOnClickInfo(XmlContext context, Attr attribute, String methodName) {
            this.context = context;
            this.attribute = attribute;
            this.methodName = methodName;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (XmlOnClickInfo info : xmlOnClicks) {
            if (!validMethods.contains(info.methodName)) {
                info.context.report(
                        ISSUE,
                        info.attribute,
                        info.context.getLocation(info.attribute),
                        "Corresponding method handler '" + info.methodName + "(android.view.View)' not found");
            }
        }
        xmlOnClicks.clear();
        validMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            xmlOnClicks.add(new XmlOnClickInfo(context, attribute, methodName));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.view.View",
                "android.content.Context"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                PsiParameter[] params = method.getParameters();
                if (params.length == 1) {
                    PsiType type = params[0].getType();
                    if (type != null && "android.view.View".equals(type.getCanonicalText())) {
                        validMethods.add(method.getName());
                    }
                }
            }
        }
    }
}