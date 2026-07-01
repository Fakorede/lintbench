package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "trust-anchors inside a <debug-overrides> element to make sure they are only "
                            + "available when android:debuggable is set to true.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        // Required override for the specification
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        org.w3c.dom.NodeList certificatesList = document.getElementsByTagName("certificates");
        for (int i = 0; i < certificatesList.getLength(); i++) {
            org.w3c.dom.Node node = certificatesList.item(i);
            if (node instanceof org.w3c.dom.Element) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) node;
                String src = element.getAttribute("src");
                if ("user".equals(src)) {
                    if (!isUnderDebugOverrides(element)) {
                        org.w3c.dom.Attr srcAttr = element.getAttributeNode("src");
                        Location location = srcAttr != null ? context.getLocation(srcAttr) : context.getLocation(element);
                        context.report(
                                ISSUE,
                                element,
                                location,
                                "Accepting user certificates is insecure unless restricted to <debug-overrides>"
                        );
                    }
                }
            }
        }
    }

    private boolean isUnderDebugOverrides(org.w3c.dom.Node node) {
        org.w3c.dom.Node parent = node.getParentNode();
        while (parent != null) {
            if (parent instanceof org.w3c.dom.Element) {
                if ("debug-overrides".equals(((org.w3c.dom.Element) parent).getTagName())) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}