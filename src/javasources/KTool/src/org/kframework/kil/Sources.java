// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.kil;

import java.io.File;

public class Sources {

    private Sources() {}

    public static Source generatedBy(Class<?> sourceClass) {
        return new GeneratedSource(sourceClass);
    }

    public static Source fromFile(File filename) {
        return new FileSource(filename);
    }

    public static Source fromFile(String filename) {
        return new FileSource(new File(filename));
    }

    public static Source fromCommandLine(String optionName) {
        return new CommandLineSource(optionName);
    }
}
