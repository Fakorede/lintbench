package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingBackupPin",
                    "Missing Backup Pin",
                    "This network security config does not define a backup `<pin>`. "
                            + "It is highly recommended to declare at least two `<pin>` elements "
                            + "inside a `<pin-set>`: one for the current certificate and a backup "
                            + "pin. When the server certificate is rotated and the app has not "
                            + "yet been updated with the new pin, a missing backup pin can cause "
                            + "connection failures.",
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
        // No project-wide setup required.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        NodeList pinSets = document.getElementsByTagName("pin-set");
        for (int i = 0; i < pinSets.getLength(); i++) {
            Element pinSet = (Element) pinSets.item(i);
            int pinCount = countPinChildren(pinSet);
            if (pinCount < 2) {
                context.report(
                        ISSUE,
                        context.getLocation(pinSet),
                        "This `<pin-set>` should declare at least two `<pin>` elements, "
                                + "including a backup pin.");
            }
        }
    }

    private static int countPinChildren(@NonNull Element pinSet) {
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