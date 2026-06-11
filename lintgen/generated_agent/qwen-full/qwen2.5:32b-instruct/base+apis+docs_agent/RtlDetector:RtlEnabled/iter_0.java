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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSupport",
            "Using RTL attributes without enabling RTL support",
            "To enable right-to-left support, when running on API 17 and higher, you must set the `android:supportsRtl` attribute in the manifest `<application>` element. If you have started adding RTL attributes, but have not yet finished the migration, you can set the attribute to false to satisfy this lint check.",
            Category.I18N,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    true,
                    ResourceFolderType.MANIFEST)
    );

    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("application".equals(element.getTagName())) {
            Attr supportsRtlAttr = element.getAttributeNode("android:supportsRtl");
            if (supportsRtlAttr == null || "false".equals(supportsRtlAttr.getValue())) {
                // Check for RTL attributes in the layout files
                context.getProject().getResources(ResourceType.LAYOUT).forEach(resource -> {
                    Document document = resource.getDocument();
                    if (document != null) {
                        visitDocument(context, document);
                    }
                });
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element rootElement = document.getDocumentElement();
        if (rootElement != null) {
            checkForRtlAttributes(context, rootElement);
        }
    }

    private void checkForRtlAttributes(XmlContext context, Element element) {
        for (int i = 0; i < element.getAttributeCount(); i++) {
            Attr attr = element.getAttributeNode(i);
            if (attr != null && isRtlAttribute(attr.getName())) {
                context.report(ISSUE, element, context.getLocation(element), "Using RTL attribute without enabling RTL support");
            }
        }

        for (Element child : XmlUtils.getChildren(element)) {
            checkForRtlAttributes(context, child);
        }
    }

    private boolean isRtlAttribute(String attributeName) {
        return attributeName.startsWith("android:layout_marginEnd") ||
               attributeName.startsWith("android:layout_marginStart") ||
               attributeName.startsWith("android:paddingEnd") ||
               attributeName.startsWith("android:paddingStart");
    }
}