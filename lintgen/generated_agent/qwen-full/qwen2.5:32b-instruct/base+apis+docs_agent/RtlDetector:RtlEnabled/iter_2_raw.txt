package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
                    true)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST || folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("application".equals(element.getTagName())) {
            Attr supportsRtlAttr = element.getAttributeNode("android:supportsRtl");
            boolean supportsRtlEnabled = supportsRtlAttr != null && !"false".equalsIgnoreCase(supportsRtlAttr.getValue());
            
            if (!supportsRtlEnabled) {
                context.getDriver().getProject().getResources(ResourceType.LAYOUT).forEach(resource -> {
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

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode instanceof Element) {
                checkForRtlAttributes(context, (Element) childNode);
            }
        }
    }

    private boolean isRtlAttribute(String attributeName) {
        return attributeName.startsWith("android:layout_marginEnd") ||
               attributeName.startsWith("android:layout_marginStart") ||
               attributeName.startsWith("android:paddingEnd") ||
               attributeName.startsWith("android:paddingStart");
    }
}