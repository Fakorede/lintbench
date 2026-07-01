package com.android.tools.lint.checks;

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

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        TYPOS.put("activty", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("aplication", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("servce", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("provder", "provider");
        TYPOS.put("provieder", "provider");
        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intentflter", "intent-filter");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("usesfeature", "uses-feature");
        TYPOS.put("usessdk", "uses-sdk");
        TYPOS.put("useslibrary", "uses-library");
        TYPOS.put("suports-screens", "supports-screens");
        TYPOS.put("compitable-screens", "compatible-screens");
        TYPOS.put("instrumntation", "instrumentation");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        String correction = TYPOS.get(tagName);
        if (correction != null) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tagName, correction);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}