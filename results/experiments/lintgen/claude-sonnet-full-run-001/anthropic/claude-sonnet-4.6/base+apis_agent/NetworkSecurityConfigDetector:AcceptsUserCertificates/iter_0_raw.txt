package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    public static final Issue ACCEPTS_USER_CERTIFICATES = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by " +
            "your app, which could impact the privacy of your users. Consider nesting your " +
            "app's `trust-anchors` inside a `<debug-overrides>` element to make sure they " +
            "are only available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            ))
            .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
            .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_BASE_CONFIG = "base-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    public NetworkSecurityConfigDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_NETWORK_SECURITY_CONFIG,
                TAG_BASE_CONFIG,
                TAG_DOMAIN_CONFIG,
                TAG_TRUST_ANCHORS,
                TAG_CERTIFICATES
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (TAG_CERTIFICATES.equals(tagName)) {
            String src = element.getAttribute(ATTR_SRC);
            if (VALUE_USER.equals(src)) {
                // Check if this certificates element is inside a debug-overrides block
                if (!isInsideDebugOverrides(element)) {
                    context.report(
                            ACCEPTS_USER_CERTIFICATES,
                            element,
                            context.getLocation(element),
                            "Allowing user certificates could allow eavesdroppers to intercept " +
                            "data sent by your app, which could impact the privacy of your users. " +
                            "Consider nesting your app's `trust-anchors` inside a " +
                            "`<debug-overrides>` element to make sure they are only available " +
                            "when `android:debuggable` is set to `true`."
                    );
                }
            }
        }
    }

    private boolean isInsideDebugOverrides(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if (TAG_DEBUG_OVERRIDES.equals(parent.getNodeName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}