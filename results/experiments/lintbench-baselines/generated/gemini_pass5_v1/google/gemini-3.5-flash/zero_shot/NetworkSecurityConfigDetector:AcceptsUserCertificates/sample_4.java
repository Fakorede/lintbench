package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
            "which could impact the privacy of your users. Consider nesting your app's " +
            "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only " +
            "available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
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
        if (!"network-security-config".equals(element.getOwnerDocument().getDocumentElement().getTagName())) {
            return;
        }

        String src = element.getAttribute("src");
        if ("user".equals(src)) {
            if (!isInsideDebugOverrides(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Allowing user certificates could allow eavesdroppers to intercept data sent by your app. " +
                        "Consider nesting your app's `trust-anchors` inside a `<debug-overrides>` element."
                );
            }
        }
    }

    private boolean isInsideDebugOverrides(Element element) {
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            if ("debug-overrides".equals(((Element) parent).getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}