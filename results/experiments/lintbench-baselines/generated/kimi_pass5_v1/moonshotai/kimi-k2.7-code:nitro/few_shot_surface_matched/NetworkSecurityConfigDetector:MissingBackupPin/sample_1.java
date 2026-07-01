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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing backup pin",
                    "It is highly recommended to declare a backup `<pin>` element. Not having a "
                            + "second pin defined can cause connection failures when the particular "
                            + "site certificate is rotated and the app has not yet been updated.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {}

    @Override
    public void visitDocument(@NonNull XmlContext context) {
        Element root = context.document.getDocumentElement();
        if (root == null) {
            return;
        }

        for (Element pinSet : getChildren(root)) {
            if (!"pin-set".equals(pinSet.getTagName())) {
                continue;
            }

            int pinCount = 0;
            for (Element child : getChildren(pinSet)) {
                if ("pin".equals(child.getTagName())) {
                    pinCount++;
                }
            }

            if (pinCount < 2) {
                context.report(
                        ISSUE,
                        pinSet,
                        context.getLocation(pinSet),
                        "It is highly recommended to declare a backup `<pin>` element in this "
                                + "pin-set to avoid connection failures during certificate "
                                + "rotation.");
            }
        }
    }

    private static Iterable<Element> getChildren(Element element) {
        return () ->
                new org.w3c.dom.NodeListIterator(element.getChildNodes()) {
                    @Override
                    public boolean hasNext() {
                        return index < nodes.getLength();
                    }

                    @Override
                    public Element next() {
                        while (index < nodes.getLength()) {
                            org.w3c.dom.Node node = nodes.item(index++);
                            if (node instanceof Element) {
                                return (Element) node;
                            }
                        }
                        throw new java.util.NoSuchElementException();
                    }

                    private final org.w3c.dom.NodeList nodes;
                    private int index;

                    {
                        this.nodes = element.getChildNodes();
                    }
                };
    }
}