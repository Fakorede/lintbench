package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_PIN_SET = "pin-set";
    private static final String TAG_PIN = "pin";

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "It is highly recommended to declare a backup `<pin>` element. "
                            + "Not having a second pin defined can cause connection failures "
                            + "when the particular site certificate is rotated and the app "
                            + "has not yet been updated.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No per-project initialization needed.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !isNamed(root, TAG_NETWORK_SECURITY_CONFIG)) {
            return;
        }

        for (Element pinSet : getElementsByTagName(document, TAG_PIN_SET)) {
            int pinCount = 0;
            NodeList children = pinSet.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && isNamed((Element) child, TAG_PIN)) {
                    pinCount++;
                }
            }

            if (pinCount < 2) {
                context.report(
                        ISSUE,
                        pinSet,
                        context.getElementLocation(pinSet),
                        "This `<pin-set>` should contain at least two `<pin>` elements "
                                + "(a primary pin and a backup pin) to avoid connection failures "
                                + "when the certificate is rotated.");
            }
        }
    }

    private static boolean isNamed(@NonNull Element element, @NonNull String name) {
        String localName = element.getLocalName();
        if (localName != null) {
            return name.equals(localName);
        }
        return name.equals(element.getNodeName());
    }

    @NonNull
    private static Iterable<Element> getElementsByTagName(@NonNull Document document,
            @NonNull String tagName) {
        final NodeList nodeList = document.getElementsByTagName(tagName);
        return new Iterable<Element>() {
            @Override
            @NonNull
            public java.util.Iterator<Element> iterator() {
                return new java.util.Iterator<Element>() {
                    private int index = 0;

                    @Override
                    public boolean hasNext() {
                        return index < nodeList.getLength();
                    }

                    @Override
                    public Element next() {
                        return (Element) nodeList.item(index++);
                    }
                };
            }
        };
    }
}