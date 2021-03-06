// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.utils.options;

import java.io.File;
import java.io.FilenameFilter;

import org.kframework.utils.general.GlobalSettings;
import com.beust.jcommander.Parameter;
import com.google.inject.Inject;
import com.google.inject.ProvidedBy;

public class DefinitionLoadingOptions {

    public DefinitionLoadingOptions() {}

    //TODO(dwightguth): remove in Guice 4.0
    @Inject
    public DefinitionLoadingOptions(Void v) {}

    @Parameter(names={"--directory", "-d"}, description="Path to the directory in which the kompiled " +
            "K definition resides. The default is the unique, only directory with the suffix '-kompiled' " +
            "in the current directory. A definition may also be specified with the 'KRUN_COMPILED_DEF' " +
            "environment variable, in which case it is used if the option is not specified on the command line.")
    private File directory;

    public File definition() {
        File directory = null;
        File[] dirs = directory().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i].getAbsolutePath().endsWith("-kompiled")) {
                if (directory != null) {
                    GlobalSettings.kem.registerCriticalError("Multiple compiled definitions found in the "
                            + "current working directory: " + directory.getAbsolutePath() + " and " +
                            dirs[i].getAbsolutePath());
                } else {
                    directory = dirs[i];
                }
            }
        }

        if (directory == null) {
            GlobalSettings.kem.registerCriticalError("Could not find a compiled definition. " +
                    "Use --directory to specify one.");
        }
        if (!directory.isDirectory()) {
            GlobalSettings.kem.registerCriticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }

    private File directory() {
        if (directory == null) {
            if (System.getenv("KRUN_COMPILED_DEF") != null) {
                directory = new File(System.getenv("KRUN_COMPILED_DEF"));
            } else {
                directory = new File(".");
            }
        }
        if (!directory.isDirectory()) {
            GlobalSettings.kem.registerCriticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }
}
