package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app, which could impact the privacy of your users."
                            + " Consider nesting your app's `trust-anchors` inside a"
                            + " `<debug-overrides>` element to make sure they are only available"
                            + " when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType, java.io.File file) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.NodeList nodeList = document.getElementsByTagName("certificates");
        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node node = nodeList.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }

            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            String src = element.getAttributeNS(null, "src");
            if (!"user".equals(src)) {
                continue;
            }

            if (isInsideDebugOverrides(element)) {
                continue;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app; consider adding trust-anchors inside"
                            + " <debug-overrides> instead.");
        }
    }

    private static boolean isInsideDebugOverrides(org.w3c.dom.Element element) {
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            if ("debug-overrides".equals(parent.getNodeName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}