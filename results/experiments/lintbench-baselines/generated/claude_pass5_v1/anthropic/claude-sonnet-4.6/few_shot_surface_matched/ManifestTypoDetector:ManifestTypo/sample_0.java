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
     * Map from likely misspellings to the correct tag name.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // uses-sdk
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("user-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("use-sdk-library", "uses-sdk");

        // uses-permission
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("user-permission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");

        // uses-feature
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("user-feature", "uses-feature");
        TYPOS.put("uses-featur", "uses-feature");
        TYPOS.put("uses-feaure", "uses-feature");

        // uses-library
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("user-library", "uses-library");
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libraray", "uses-library");
        TYPOS.put("uses-lirary", "uses-library");

        // uses-configuration
        TYPOS.put("use-configuration", "uses-configuration");
        TYPOS.put("user-configuration", "uses-configuration");
        TYPOS.put("uses-configuation", "uses-configuration");
        TYPOS.put("uses-cofiguration", "uses-configuration");

        // application
        TYPOS.put("aplplication", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("aplication", "application");
        TYPOS.put("appication", "application");
        TYPOS.put("applicaion", "application");

        // activity
        TYPOS.put("activty", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("actiity", "activity");
        TYPOS.put("activiti", "activity");

        // activity-alias
        TYPOS.put("activity-alis", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("actvity-alias", "activity-alias");

        // service
        TYPOS.put("sevice", "service");
        TYPOS.put("srvice", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("servcie", "service");
        TYPOS.put("serivce", "service");

        // receiver
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("recieiver", "receiver");

        // provider
        TYPOS.put("provder", "provider");
        TYPOS.put("provier", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("provdier", "provider");

        // manifest
        TYPOS.put("manifst", "manifest");
        TYPOS.put("manfest", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("mainifiest", "manifest");
        TYPOS.put("manifiest", "manifest");

        // intent-filter
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intent-flter", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("inten-filter", "intent-filter");
        TYPOS.put("intent-filtter", "intent-filter");

        // meta-data
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meta-daat", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("meata-data", "meta-data");
        TYPOS.put("meta-data-data", "meta-data");

        // permission
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("permssion", "permission");
        TYPOS.put("permisison", "permission");

        // permission-group
        TYPOS.put("permission-grp", "permission-group");
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permisson-group", "permission-group");

        // permission-tree
        TYPOS.put("permission-tre", "permission-tree");
        TYPOS.put("permision-tree", "permission-tree");

        // instrumentation
        TYPOS.put("instrumentaton", "instrumentation");
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("instrumetation", "instrumentation");
        TYPOS.put("instumenation", "instrumentation");

        // supports-screens
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");
        TYPOS.put("suports-screens", "supports-screens");
        TYPOS.put("supprts-screens", "supports-screens");

        // compatible-screens
        TYPOS.put("compatible-screen", "compatible-screens");
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatble-screens", "compatible-screens");

        // grant-uri-permission
        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-uri-permisson", "grant-uri-permission");
        TYPOS.put("grant-url-permission", "grant-uri-permission");

        // path-permission
        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("path-permisson", "path-permission");
        TYPOS.put("pat-permission", "path-permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String correct = TYPOS.get(tag);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tag, correct));
        }
    }
}