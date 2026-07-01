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

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
                    "which could impact the privacy of your users. Consider nesting your app's " +
                    "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
                    "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        NodeList certificates = document.getElementsByTagName("certificates");
        for (int i = 0; i < certificates.getLength(); i++) {
            Node node = certificates.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element certElement = (Element) node;
                String src = certElement.getAttribute("src");
                if ("user".equals(src) && !isInsideDebugOverrides(certElement)) {
                    context.report(ISSUE, certElement, context.getLocation(certElement),
                            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app");
                }
            }
        }
    }

    private boolean isInsideDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                if ("debug-overrides".equals(((Element) parent).getTagName())) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}