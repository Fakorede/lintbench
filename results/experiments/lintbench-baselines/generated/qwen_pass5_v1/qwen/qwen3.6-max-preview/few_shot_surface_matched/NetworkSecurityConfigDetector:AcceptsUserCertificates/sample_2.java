package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                            + "available when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global initialization required for this detector
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        NodeList certificatesList = document.getElementsByTagName("certificates");
        for (int i = 0; i < certificatesList.getLength(); i++) {
            Node node = certificatesList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element certElement = (Element) node;
                String src = certElement.getAttribute("src");
                if ("user".equals(src) && !isInsideDebugOverrides(certElement)) {
                    context.report(
                            ISSUE,
                            context.getLocation(certElement),
                            "Accepting user certificates could allow eavesdroppers to intercept data. "
                                    + "Consider nesting this inside a `<debug-overrides>` element.");
                }
            }
        }
    }

    private boolean isInsideDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = ((Element) parent).getTagName();
                if ("debug-overrides".equals(tagName)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}