package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Manifest Typo",
                    "Looks through the manifest for tag names that look like likely misspellings. "
                            + "Misspelled manifest tags are ignored by the framework, which can "
                            + "cause missing components, permissions, or configuration.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final java.util.Map<String, String> TYPO_MAP = createTypoMap();

    private static java.util.Map<String, String> createTypoMap() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("mainfest", "manifest");
        map.put("manfest", "manifest");
        map.put("applicaton", "application");
        map.put("aplication", "application");
        map.put("appliction", "application");
        map.put("activty", "activity");
        map.put("actvity", "activity");
        map.put("activitiy", "activity");
        map.put("servce", "service");
        map.put("servicee", "service");
        map.put("reciever", "receiver");
        map.put("reciver", "receiver");
        map.put("providr", "provider");
        map.put("provder", "provider");
        map.put("provdier", "provider");
        map.put("intentfilter", "intent-filter");
        map.put("intent-filters", "intent-filter");
        map.put("intenet-filter", "intent-filter");
        map.put("catagory", "category");
        map.put("catgory", "category");
        map.put("metadata", "meta-data");
        map.put("use-permission", "uses-permission");
        map.put("uses-permision", "uses-permission");
        map.put("uses-permissions", "uses-permission");
        map.put("uses-permision-sdk-23", "uses-permission-sdk-23");
        map.put("use-sdk", "uses-sdk");
        map.put("uses-sd", "uses-sdk");
        map.put("use-feature", "uses-feature");
        map.put("uses-freature", "uses-feature");
        map.put("permision", "permission");
        map.put("premission", "permission");
        map.put("permision-group", "permission-group");
        map.put("permision-tree", "permission-tree");
        map.put("support-screens", "supports-screens");
        map.put("compatible-screen", "compatible-screens");
        map.put("compatiable-screens", "compatible-screens");
        map.put("path-permision", "path-permission");
        map.put("path-permisison", "path-permission");
        map.put("grant-uri-permision", "grant-uri-permission");
        map.put("grant-url-permission", "grant-uri-permission");
        map.put("grant-uri-permisison", "grant-uri-permission");
        map.put("instrumentaion", "instrumentation");
        map.put("instrumentaiton", "instrumentation");
        map.put("use-library", "uses-library");
        map.put("uses-libary", "uses-library");
        return map;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return TYPO_MAP.keySet();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        String correct = TYPO_MAP.get(tag);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The manifest tag \"" + tag + "\" looks like a typo; did you mean \""
                            + correct + "\"?");
        }
    }
}