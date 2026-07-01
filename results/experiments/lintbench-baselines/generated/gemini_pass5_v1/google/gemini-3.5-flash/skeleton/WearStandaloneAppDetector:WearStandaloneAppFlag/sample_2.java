package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, "
                            + "without a phone app. Add a valid meta-data entry for "
                            + "`com.google.android.wearable.standalone` to your application "
                            + "element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private XmlContext xmlContext;
    private boolean hasWatchFeature;
    private boolean hasStandaloneMetadata;
    private String standaloneValue;
    private Element applicationElement;
    private Element metadataElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            this.xmlContext = (XmlContext) context;
        }
        this.hasWatchFeature = false;
        this.hasStandaloneMetadata = false;
        this.standaloneValue = null;
        this.applicationElement = null;
        this.metadataElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "meta-data", "application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.hardware.type.watch".equals(name)) {
                hasWatchFeature = true;
            }
        } else if ("application".equals(tagName)) {
            applicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("com.google.android.wearable.standalone".equals(name)) {
                hasStandaloneMetadata = true;
                metadataElement = element;
                String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", "value");
                if (value.isEmpty()) {
                    value = element.getAttribute("android:value");
                }
                standaloneValue = value;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasWatchFeature && xmlContext != null) {
            if (!hasStandaloneMetadata) {
                Element target = applicationElement != null ? applicationElement : xmlContext.getDocument().getDocumentElement();
                if (target != null) {
                    xmlContext.report(
                            ISSUE,
                            target,
                            xmlContext.getLocation(target),
                            "Missing 'com.google.android.wearable.standalone' meta-data tag in AndroidManifest.xml");
                }
            } else {
                if (!"true".equals(standaloneValue) && !"false".equals(standaloneValue)) {
                    xmlContext.report(
                            ISSUE,
                            metadataElement,
                            xmlContext.getLocation(metadataElement),
                            "The com.google.android.wearable.standalone value must be 'true' or 'false'");
                }
            }
        }
    }
}