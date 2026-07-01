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

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("trust-anchors");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent != null && "debug-overrides".equals(parent.getNodeName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element certElement = (Element) child;
                if ("certificates".equals(certElement.getTagName())) {
                    String src = certElement.getAttribute("src");
                    if ("user".equals(src)) {
                        context.report(ISSUE, context.getLocation(certElement),
                                "Accepting user certificates could allow eavesdroppers to intercept data. " +
                                "Consider moving this `<trust-anchors>` block inside `<debug-overrides>`.");
                        return;
                    }
                }
            }
        }
    }
}