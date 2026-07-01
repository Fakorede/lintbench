package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing user certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                    + "which could impact the privacy of your users. Consider nesting your app's "
                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                    + "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    protected boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("trust-anchors");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"network-security-config".equals(root.getTagName())) {
            return;
        }

        if (isInDebugOverrides(element)) {
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "certificates".equals(child.getNodeName())) {
                Element certificates = (Element) child;
                if ("user".equals(certificates.getAttribute("src"))) {
                    context.report(
                            ISSUE,
                            certificates,
                            context.getLocation(certificates),
                            "Accepting user certificates may allow eavesdroppers to intercept app traffic; "
                                    + "move these trust anchors into `<debug-overrides>` if they are only needed for debugging.");
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean isInDebugOverrides(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if ("debug-overrides".equals(parent.getNodeName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}