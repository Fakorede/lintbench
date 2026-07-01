package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Map<String, String> KNOWN_TYPOS = new HashMap<>();
    static {
        KNOWN_TYPOS.put("activty", "activity");
        KNOWN_TYPOS.put("activiy", "activity");
        KNOWN_TYPOS.put("applcation", "application");
        KNOWN_TYPOS.put("applicaion", "application");
        KNOWN_TYPOS.put("servce", "service");
        KNOWN_TYPOS.put("serivce", "service");
        KNOWN_TYPOS.put("reciever", "receiver");
        KNOWN_TYPOS.put("recevier", "receiver");
        KNOWN_TYPOS.put("provder", "provider");
        KNOWN_TYPOS.put("providor", "provider");
        KNOWN_TYPOS.put("uses-permision", "uses-permission");
        KNOWN_TYPOS.put("uses-permisson", "uses-permission");
        KNOWN_TYPOS.put("intent-filer", "intent-filter");
        KNOWN_TYPOS.put("intet-filter", "intent-filter");
        KNOWN_TYPOS.put("metadata", "meta-data");
        KNOWN_TYPOS.put("uses-featue", "uses-feature");
        KNOWN_TYPOS.put("uses-libary", "uses-library");
        KNOWN_TYPOS.put("activiy-alias", "activity-alias");
        KNOWN_TYPOS.put("actvity-alias", "activity-alias");
        KNOWN_TYPOS.put("manifiest", "manifest");
        KNOWN_TYPOS.put("instrumenation", "instrumentation");
        KNOWN_TYPOS.put("permision", "permission");
        KNOWN_TYPOS.put("permisson", "permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String localName = tagName;
        int colonIndex = tagName.indexOf(':');
        if (colonIndex != -1) {
            localName = tagName.substring(colonIndex + 1);
        }

        String correction = KNOWN_TYPOS.get(localName);
        if (correction != null) {
            String message = String.format("Misspelled tag `%s`: did you mean `%s`?", localName, correction);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}