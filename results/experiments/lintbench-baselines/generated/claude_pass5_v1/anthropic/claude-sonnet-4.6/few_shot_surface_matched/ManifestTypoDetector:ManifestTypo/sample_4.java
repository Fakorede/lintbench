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
                    "Typo in manifest tag",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    /**
     * Map from common typo/misspelling to the correct tag name.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // activity
        TYPOS.put("activty", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("activitiy", "activity");
        TYPOS.put("activitty", "activity");
        TYPOS.put("acivity", "activity");

        // activity-alias
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activity-alis", "activity-alias");

        // application
        TYPOS.put("aplcation", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applicaion", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("appication", "application");
        TYPOS.put("applicaiton", "application");
        TYPOS.put("applicatoin", "application");
        TYPOS.put("aplication", "application");

        // manifest
        TYPOS.put("mainfest", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifset", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("manigest", "manifest");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("manfiest", "manifest");
        TYPOS.put("maniest", "manifest");

        // permission
        TYPOS.put("premission", "permission");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("permisison", "permission");
        TYPOS.put("permssion", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("permision", "permission");

        // permission-group
        TYPOS.put("permission-grp", "permission-group");
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permission-grup", "permission-group");

        // permission-tree
        TYPOS.put("permision-tree", "permission-tree");
        TYPOS.put("permission-tre", "permission-tree");

        // provider
        TYPOS.put("proivder", "provider");
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("prvider", "provider");
        TYPOS.put("provied", "provider");

        // receiver
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recieiver", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("receiever", "receiver");

        // service
        TYPOS.put("servce", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("sercice", "service");
        TYPOS.put("sevice", "service");
        TYPOS.put("serice", "service");

        // uses-feature
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-feautre", "uses-feature");
        TYPOS.put("uses-featrue", "uses-feature");
        TYPOS.put("uses-feture", "uses-feature");

        // uses-library
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libraray", "uses-library");
        TYPOS.put("uses-libraray", "uses-library");

        // uses-permission
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-premission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("user-permission", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");

        // uses-permission-sdk-23
        TYPOS.put("use-permission-sdk-23", "uses-permission-sdk-23");
        TYPOS.put("uses-premission-sdk-23", "uses-permission-sdk-23");

        // uses-sdk
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("user-sdk", "uses-sdk");

        // intent-filter
        TYPOS.put("intent-fileter", "intent-filter");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("intet-filter", "intent-filter");

        // action
        TYPOS.put("acton", "action");
        TYPOS.put("acition", "action");
        TYPOS.put("acion", "action");

        // category
        TYPOS.put("catagory", "category");
        TYPOS.put("caregory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("categor", "category");
        TYPOS.put("catgory", "category");

        // data
        TYPOS.put("daat", "data");
        TYPOS.put("dta", "data");

        // meta-data
        TYPOS.put("meta-daat", "meta-data");
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("met-data", "meta-data");
        TYPOS.put("meta-data", "meta-data"); // correct, but keep for completeness

        // instrumentation
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("instruemntation", "instrumentation");
        TYPOS.put("instrumntation", "instrumentation");

        // supports-screens
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");
        TYPOS.put("suports-screens", "supports-screens");

        // compatible-screens
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatible-screen", "compatible-screens");
        TYPOS.put("compatble-screens", "compatible-screens");

        // grant-uri-permission
        TYPOS.put("grant-uri-premission", "grant-uri-permission");
        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-ur-permission", "grant-uri-permission");

        // path-permission
        TYPOS.put("path-premission", "path-permission");
        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("pat-permission", "path-permission");

        // queries
        TYPOS.put("querie", "queries");
        TYPOS.put("queires", "queries");
        TYPOS.put("quries", "queries");

        // package
        TYPOS.put("pakage", "package");
        TYPOS.put("pacakge", "package");
        TYPOS.put("packge", "package");

        // profileable
        TYPOS.put("profileble", "profileable");
        TYPOS.put("profilable", "profileable");
        TYPOS.put("profileeble", "profileable");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        // Strip namespace prefix if present
        int colonIndex = tagName.indexOf(':');
        String localName = colonIndex >= 0 ? tagName.substring(colonIndex + 1) : tagName;

        String suggestion = TYPOS.get(localName);
        if (suggestion == null) {
            suggestion = TYPOS.get(tagName);
        }

        if (suggestion != null && !suggestion.equals(localName) && !suggestion.equals(tagName)) {
            String message = String.format(
                    "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                    tagName, suggestion);
            context.report(ISSUE, element, context.getElementLocation(element), message);
        }
    }
}