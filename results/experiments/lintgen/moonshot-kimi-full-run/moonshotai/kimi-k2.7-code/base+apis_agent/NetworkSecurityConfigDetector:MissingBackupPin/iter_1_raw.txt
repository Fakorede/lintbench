package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingBackupPin",
            "Missing backup `<pin>` element",
            "It is highly recommended to declare a backup `<pin>` element. Not having a second pin defined can cause connection failures when the particular site certificate is rotated and the app has not yet been updated.",
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
        return Arrays.asList(TAG_PIN_SET);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int pinCount = 0;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_PIN.equals(child.getNodeName())) {
                pinCount++;
            }
            child = child.getNextSibling();
        }

        if (pinCount < 2) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing backup `<pin>` element: a `<pin-set>` should contain at least two pins to avoid connection failures during certificate rotation."
            );
        }
    }
}