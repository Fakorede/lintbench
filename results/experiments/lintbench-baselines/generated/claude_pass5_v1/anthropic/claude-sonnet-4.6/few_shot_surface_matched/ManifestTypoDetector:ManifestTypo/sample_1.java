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

    // Map from common misspellings to their correct tag names
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // activity typos
        TYPOS.put("activty", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("actiity", "activity");
        TYPOS.put("actviity", "activity");
        TYPOS.put("activitiy", "activity");

        // activity-alias typos
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("actvity-alias", "activity-alias");

        // service typos
        TYPOS.put("sevice", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("srevice", "service");
        TYPOS.put("serice", "service");

        // receiver typos
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("receiever", "receiver");

        // provider typos
        TYPOS.put("provder", "provider");
        TYPOS.put("proivder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("provier", "provider");
        TYPOS.put("privoder", "provider");

        // application typos
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaion", "application");
        TYPOS.put("appication", "application");
        TYPOS.put("appliction", "application");
        TYPOS.put("aplication", "application");

        // manifest typos
        TYPOS.put("manifset", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("manfiest", "manifest");
        TYPOS.put("mainfest", "manifest");

        // uses-permission typos
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");
        TYPOS.put("uses-permsision", "uses-permission");
        TYPOS.put("uses-premission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");

        // uses-feature typos
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-featrue", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-freature", "uses-feature");

        // uses-library typos
        TYPOS.put("uses-libraray", "uses-library");
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-libaray", "uses-library");

        // uses-sdk typos
        TYPOS.put("uses-ssk", "uses-sdk");
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");

        // intent-filter typos
        TYPOS.put("intent-flter", "intent-filter");
        TYPOS.put("intent-fileter", "intent-filter");
        TYPOS.put("intent-fitler", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("intet-filter", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");

        // meta-data typos
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("mete-data", "meta-data");
        TYPOS.put("meta-date", "meta-data");

        // permission typos
        TYPOS.put("permision", "permission");
        TYPOS.put("permssion", "permission");
        TYPOS.put("premission", "permission");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisison", "permission");

        // permission-group typos
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permission-grup", "permission-group");
        TYPOS.put("premission-group", "permission-group");

        // permission-tree typos
        TYPOS.put("permision-tree", "permission-tree");
        TYPOS.put("permission-tre", "permission-tree");
        TYPOS.put("premission-tree", "permission-tree");

        // instrumentation typos
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("instrumentaton", "instrumentation");
        TYPOS.put("instrumenttion", "instrumentation");

        // supports-screens typos
        TYPOS.put("supports-sceens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");

        // compatible-screens typos
        TYPOS.put("compatible-sceens", "compatible-screens");
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatible-screen", "compatible-screens");

        // action typos
        TYPOS.put("acton", "action");
        TYPOS.put("actoin", "action");
        TYPOS.put("aciton", "action");

        // category typos
        TYPOS.put("catagory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("categiry", "category");
        TYPOS.put("caegory", "category");

        // data typos
        TYPOS.put("dta", "data");
        TYPOS.put("dat", "data");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String correct = TYPOS.get(tagName);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tagName, correct));
        }
    }
}