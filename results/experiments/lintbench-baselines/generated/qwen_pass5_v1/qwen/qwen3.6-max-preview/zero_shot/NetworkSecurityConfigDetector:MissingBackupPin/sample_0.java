package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
        "particular site certificate is rotated and the app has not yet been updated.",
        Category.SECURITY,
        5,
        Severity.WARNING,
        new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String TAG_PIN_SET = "pin-set";
    private static final String TAG_PIN = "pin";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_PIN_SET);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int pinCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && TAG_PIN.equals(node.getNodeName())) {
                pinCount++;
            }
        }

        if (pinCount < 2) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing backup pin: <pin-set> should contain at least two <pin> elements to prevent connection failures during certificate rotation."
            );
        }
    }
}