package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ManifestTypoDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, String> TYPOS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("activty", "activity");
        map.put("actvity", "activity");
        map.put("activit", "activity");
        map.put("aplication", "application");
        map.put("applicaton", "application");
        map.put("manifset", "manifest");
        map.put("reciever", "receiver");
        map.put("recever", "receiver");
        map.put("reciver", "receiver");
        map.put("servce", "service");
        map.put("serivce", "service");
        map.put("provder", "provider");
        map.put("providor", "provider");
        map.put("intentfilter", "intent-filter");
        map.put("inttent-filter", "intent-filter");
        map.put("usespermission", "uses-permission");
        map.put("metadata", "meta-data");
        map.put("metadate", "meta-data");
        map.put("usesfeature", "uses-feature");
        map.put("usessdk", "uses-sdk");
        map.put("suports-screens", "supports-screens");
        map.put("support-screens", "supports-screens");
        map.put("compitable-screens", "compatible-screens");
        map.put("instrumenation", "instrumentation");
        map.put("permissiongroup", "permission-group");
        map.put("permissiontree", "permission-tree");
        map.put("useslibrary", "uses-library");
        map.put("usesconfiguration", "uses-configuration");
        TYPOS = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String suggestion = TYPOS.get(tag);
        if (suggestion != null) {
            String message = String.format("Suspicious typo in manifest tag: Did you mean `<%s>`?", suggestion);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}