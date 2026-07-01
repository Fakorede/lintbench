package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing user certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
            "which could impact the privacy of your users. Consider nesting your app's " +
            "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
            "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        String src = element.getAttribute("src");
        if (!"user".equals(src)) {
            return;
        }

        Node parent = element.getParentNode();
        boolean insideDebugOverrides = false;
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = ((Element) parent).getTagName();
                if ("debug-overrides".equals(tagName)) {
                    insideDebugOverrides = true;
                    break;
                }
                if ("network-security-config".equals(tagName)) {
                    break;
                }
            }
            parent = parent.getParentNode();
        }

        if (!insideDebugOverrides) {
            context.report(ISSUE, element, context.getLocation(element.getAttributeNode("src")),
                    "Accepting user certificates could allow eavesdroppers to intercept data. " +
                    "Consider nesting this `<trust-anchors>` block inside `<debug-overrides>`.");
        }
    }
}