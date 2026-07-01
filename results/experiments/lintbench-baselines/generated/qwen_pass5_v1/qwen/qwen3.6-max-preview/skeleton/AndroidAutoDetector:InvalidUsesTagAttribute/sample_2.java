package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The <uses> element in <automotiveApp> should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
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
        Node parent = element.getParentNode();
        if (parent == null || !"automotiveApp".equals(parent.getNodeName())) {
            return;
        }

        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return;
        }

        if (!name.equals("media") && !name.equals("notification") && !name.equals("sms")) {
            context.report(ISSUE, element, context.getLocation(element.getAttributeNode("name")),
                    "Invalid value for `name` attribute. Valid values are `media`, `notification`, or `sms`.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        // No-op
    }
}