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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/preview/features/security-config.html");

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("pin-set");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Document doc = element.getOwnerDocument();
        if (doc == null || doc.getDocumentElement() == null) {
            return;
        }
        if (!"network-security-config".equals(doc.getDocumentElement().getTagName())) {
            return;
        }

        int pinCount = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "pin".equals(child.getNodeName())) {
                pinCount++;
            }
        }

        if (pinCount == 1) {
            context.report(ISSUE, element, context.getNameLocation(element), "Missing backup pin");
        }
    }
}