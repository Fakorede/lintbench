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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid uses tag attribute",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. "
                            + "Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No-op
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if ("uses".equals(element.getTagName())) {
            org.w3c.dom.Node parentNode = element.getParentNode();
            if (parentNode instanceof org.w3c.dom.Element && "automotiveApp".equals(((org.w3c.dom.Element) parentNode).getTagName())) {
                org.w3c.dom.Attr nameAttr = element.getAttributeNode("name");
                if (nameAttr != null) {
                    String value = nameAttr.getValue();
                    if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
                        context.report(
                                ISSUE,
                                nameAttr,
                                context.getLocation(nameAttr),
                                "The name attribute must be one of 'media', 'notification', or 'sms'"
                        );
                    }
                }
            }
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // No-op
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // No-op
    }
}