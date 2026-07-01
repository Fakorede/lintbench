package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingBackupPin",
            "Missing backup pin",
            "It is highly recommended to declare a backup `<pin>` element. Not having a second pin " +
                    "defined can cause connection failures when the particular site certificate is " +
                    "rotated and the app has not yet been updated.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("pin-set");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList pins = element.getElementsByTagName("pin");
        if (pins.getLength() < 2) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing backup pin: network security config pin-sets should contain at least two pins"
            );
        }
    }
}