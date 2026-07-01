package com.android.tools.lint.checks;

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

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    /**
     * Map from known correct manifest tag names to common misspellings of those tags.
     */
    private static final Map<String, String[]> TYPOS;

    static {
        TYPOS = new HashMap<>();

        TYPOS.put("manifest", new String[]{
                "manifets", "manifiest", "mainifest", "mainfest", "manifst", "menifest"
        });
        TYPOS.put("application", new String[]{
                "aplcation", "applicaton", "applicaion", "applcation", "aplication",
                "applicaiton", "appliation", "applicaton", "appication"
        });
        TYPOS.put("activity", new String[]{
                "actvity", "activty", "activiy", "activiti", "acivity", "ativity",
                "activitiy", "activty"
        });
        TYPOS.put("service", new String[]{
                "sevice", "serivce", "servce", "servcie", "srevice", "serice"
        });
        TYPOS.put("receiver", new String[]{
                "reciver", "reciever", "recever", "recevier", "receiever", "reciver"
        });
        TYPOS.put("provider", new String[]{
                "provder", "providor", "proivder", "provdier", "providr", "provier"
        });
        TYPOS.put("intent-filter", new String[]{
                "intent-fliter", "intent-filtr", "intent-filer", "intentfilter",
                "intent-filtter", "inten-filter", "intnet-filter"
        });
        TYPOS.put("action", new String[]{
                "acton", "actioin", "actoin", "acion", "acction"
        });
        TYPOS.put("category", new String[]{
                "catagory", "categroy", "categori", "caegory", "catgory", "cateogry"
        });
        TYPOS.put("data", new String[]{
                "dta", "dat", "daat"
        });
        TYPOS.put("uses-permission", new String[]{
                "use-permission", "uses-permision", "uses-permisson", "uses-permision",
                "uses-persmission", "uses-permssion", "user-permission", "uses-premission"
        });
        TYPOS.put("uses-feature", new String[]{
                "use-feature", "uses-feture", "uses-featre", "uses-feaure", "user-feature"
        });
        TYPOS.put("uses-sdk", new String[]{
                "use-sdk", "uses-skd", "user-sdk", "uses-dsk"
        });
        TYPOS.put("uses-library", new String[]{
                "use-library", "uses-libary", "uses-libarary", "user-library"
        });
        TYPOS.put("permission", new String[]{
                "permision", "permisson", "persmission", "permssion", "premission"
        });
        TYPOS.put("permission-group", new String[]{
                "permission-grp", "permision-group", "permission-grup"
        });
        TYPOS.put("permission-tree", new String[]{
                "permission-tre", "permision-tree", "permission-tee"
        });
        TYPOS.put("instrumentation", new String[]{
                "instrumenation", "instrumentaion", "instrumentaton", "instrumntation",
                "instrumantation"
        });
        TYPOS.put("meta-data", new String[]{
                "meta-dat", "meta-dta", "metadat", "meta-data", "meata-data", "meta-daat"
        });
        TYPOS.put("activity-alias", new String[]{
                "activity-alais", "activity-alis", "actvity-alias", "activty-alias"
        });
        TYPOS.put("grant-uri-permission", new String[]{
                "grant-uri-permision", "grant-uri-permisson", "grant-url-permission"
        });
        TYPOS.put("path-permission", new String[]{
                "path-permision", "path-permisson", "pat-permission"
        });
        TYPOS.put("supports-screens", new String[]{
                "support-screens", "supports-screen", "suports-screens"
        });
        TYPOS.put("compatible-screens", new String[]{
                "compatible-screen", "compatable-screens", "compatble-screens"
        });
        TYPOS.put("supports-gl-texture", new String[]{
                "supports-gl-texure", "support-gl-texture", "supports-gl-textre"
        });
        TYPOS.put("queries", new String[]{
                "querie", "querys", "quries"
        });
        TYPOS.put("package", new String[]{
                "pakage", "packge", "packege", "pacakge"
        });
        TYPOS.put("profileable", new String[]{
                "profileble", "profilable", "profileabel"
        });
    }

    /**
     * Reverse map: from misspelling to correct tag name.
     */
    private static final Map<String, String> MISSPELLING_TO_CORRECT;

    static {
        MISSPELLING_TO_CORRECT = new HashMap<>();
        for (Map.Entry<String, String[]> entry : TYPOS.entrySet()) {
            String correct = entry.getKey();
            for (String typo : entry.getValue()) {
                MISSPELLING_TO_CORRECT.put(typo, correct);
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to be called for all elements, so return null to get visitElement called
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null) {
            return;
        }

        String correct = MISSPELLING_TO_CORRECT.get(tag);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correct)
            );
            return;
        }

        // Also check for case-insensitive matches against known correct tags
        // (e.g., "Activity" instead of "activity")
        String tagLower = tag.toLowerCase(java.util.Locale.US);
        if (!tag.equals(tagLower) && TYPOS.containsKey(tagLower)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tag, tagLower
                    )
            );
        }
    }
}