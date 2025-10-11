package com.threethan.launcher.helper;

import android.content.Context;

import java.util.List;
import java.util.stream.Collectors;

public abstract class VariantHelper {
    public static final String VARIANT_SIDELOAD = "com.threethan.launcher";
    public static final String VARIANT_METASTORE = "com.threethan.launcher.metastore";
    public static final String VARIANT_PLAYSTORE = "com.threethan.launcher.playstore";

    /** Potential variant package names */
    private static final List<String> variants = List.of(
            VARIANT_SIDELOAD,
            VARIANT_METASTORE,
            VARIANT_PLAYSTORE);



    /** Get the package names of all variants which are present on the device,
     * excluding this one. */
    public static List<String> getInstalledVariantPackages(Context context) {
        return variants.stream().filter(pkg
                        -> (AppExt.doesPackageExist(context, pkg))
                        && !pkg.equals(context.getPackageName()))
                .collect(Collectors.toList());
    }

    /** Check if any variants are installed on the device, excluding this one. */
    public static boolean hasVariants(Context context) {
        return !getInstalledVariantPackages(context).isEmpty();
    }
}
