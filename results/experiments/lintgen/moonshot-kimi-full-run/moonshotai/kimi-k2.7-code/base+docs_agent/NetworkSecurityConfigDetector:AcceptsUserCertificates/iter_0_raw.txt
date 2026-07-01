package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                    + "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
            .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!VALUE_USER.equals(element.getAttributeNS(null, ATTR_SRC))) {
            return;
        }

        if (!isNetworkSecurityConfig(element)) {
            return;
        }

        if (isInsideDebugOverrides(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Allowing user certificates could allow eavesdroppers to intercept data sent by your app. "
                        + "Consider nesting this inside a `<debug-overrides>` element.");
    }

    private static boolean isNetworkSecurityConfig(Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        return root != null && TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName());
    }

    private static boolean isInsideDebugOverrides(Element element) {
        Element current = element;
        while (true) {
            Node parent = current.getParentNode();
            if (!(parent instanceof Element)) {
                return false;
            }
            current = (Element) parent;
            if (TAG_DEBUG_OVERRIDES.equals(current.getTagName())) {
                return true;
            }
        }
    }
}