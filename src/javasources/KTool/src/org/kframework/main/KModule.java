// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.main;

import com.google.inject.Module;

import java.util.List;

public interface KModule {

    public List<Module> getKompileModules();
    public List<Module> getKastModules();
    public List<Module> getKRunModules();
    public List<Module> getKTestModules();

}
