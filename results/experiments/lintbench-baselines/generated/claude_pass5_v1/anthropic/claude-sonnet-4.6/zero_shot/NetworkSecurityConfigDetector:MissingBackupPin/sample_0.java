package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_PIN_SET);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_PIN_SET.equals(element.getTagName())) {
            return;
        }

        NodeList pins = element.getElementsByTagName(TAG_PIN);
        if (pins.getLength() < 2) {
            context.report(
                    MISSING_BACKUP_PIN,
                    element,
                    context.getLocation(element),
                    "A `<pin-set>` should contain at least two `<pin>` elements to ensure " +
                    "a backup pin is available if the primary certificate is rotated."
            );
        }
    }
}