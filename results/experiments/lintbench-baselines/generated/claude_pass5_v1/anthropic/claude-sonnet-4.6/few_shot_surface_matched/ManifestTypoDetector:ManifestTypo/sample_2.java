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
     * Map from likely typo tag names to the correct tag name they resemble.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // activity typos
        TYPOS.put("activty", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("activitiy", "activity");
        TYPOS.put("acticity", "activity");
        TYPOS.put("actiity", "activity");

        // activity-alias typos
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activity-alis", "activity-alias");
        TYPOS.put("activity-aliase", "activity-alias");

        // application typos
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaiton", "application");
        TYPOS.put("appliation", "application");
        TYPOS.put("aplication", "application");
        TYPOS.put("appication", "application");

        // manifest typos
        TYPOS.put("manifets", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifset", "manifest");
        TYPOS.put("mainfest", "manifest");
        TYPOS.put("manifiest", "manifest");

        // service typos
        TYPOS.put("serivce", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("sevice", "service");
        TYPOS.put("srevice", "service");
        TYPOS.put("servcei", "service");

        // receiver typos
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("receiever", "receiver");
        TYPOS.put("recever", "receiver");

        // provider typos
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("proivder", "provider");
        TYPOS.put("providr", "provider");
        TYPOS.put("provicer", "provider");

        // uses-permission typos
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");

        // uses-feature typos
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-featrue", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-feautre", "uses-feature");

        // uses-sdk typos
        TYPOS.put("uses-ssk", "uses-sdk");
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("uses-dsk", "uses-sdk");

        // uses-library typos
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libarary", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-librray", "uses-library");

        // intent-filter typos
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("inten-filter", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");

        // permission typos
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("premission", "permission");
        TYPOS.put("permssion", "permission");

        // permission-group typos
        TYPOS.put("permission-grp", "permission-group");
        TYPOS.put("permission-gropu", "permission-group");
        TYPOS.put("permision-group", "permission-group");

        // permission-tree typos
        TYPOS.put("permission-tre", "permission-tree");
        TYPOS.put("permision-tree", "permission-tree");

        // meta-data typos
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("met-data", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("meta-date", "meta-data");

        // action typos
        TYPOS.put("acton", "action");
        TYPOS.put("aciton", "action");
        TYPOS.put("actoin", "action");

        // category typos
        TYPOS.put("catagory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("catgory", "category");
        TYPOS.put("caregory", "category");

        // data typos
        TYPOS.put("dta", "data");
        TYPOS.put("dat", "data");

        // grant-uri-permission typos
        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-uri-permisson", "grant-uri-permission");
        TYPOS.put("grant-uri-premission", "grant-uri-permission");

        // instrumentation typos
        TYPOS.put("instrumantation", "instrumentation");
        TYPOS.put("instrumetation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrmentation", "instrumentation");

        // supports-screens typos
        TYPOS.put("supports-screen", "supports-screens");
        TYPOS.put("suports-screens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");

        // compatible-screens typos
        TYPOS.put("compatible-screen", "compatible-screens");
        TYPOS.put("compatable-screens", "compatible-screens");

        // screen typos
        TYPOS.put("screan", "screen");
        TYPOS.put("sreen", "screen");

        // path-permission typos
        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("path-permisson", "path-permission");
        TYPOS.put("pat-permission", "path-permission");

        // queries typos
        TYPOS.put("querie", "queries");
        TYPOS.put("queires", "queries");
        TYPOS.put("quereis", "queries");

        // package typos
        TYPOS.put("packge", "package");
        TYPOS.put("pakage", "package");
        TYPOS.put("pacakge", "package");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Strip any namespace prefix for lookup
        String localName = tagName;
        int colonIndex = tagName.indexOf(':');
        if (colonIndex >= 0) {
            localName = tagName.substring(colonIndex + 1);
        }

        String suggestion = TYPOS.get(localName);
        if (suggestion == null) {
            suggestion = TYPOS.get(tagName);
        }

        if (suggestion != null) {
            String message =
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tagName, suggestion);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }
}