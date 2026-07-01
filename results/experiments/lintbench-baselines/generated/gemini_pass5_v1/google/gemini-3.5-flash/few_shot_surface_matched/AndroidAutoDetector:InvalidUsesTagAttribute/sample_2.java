package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            );

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid name attribute for uses element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. "
                            + "Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode != null && "automotiveApp".equals(parentNode.getNodeName())) {
            String name = element.getAttribute("name");
            if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute: `media`, `notification`, or `sms`."
                );
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op for this standard Android Auto XML check
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No-op for this standard Android Auto XML check
    }
}