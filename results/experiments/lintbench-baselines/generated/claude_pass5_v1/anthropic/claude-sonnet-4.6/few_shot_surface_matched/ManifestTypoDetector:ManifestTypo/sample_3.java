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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    // Map from likely typo tag names to the correct tag names
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common misspellings of manifest tags
        TYPOS.put("manifest", null); // correct, no typo
        TYPOS.put("Manifest", "manifest");
        TYPOS.put("menifest", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifset", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("mainfest", "manifest");

        TYPOS.put("uses-sdk", null); // correct
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("user-sdk", "uses-sdk");
        TYPOS.put("uses-permission", null); // correct
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-premission", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("uses-permissions", "uses-permission");

        TYPOS.put("application", null); // correct
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applicaion", "application");
        TYPOS.put("appliction", "application");
        TYPOS.put("appication", "application");
        TYPOS.put("aplication", "application");
        TYPOS.put("applicaiton", "application");

        TYPOS.put("activity", null); // correct
        TYPOS.put("activty", "activity");
        TYPOS.put("acivity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("actviity", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activitiy", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("acticity", "activity");

        TYPOS.put("activity-alias", null); // correct
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");

        TYPOS.put("service", null); // correct
        TYPOS.put("sevice", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("serice", "service");
        TYPOS.put("servcie", "service");

        TYPOS.put("receiver", null); // correct
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("receiever", "receiver");

        TYPOS.put("provider", null); // correct
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("provier", "provider");
        TYPOS.put("providre", "provider");

        TYPOS.put("intent-filter", null); // correct
        TYPOS.put("intent-filtre", "intent-filter");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("inten-filter", "intent-filter");
        TYPOS.put("intent-filters", "intent-filter");

        TYPOS.put("action", null); // correct
        TYPOS.put("acton", "action");
        TYPOS.put("actiom", "action");
        TYPOS.put("aciton", "action");

        TYPOS.put("category", null); // correct
        TYPOS.put("catagory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("catetgory", "category");
        TYPOS.put("catgory", "category");

        TYPOS.put("data", null); // correct

        TYPOS.put("meta-data", null); // correct
        TYPOS.put("meta-date", "meta-data");
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("meta-deta", "meta-data");

        TYPOS.put("uses-library", null); // correct
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-libray", "uses-library");
        TYPOS.put("uses-libraray", "uses-library");

        TYPOS.put("uses-feature", null); // correct
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-featre", "uses-feature");

        TYPOS.put("permission", null); // correct
        TYPOS.put("permision", "permission");
        TYPOS.put("premission", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("permissions", "permission");

        TYPOS.put("permission-group", null); // correct
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permission-grp", "permission-group");

        TYPOS.put("permission-tree", null); // correct
        TYPOS.put("permision-tree", "permission-tree");

        TYPOS.put("instrumentation", null); // correct
        TYPOS.put("instrumantation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumnetation", "instrumentation");
        TYPOS.put("instrumetation", "instrumentation");

        TYPOS.put("supports-screens", null); // correct
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");

        TYPOS.put("compatible-screens", null); // correct
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatible-screen", "compatible-screens");

        TYPOS.put("supports-gl-texture", null); // correct
        TYPOS.put("support-gl-texture", "supports-gl-texture");

        TYPOS.put("grant-uri-permission", null); // correct
        TYPOS.put("grant-uri-permissions", "grant-uri-permission");
        TYPOS.put("grant-url-permission", "grant-uri-permission");

        TYPOS.put("path-permission", null); // correct
        TYPOS.put("path-permision", "path-permission");

        TYPOS.put("queries", null); // correct
        TYPOS.put("quaries", "queries");
        TYPOS.put("querys", "queries");

        TYPOS.put("package", null); // correct
        TYPOS.put("packge", "package");
        TYPOS.put("pakage", "package");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        // Strip namespace prefix if present
        int colon = tag.indexOf(':');
        if (colon >= 0) {
            tag = tag.substring(colon + 1);
        }

        if (TYPOS.containsKey(tag)) {
            String correct = TYPOS.get(tag);
            if (correct != null) {
                String message = String.format(
                        "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correct);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }
}