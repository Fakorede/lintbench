package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data "
                            + "sent by your app, which could impact the privacy of your users. "
                            + "Consider nesting your app's `trust-anchors` inside a `<debug-overrides>` "
                            + "element to make sure they are only available when `android:debuggable` "
                            + "is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
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
            if (node instanceof Element) {
                Element element = (Element) node;
                if ("user".equals(element.getAttribute("src"))) {
                    if (!isInsideDebugOverrides(element)) {
                        Attr srcAttr = element.getAttributeNode("src");
                        Location location = srcAttr != null ? context.getLocation(srcAttr) : context.getLocation(element);
                        context.report(
                                ISSUE,
                                element,
                                location,
                                "Allowing user certificates is a security risk. Consider nesting your app's `trust-anchors` inside a `<debug-overrides>` element to make sure they are only available when `android:debuggable` is set to `true`.");
                    }
                }
            }
        }
    }

    private boolean isInsideDebugOverrides(Node node) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent instanceof Element) {
                Element parentElement = (Element) parent;
                if ("debug-overrides".equals(parentElement.getTagName())) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}