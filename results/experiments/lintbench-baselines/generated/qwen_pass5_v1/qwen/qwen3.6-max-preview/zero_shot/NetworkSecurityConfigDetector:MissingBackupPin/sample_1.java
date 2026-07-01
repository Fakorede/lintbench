package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "MissingBackupPin",
        "Missing Backup Pin",
        "It is highly recommended to declare a backup `<pin>` element. " +
        "Not having a second pin defined can cause connection failures when the " +
        "particular site certificate is rotated and the app has not yet been updated.\n\n" +
        "Reference documentation:\n" +
        "https://developer.android.com/preview/features/security-config.html",
        Category.SECURITY,
        5,
        Severity.WARNING,
        new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("pin-set");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int pinCount = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "pin".equals(child.getNodeName())) {
                pinCount++;
            }
        }

        if (pinCount < 2) {
            context.report(ISSUE, context.getLocation(element),
                "This `<pin-set>` only defines " + pinCount + " pin(s). " +
                "It is highly recommended to declare a backup `<pin>` element to avoid " +
                "connection failures during certificate rotation.");
        }
    }
}