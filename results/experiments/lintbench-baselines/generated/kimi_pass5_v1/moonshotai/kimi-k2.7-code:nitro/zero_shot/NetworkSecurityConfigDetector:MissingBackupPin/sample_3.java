package com.android.tools.lint.checks;

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
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "MissingBackupPin",
            "Missing backup pin",
            "It is highly recommended to declare a backup `<pin>` element. "
                    + "Not having a second pin defined can cause connection failures when the "
                    + "particular site certificate is rotated and the app has not yet been updated.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("pin-set");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList childNodes = element.getChildNodes();
        int pinCount = 0;
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && "pin".equals(node.getNodeName())) {
                pinCount++;
            }
        }

        if (pinCount < 2) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing backup `<pin>` element: a `<pin-set>` should contain at least two pins "
                            + "to avoid connection failures when a certificate is rotated.");
        }
    }
}