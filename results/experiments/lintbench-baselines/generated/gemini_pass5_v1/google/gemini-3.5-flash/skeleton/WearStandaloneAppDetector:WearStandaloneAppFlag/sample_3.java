package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                            + "your application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean isWatchApp = false;
        
        NodeList usesFeatures = element.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element child = (Element) usesFeatures.item(i);
            String name = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.hardware.type.watch".equals(name)) {
                isWatchApp = true;
                break;
            }
        }

        if (!isWatchApp) {
            NodeList usesLibraries = element.getElementsByTagName("uses-library");
            for (int i = 0; i < usesLibraries.getLength(); i++) {
                Element child = (Element) usesLibraries.item(i);
                String name = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if ("com.google.android.wearable".equals(name)) {
                    isWatchApp = true;
                    break;
                }
            }
        }

        NodeList metaDatas = element.getElementsByTagName("meta-data");
        Element standaloneMeta = null;
        for (int i = 0; i < metaDatas.getLength(); i++) {
            Element child = (Element) metaDatas.item(i);
            String name = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("com.google.android.wearable.standalone".equals(name)) {
                standaloneMeta = child;
                break;
            }
        }

        if (standaloneMeta == null) {
            if (isWatchApp) {
                NodeList applications = element.getElementsByTagName("application");
                Element target = element;
                if (applications.getLength() > 0) {
                    target = (Element) applications.item(0);
                }
                context.report(
                        ISSUE,
                        target,
                        context.getLocation(target),
                        "Missing Wear standalone app flag"
                );
            }
        } else {
            String value = standaloneMeta.getAttributeNS("http://schemas.android.com/apk/res/android", "value");
            if (!"true".equals(value) && !"false".equals(value) && !value.startsWith("@")) {
                context.report(
                        ISSUE,
                        standaloneMeta,
                        context.getLocation(standaloneMeta),
                        "Invalid Wear standalone app flag value"
                );
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        // No-op
    }
}