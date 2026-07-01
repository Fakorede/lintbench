package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingBackupPin",
            "Missing Backup Pin",
            "It is highly recommended to declare a backup `<pin>` element. Not having a "
                    + "second pin defined can cause connection failures when the particular site "
                    + "certificate is rotated and the app has not yet been updated.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("pin-set");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int pinCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "pin".equals(child.getNodeName())) {
                pinCount++;
            }
        }

        if (pinCount == 1) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A backup pin should be configured to prevent connection failures when the primary pin is rotated."
            );
        }
    }
}