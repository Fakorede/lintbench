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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /**
     * Map from likely typo tag names to the correct tag names they should be.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common typos for manifest tags
        TYPOS.put("activty", "activity");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activityalias", "activity-alias");
        TYPOS.put("activity_alias", "activity-alias");
        TYPOS.put("aplication", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicationn", "application");
        TYPOS.put("uses-premission", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permission-sdk-m", "uses-permission-sdk-23");
        TYPOS.put("uses-permision-sdk-23", "uses-permission-sdk-23");
        TYPOS.put("uses-premission-sdk-23", "uses-permission-sdk-23");
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("broadcastreceiver", "receiver");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intent_filter", "intent-filter");
        TYPOS.put("uses-featur", "uses-feature");
        TYPOS.put("uses-fetaure", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-sdk", "uses-sdk"); // Not a typo but sometimes written incorrectly
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-librray", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("meta_data", "meta-data");
        TYPOS.put("sevice", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("srevice", "service");
        TYPOS.put("profider", "provider");
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("categroy", "category");
        TYPOS.put("catagory", "category");
        TYPOS.put("acton", "action");
        TYPOS.put("acction", "action");
        TYPOS.put("dat", "data");
        TYPOS.put("grnat-uri-permission", "grant-uri-permission");
        TYPOS.put("grant-uri-premission", "grant-uri-permission");
        TYPOS.put("path-premission", "path-permission");
        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("instrumentation", "instrumentation"); // correct, included for completeness
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumntation", "instrumentation");
        TYPOS.put("permision", "permission");
        TYPOS.put("premission", "permission");
        TYPOS.put("permssion", "permission");
        TYPOS.put("permission-grop", "permission-group");
        TYPOS.put("permission-gropu", "permission-group");
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permission-tre", "permission-tree");
        TYPOS.put("permision-tree", "permission-tree");
        TYPOS.put("suppots-screens", "supports-screens");
        TYPOS.put("supports-sceens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("compatibl-screens", "compatible-screens");
        TYPOS.put("compatible-sceens", "compatible-screens");
        TYPOS.put("compatible_screens", "compatible-screens");
        TYPOS.put("uses-configuration", "uses-configuration");
        TYPOS.put("uses-configuation", "uses-configuration");
        TYPOS.put("use-configuration", "uses-configuration");
        TYPOS.put("profle-able", "profileable");
        TYPOS.put("profileble", "profileable");
        TYPOS.put("qurey", "queries");
        TYPOS.put("querie", "queries");
        TYPOS.put("quereis", "queries");
        TYPOS.put("pacakge", "package");
        TYPOS.put("pakage", "package");
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
                            tag,
                            correct));
        }
    }
}