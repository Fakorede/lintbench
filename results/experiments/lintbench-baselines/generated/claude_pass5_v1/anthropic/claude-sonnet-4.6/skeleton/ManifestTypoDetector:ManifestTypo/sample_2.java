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
     * Map of common typos to their correct tag names.
     * Key: typo (misspelled tag), Value: correct tag name
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common manifest tag typos
        TYPOS.put("activty", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("activitys", "activity");
        TYPOS.put("activties", "activity");

        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("premission", "permission");
        TYPOS.put("permisions", "permission");
        TYPOS.put("permssion", "permission");

        TYPOS.put("aplication", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("appliciation", "application");
        TYPOS.put("aplplication", "application");
        TYPOS.put("appication", "application");

        TYPOS.put("sevice", "service");
        TYPOS.put("srevice", "service");
        TYPOS.put("sercive", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("servie", "service");

        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("recieiver", "receiver");

        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("proivder", "provider");
        TYPOS.put("provdier", "provider");

        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intenfilter", "intent-filter");
        TYPOS.put("intentfiler", "intent-filter");
        TYPOS.put("intentfiltr", "intent-filter");
        TYPOS.put("intent-flter", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");

        TYPOS.put("acton", "action");
        TYPOS.put("actoin", "action");
        TYPOS.put("acion", "action");

        TYPOS.put("categoy", "category");
        TYPOS.put("catagory", "category");
        TYPOS.put("categori", "category");
        TYPOS.put("catgory", "category");

        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manfiest", "manifest");
        TYPOS.put("manifset", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("menifest", "manifest");

        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-premission", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");

        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-fetaure", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-feaure", "uses-feature");

        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libarary", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("uses-libraray", "uses-library");

        TYPOS.put("uses-sdk", "uses-sdk"); // Already correct, but keep for reference
        TYPOS.put("use-sdk", "uses-sdk");

        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activiy-alias", "activity-alias");

        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("metadat", "meta-data");
        TYPOS.put("metadata", "meta-data");

        TYPOS.put("suppots-screens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");

        TYPOS.put("compatible-screens", "compatible-screens"); // correct
        TYPOS.put("compatable-screens", "compatible-screens");

        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-uri-permisson", "grant-uri-permission");
        TYPOS.put("grant-uri-premission", "grant-uri-permission");

        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("path-permisson", "path-permission");
        TYPOS.put("path-premission", "path-permission");

        TYPOS.put("instrumentation", "instrumentation"); // correct
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("instrumentaton", "instrumentation");
        TYPOS.put("instrumntation", "instrumentation");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String correctedName = TYPOS.get(tagName);
        if (correctedName != null && !correctedName.equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Possible typo in tag `<%1$s>`; did you mean `<%2$s>`?",
                            tagName, correctedName));
        }
    }
}