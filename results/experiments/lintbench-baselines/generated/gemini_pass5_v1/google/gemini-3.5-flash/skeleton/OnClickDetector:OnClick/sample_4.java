package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<PendingReport>> mPendingReports = new HashMap<>();
    private final Set<String> mFoundMethods = new HashSet<>();

    private static class PendingReport {
        final XmlContext context;
        final Location location;

        PendingReport(XmlContext context, Location location) {
            this.context = context;
            this.location = location;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<PendingReport>> entry : mPendingReports.entrySet()) {
            String methodName = entry.getKey();
            if (!mFoundMethods.contains(methodName)) {
                for (PendingReport report : entry.getValue()) {
                    report.context.report(
                            ISSUE,
                            report.location,
                            String.format("Corresponding method `public void %s(View)` not found", methodName));
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
        if ("http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            String methodName = attribute.getValue();
            if (methodName != null && !methodName.isEmpty() && !methodName.startsWith("@")) {
                Location location = context.getValueLocation(attribute);
                List<PendingReport> reports = mPendingReports.get(methodName);
                if (reports == null) {
                    reports = new ArrayList<>();
                    mPendingReports.put(methodName, reports);
                }
                reports.add(new PendingReport(context, location));
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.content.Context");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 1) {
                    UParameter parameter = parameters.get(0);
                    PsiType type = parameter.getType();
                    if (type != null) {
                        String typeName = type.getCanonicalText();
                        if ("android.view.View".equals(typeName) || "View".equals(typeName)) {
                            mFoundMethods.add(method.getName());
                        }
                    }
                }
            }
        }
    }
}