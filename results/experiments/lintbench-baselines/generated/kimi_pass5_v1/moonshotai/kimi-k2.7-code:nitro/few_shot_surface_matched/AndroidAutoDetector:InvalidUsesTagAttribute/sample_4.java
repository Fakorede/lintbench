package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid name attribute for `<uses>` element",
                    "The `<uses>` element inside `<automotiveApp>` must use a valid value for "
                            + "its `name` attribute. Valid values are `media`, `notification`, or "
                            + "`sms`. Other values are ignored and may indicate a configuration "
                            + "error.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.RESOURCE_XML_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null || !"automotiveApp".equals(parent.getTagName())) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (isValidName(name)) {
            return;
        }

        context.report(
                INVALID_USES_TAG_ATTRIBUTE,
                element,
                context.getLocation(element),
                "Invalid `name` attribute for `<uses>` element. Valid values are `media`, "
                        + "`notification`, or `sms`.");
    }

    private static boolean isValidName(String name) {
        return "media".equals(name) || "notification".equals(name) || "sms".equals(name);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitMethod(JavaContext context, UMethod node, PsiMethod method) {
    }
}