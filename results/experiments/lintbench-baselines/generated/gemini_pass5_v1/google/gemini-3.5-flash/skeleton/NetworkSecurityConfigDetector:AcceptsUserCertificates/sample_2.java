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
        if (root == null || !root.getTagName().equals("network-security-config")) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("certificates".equals(element.getTagName())) {
            String src = element.getAttribute("src");
            if ("user".equals(src)) {
                if (!isUnderDebugOverrides(element)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Accepting user certificates of trust-anchors is insecure. "
                                    + "Consider nesting your app's trust-anchors inside a <debug-overrides> "
                                    + "element to make sure they are only available when android:debuggable is set to true.");
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private boolean isUnderDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if ("debug-overrides".equals(parentElement.getTagName())) {
                return true;
            }
            parent = parentElement.getParentNode();
        }
        return false;
    }
}