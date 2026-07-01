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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid name attribute for uses element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for the "
                            + "`name` attribute. Valid values are `media`, `notification`, or `sms`.",
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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            if ("automotiveApp".equals(parent.getTagName())) {
                if (!element.hasAttribute("name")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "The `<uses>` element should contain a `name` attribute");
                    return;
                }
                String nameValue = element.getAttribute("name");
                if (!"media".equals(nameValue) && !"notification".equals(nameValue) && !"sms".equals(nameValue)) {
                    Attr attribute = element.getAttributeNode("name");
                    context.report(
                            ISSUE,
                            attribute != null ? attribute : element,
                            context.getLocation(attribute != null ? attribute : element),
                            "Invalid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`");
                }
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
        // No-op
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No-op
    }
}