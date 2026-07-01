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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing backup pin in network security config",
                    "It is highly recommended to declare a backup `<pin>` element inside a"
                            + " `<pin-set>`. If only a single pin is configured, the app will be"
                            + " unable to connect to the server when that certificate is rotated"
                            + " and the app has not yet been updated.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    protected boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.FOLDER_XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No per-project state required.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        NodeList pinSets = document.getElementsByTagName("pin-set");
        for (int i = 0; i < pinSets.getLength(); i++) {
            Element pinSet = (Element) pinSets.item(i);
            if (countChildPins(pinSet) < 2) {
                context.report(
                        ISSUE,
                        pinSet,
                        context.getLocation(pinSet),
                        "A <pin-set> should declare at least two <pin> elements to avoid"
                                + " connection failures when a certificate is rotated");
            }
        }
    }

    private static int countChildPins(@NonNull Element pinSet) {
        int count = 0;
        NodeList children = pinSet.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "pin".equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }
}