package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.TAG_CERTIFICATES;
import static com.android.SdkConstants.TAG_DEBUG_OVERRIDES;
import static com.android.SdkConstants.TAG_NETWORK_SECURITY_CONFIG;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Accepting User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent"
                            + " by your app, which could impact the privacy of your users. Consider"
                            + " nesting your app's `trust-anchors` inside a `<debug-overrides>`"
                            + " element to make sure they are only available when"
                            + " `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_XML_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType, File file) {
        return folderType == ResourceFolderType.XML
                && "network_security_config.xml".equals(file.getName());
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No per-project state needed; hook kept because the check is project-wide.
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }
        checkElement(context, root, false);
    }

    private void checkElement(XmlContext context, Element element, boolean insideDebugOverrides) {
        String tag = element.getTagName();
        boolean inDebug = insideDebugOverrides || TAG_DEBUG_OVERRIDES.equals(tag);

        if (!inDebug && TAG_CERTIFICATES.equals(tag)) {
            String src = element.getAttribute(ATTR_SRC);
            if ("user".equals(src)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Allowing user certificates could allow eavesdroppers to intercept data"
                                + " sent by your app; consider adding this trust anchor inside a"
                                + " `<debug-overrides>` element");
            }
        }

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, inDebug);
            }
        }
    }
}