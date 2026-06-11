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
                    Scope.ALL_XML_FILES
            )
    );

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
        if (element.getTagName().equals("application")) {
            Attr supportsRtlAttr = element.getAttributeNode("android:supportsRtl");

            if (supportsRtlAttr == null || !Boolean.parseBoolean(supportsRtlAttr.getValue())) {
                context.report(ISSUE, element, context.getLocation(element),
                        "You must set `android:supportsRtl` to true in the manifest `<application>` element.");
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:layoutDirection");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (attribute.getName().equals("android:layoutDirection")) {
            Element parent = attribute.getOwnerElement();
            if (!isRtlSupportEnabled(context)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Using RTL attributes without enabling RTL support in the manifest `<application>` element.");
            }
        }
    }

    private boolean isRtlSupportEnabled(XmlContext context) {
        Document document = context.getXmlDocument();
        Element applicationElement = (Element) document.getElementsByTagName("application").item(0);
        if (applicationElement != null) {
            Attr supportsRtlAttr = applicationElement.getAttributeNode("android:supportsRtl");
            return supportsRtlAttr != null && Boolean.parseBoolean(supportsRtlAttr.getValue());
        }
        return false;
    }

}