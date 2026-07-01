package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing user certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are "
                    + "only available when `android:debuggable` is set to `true`.\n\n"
                    + "For more information, see "
                    + "https://developer.android.com/training/articles/security-config#TrustingDebugCa",
            "https://goo.gle/AcceptsUserCertificates",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr srcAttr = element.getAttributeNode(ATTR_SRC);
        if (srcAttr == null) {
            return;
        }

        String src = srcAttr.getValue();
        if (src == null || !"user".equals(src.trim())) {
            return;
        }

        Element parent = getParentElement(element);
        if (parent == null || !TAG_TRUST_ANCHORS.equals(parent.getTagName())) {
            return;
        }

        Element root = getRootElement(element);
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        if (isInsideDebugOverrides(element)) {
            return;
        }

        context.report(
                ISSUE,
                srcAttr,
                context.getLocation(srcAttr),
                "Accepting user certificates is insecure; use debug-overrides instead");
    }

    private static boolean isInsideDebugOverrides(Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element e = (Element) parent;
            if (TAG_DEBUG_OVERRIDES.equals(e.getTagName())) {
                return true;
            }
            parent = e.getParentNode();
        }
        return false;
    }

    private static Element getParentElement(Element element) {
        Node parent = element.getParentNode();
        return parent instanceof Element ? (Element) parent : null;
    }

    private static Element getRootElement(Element element) {
        Element current = element;
        while (current.getParentNode() instanceof Element) {
            current = (Element) current.getParentNode();
        }
        return current;
    }
}