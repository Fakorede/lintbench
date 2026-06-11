package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconShape",
            "The launcher icon shape should use a distinct silhouette.",
            "According to the Android Design Guide, your launcher icons should \"use a distinct silhouette\", " +
                    "a \"three-dimensional, front view, with a slight perspective as if viewed from above, so that users perceive some depth.\" " +
                    "This implies that your launcher icon should not be a filled square.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name != null && name.startsWith("ic_launcher")) {
            String format = element.getAttribute("format");
            if ("png".equals(format)) {
                // Check for the presence of a silhouette in the icon.
                // This is a placeholder for actual image analysis logic.
                context.report(ISSUE, element, context.getLocation(element),
                        "Launcher icon should use a distinct silhouette.");
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        // No-op
    }
}