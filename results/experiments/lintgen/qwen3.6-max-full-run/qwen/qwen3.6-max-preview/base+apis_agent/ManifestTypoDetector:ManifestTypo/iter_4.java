package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
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
        TYPOS.put("actvity", "activity");
        TYPOS.put("acitivity", "activity");
        TYPOS.put("avtivity", "activity");
        TYPOS.put("aplication", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaion", "application");
        TYPOS.put("aplicaton", "application");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("reciver", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("servce", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("servise", "service");
        TYPOS.put("sevice", "service");
        TYPOS.put("provder", "provider");
        TYPOS.put("provieder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intentflter", "intent-filter");
        TYPOS.put("intent-flter", "intent-filter");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-permissin", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permissions", "uses-permission");
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("metadta", "meta-data");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("manifast", "manifest");
        TYPOS.put("mainfest", "manifest");
        TYPOS.put("menifest", "manifest");
        TYPOS.put("usesfeature", "uses-feature");
        TYPOS.put("uses-feautre", "uses-feature");
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-features", "uses-feature");
        TYPOS.put("uses-featue", "uses-feature");
        TYPOS.put("uses-feauture", "uses-feature");
        TYPOS.put("uses-fetaure", "uses-feature");
        TYPOS.put("uses-featuer", "uses-feature");
        TYPOS.put("usessdk", "uses-sdk");
        TYPOS.put("uses-sdks", "uses-sdk");
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("useslibrary", "uses-library");
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libray", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-libraries", "uses-library");
        TYPOS.put("uses-librery", "uses-library");
        TYPOS.put("suports-screens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");
        TYPOS.put("supports-screns", "supports-screens");
        TYPOS.put("compitable-screens", "compatible-screens");
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatible-screen", "compatible-screens");
        TYPOS.put("compatibile-screens", "compatible-screens");
        TYPOS.put("instrumntation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrmentation", "instrumentation");
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("permissin", "permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        int colonIndex = tagName.indexOf(':');
        if (colonIndex != -1) {
            tagName = tagName.substring(colonIndex + 1);
        }
        String lower = tagName.toLowerCase(Locale.US);
        String correction = TYPOS.get(lower);
        if (correction != null) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tagName, correction);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}