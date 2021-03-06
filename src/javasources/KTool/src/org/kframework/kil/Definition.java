// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import org.kframework.compile.sharing.DataStructureSortCollector;
import org.kframework.compile.sharing.TokenSortCollector;
import org.kframework.kil.loader.*;
import org.kframework.kil.visitors.Visitor;
import org.kframework.parser.DefinitionLoader;
import org.kframework.utils.general.GlobalSettings;
import org.kframework.utils.xml.XML;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Represents a language definition.
 * Includes contents from all {@code required}-d files.
 * @see DefinitionLoader
 */
public class Definition extends ASTNode implements Interfaces.MutableList<DefinitionItem, Enum<?>> {

    private List<DefinitionItem> items;
    private String mainFile;
    private String mainModule;
    /** An index of all modules in {@link #items} by name */
    private Map<String, Module> modulesMap;
    private String mainSyntaxModule;

    public Definition() {
        super();
    }

    public Definition(Definition d) {
        super(d);
        this.mainFile = d.mainFile;
        this.mainModule = d.mainModule;
        this.mainSyntaxModule = d.mainSyntaxModule;
        this.items = d.items;
    }

    public Definition(Element element) {
        super(element);

        mainFile = element.getAttribute(Constants.MAINFILE);
        mainModule = element.getAttribute(Constants.MAINMODULE);
        items = new ArrayList<DefinitionItem>();

        List<Element> elements = XML.getChildrenElements(element);
        for (Element e : elements)
            items.add((DefinitionItem) JavaClassesFactory.getTerm(e));
    }

    @Override
    public String toString() {
        String content = "";
        for (DefinitionItem di : items)
            content += di + " \n";

        return "DEF: " + mainFile + " -> " + mainModule + "\n" + content;
    }



    public void setItems(List<DefinitionItem> items) {
        this.items = items;
    }

    public List<DefinitionItem> getItems() {
        return items;
    }

    public void setMainFile(String mainFile) {
        this.mainFile = mainFile;
    }

    public String getMainFile() {
        return mainFile;
    }

    public void setMainModule(String mainModule) {
        this.mainModule = mainModule;
    }

    public String getMainModule() {
        return mainModule;
    }

    public void setMainSyntaxModule(String mainSyntaxModule) {
        this.mainSyntaxModule = mainSyntaxModule;
    }

    public String getMainSyntaxModule() {
        return mainSyntaxModule;
    }

    public void preprocess(org.kframework.kil.loader.Context context) {
        // Collect information
        // this.accept(new AddSymbolicVariablesDeclaration(context, this.getMainSyntaxModule()));
        new UpdateReferencesVisitor(context).visitNode(this);
        new UpdateAssocVisitor(context).visitNode(this);
        new CollectProductionsVisitor(context).visitNode(this);
        context.computeConses();
        new CollectBracketsVisitor(context).visitNode(this);
        new CollectSubsortsVisitor(context).visitNode(this);
        new CollectPrioritiesVisitor(context).visitNode(this);
        new CollectStartSymbolPgmVisitor(context).visitNode(this);
        new CollectConfigCellsVisitor(context).visitNode(this);
        new CollectLocationsVisitor(context).visitNode(this);
        new CountNodesVisitor(context).visitNode(this);
        new CollectVariableTokens(context).visitNode(this);

        /* collect lexical token sorts */
        context.setTokenSorts(TokenSortCollector.collectTokenSorts(this, context));

        /* collect the data structure sorts */
        DataStructureSortCollector dataStructureSortCollector
                = new DataStructureSortCollector(context);
        dataStructureSortCollector.visitNode(this);
        context.setDataStructureSorts(dataStructureSortCollector.getSorts());

        context.makeFreshFunctionNamesMap(this.getSyntaxByTag(Attribute.FRESH_GENERATOR, context));

        /* set the initialized flag */
        context.initialized = true;
    }

    public Map<String, Module> getModulesMap() {
        return modulesMap;
    }

    public void setModulesMap(Map<String, Module> modulesMap) {
        this.modulesMap = modulesMap;
    }

    public Module getSingletonModule() {
        List<Module> modules = new LinkedList<Module>();
        for (DefinitionItem i : this.getItems()) {
            if (i instanceof Module)
                modules.add((Module) i);
        }
        if (modules.size() != 1) {
            String msg = "Should have been only one module when calling this method.";
            GlobalSettings.kem.registerInternalError(msg, this);
        }
        return modules.get(0);
    }

    @Override
    public Definition shallowCopy() {
        return new Definition(this);
    }

    public Configuration getSingletonConfiguration() throws ConfigurationNotUnique, ConfigurationNotFound {
        Configuration result = null;
        for (DefinitionItem i : this.getItems()) {
            if (i instanceof Module) {
                if (i.isPredefined())
                    continue;
                for (ModuleItem j : ((Module) i).getItems()) {
                    if (j instanceof Configuration) {
                        if (result != null) {
                            throw new ConfigurationNotUnique();
                        } else {
                            result = (Configuration)j;
                        }
                    }
                }
            }
        }
        if (result == null)
            throw new ConfigurationNotFound();
        return result;
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }

    @Override
    public List<DefinitionItem> getChildren(Enum<?> _) {
        return items;
    }

    @Override
    public void setChildren(List<DefinitionItem> children, Enum<?> _) {
        this.items = children;
    }
}
