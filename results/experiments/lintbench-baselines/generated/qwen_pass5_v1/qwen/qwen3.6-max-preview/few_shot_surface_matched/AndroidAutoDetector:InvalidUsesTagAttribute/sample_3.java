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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid uses tag attribute",
            "The <uses> element in <automotiveApp> should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull Scope scope) {
        return scope == Scope.MANIFEST_SCOPE || scope == Scope.JAVA_FILE_SCOPE;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String name = element.getAttribute("name");
        if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Invalid `name` attribute for `<uses>` element. Valid values are `media`, `notification`, or `sms`.");
        }
    }

    @Nullable
    @Override
    public java.util.List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No Java class checks required for this XML-focused issue
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // No Java method checks required for this XML-focused issue
    }
}