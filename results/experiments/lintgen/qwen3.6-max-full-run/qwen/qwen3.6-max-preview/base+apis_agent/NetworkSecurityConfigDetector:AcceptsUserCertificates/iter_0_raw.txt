package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "AcceptsUserCertificates",
        "Allowing User Certificates",
        "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
        "which could impact the privacy of your users. Consider nesting your app's " +
        "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
        "available when `android:debuggable` is set to `true`.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("certificates");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getNodeName())) {
            return;
        }

        String src = element.getAttribute("src");
        if (!"user".equals(src)) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE && "debug-overrides".equals(parent.getNodeName())) {
                return;
            }
            parent = parent.getParentNode();
        }

        context.report(
            ISSUE,
            context.getLocation(element),
            "Accepting user certificates is insecure. Consider nesting `<trust-anchors>` inside `<debug-overrides>`."
        );
    }
}