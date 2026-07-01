package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {
    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String SRC_USER = "user";

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Application accepts user certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are "
                    + "only available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_XML_FILE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SRC_USER.equals(element.getAttributeNS(null, ATTR_SRC))) {
            return;
        }

        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getNodeName())) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if (TAG_DEBUG_OVERRIDES.equals(parent.getNodeName())) {
                return;
            }
            parent = parent.getParentNode();
        }

        Location location = context.getLocation(element);
        context.report(
                ISSUE,
                element,
                location,
                "Trusting user-added CAs outside of `<debug-overrides>` can allow eavesdroppers "
                        + "to intercept app traffic."
        );
    }
}