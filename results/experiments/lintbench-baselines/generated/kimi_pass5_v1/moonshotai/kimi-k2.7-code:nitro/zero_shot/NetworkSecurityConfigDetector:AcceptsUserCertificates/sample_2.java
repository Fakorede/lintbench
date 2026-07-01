package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final String TAG_CERTIFICATES = "certificates";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Application accepts user certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                            + "which could impact the privacy of your users. Consider nesting your app's "
                            + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                            + "available when `android:debuggable` is set to `true`.",
                    "https://goo.gle/AcceptsUserCertificates",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.XML) {
            return;
        }

        Element root = context.getDocument().getDocumentElement();
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        String src = element.getAttribute(ATTR_SRC);
        if (!VALUE_USER.equals(src)) {
            return;
        }

        Node node = element.getParentNode();
        while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            Element parent = (Element) node;
            if (TAG_DEBUG_OVERRIDES.equals(parent.getTagName())) {
                return;
            }
            node = parent.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This configuration accepts user certificates, which could allow eavesdroppers to intercept "
                        + "app traffic. Move these `trust-anchors` into a `<debug-overrides>` element.");
    }
}