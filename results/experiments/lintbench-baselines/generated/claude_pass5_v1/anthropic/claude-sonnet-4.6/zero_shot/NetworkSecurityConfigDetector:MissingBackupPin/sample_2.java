package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Issue MISSING_BACKUP_PIN = Issue.create(
            "MissingBackupPin",
            "Missing Backup Pin",
            "It is highly recommended to declare a backup `<pin>` element. " +
            "Not having a second pin defined can cause connection failures when the " +
            "particular site certificate is rotated and the app has not yet been updated.",
            Category.SECURITY,
            7,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/preview/features/security-config.html");

    private static final String TAG_PIN_SET = "pin-set";
    private static final String TAG_PIN = "pin";

    public NetworkSecurityConfigDetector() {
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_PIN_SET);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_PIN_SET.equals(element.getTagName())) {
            return;
        }

        int pinCount = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_PIN.equals(childElement.getTagName())) {
                    pinCount++;
                }
            }
        }

        if (pinCount < 2) {
            context.report(
                    MISSING_BACKUP_PIN,
                    element,
                    context.getLocation(element),
                    "A backup `<pin>` should be defined to prevent connection failures " +
                    "when the certificate is rotated"
            );
        }
    }
}