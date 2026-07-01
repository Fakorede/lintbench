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
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid uses tag attribute",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value "
                            + "for the `name` attribute. Valid values are `media`, `notification`, "
                            + "or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

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
        super.beforeCheckRootProject(context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent != null && "automotiveApp".equals(parent.getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr == null) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `name` attribute of the `<uses>` element must be `media`, `notification`, or `sms`.");
            } else {
                String value = nameAttr.getValue();
                if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
                    context.report(
                            ISSUE,
                            nameAttr,
                            context.getLocation(nameAttr),
                            "The `name` attribute of the `<uses>` element must be `media`, `notification`, or `sms`.");
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
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
    }
}