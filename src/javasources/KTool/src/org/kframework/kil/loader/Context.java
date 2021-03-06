// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil.loader;

import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Attribute.Key;
import org.kframework.kil.Cell;
import org.kframework.kil.Cell.Ellipses;
import org.kframework.kil.CellDataStructure;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.KApp;
import org.kframework.kil.KInjectedLabel;
import org.kframework.kil.Production;
import org.kframework.kil.Sort;
import org.kframework.kil.Term;
import org.kframework.kil.UserList;
import org.kframework.kompile.KompileOptions;
import org.kframework.krun.ColorOptions;
import org.kframework.krun.KRunOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.Poset;
import org.kframework.utils.StringUtil;
import org.kframework.utils.general.GlobalSettings;
import org.kframework.utils.options.SMTOptions;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Context implements Serializable {

    public static final Set<String> generatedTags = ImmutableSet.of(
            "kgeneratedlabel",
            "prefixlabel");

    public static final Set<Key<String>> parsingTags = ImmutableSet.of(
        Attribute.keyOf("left"),
        Attribute.keyOf("right"),
        Attribute.keyOf("non-assoc"));

    public static final Set<String> specialTerminals = ImmutableSet.of(
        "(",
        ")",
        ",",
        "[",
        "]",
        "{",
        "}");

    /**
     * Represents the bijection map between conses and productions.
     */
    public Set<Production> productions = new HashSet<>();
    /**
     * Represents a map from all Klabels in string representation
     * to sets of corresponding productions.
     * why?
     */
    public SetMultimap<String, Production> klabels = HashMultimap.create();
    public SetMultimap<String, Production> tags = HashMultimap.create();
    public Map<String, Cell> cells = new HashMap<String, Cell>();
    public Map<String, Sort> cellSorts = new HashMap<>();
    public Map<Sort, Production> listProductions = new HashMap<>();
    public SetMultimap<String, Production> listKLabels = HashMultimap.create();
    public Map<String, String> listLabelSeparator = new HashMap<>();
    public Map<String, ASTNode> locations = new HashMap<String, ASTNode>();

    public Map<Sort, Production> canonicalBracketForSort = new HashMap<>();
    private Poset<Sort> subsorts = Poset.create();
    public java.util.Set<Sort> definedSorts = Sort.getBaseSorts();
    private Poset<String> priorities = Poset.create();
    private Poset<String> assocLeft = Poset.create();
    private Poset<String> assocRight = Poset.create();
    private Poset<String> modules = Poset.create();
    private Poset<String> fileRequirements = Poset.create();
    public Sort startSymbolPgm = Sort.K;
    public Map<String, Sort> configVarSorts = new HashMap<>();
    public File dotk = null;
    public File kompiled = null;
    public boolean initialized = false;
    protected java.util.List<String> komputationCells = null;
    public Map<String, CellDataStructure> cellDataStructures = new HashMap<>();
    public Set<Sort> variableTokenSorts = new HashSet<>();
    public HashMap<Sort, String> freshFunctionNames = new HashMap<>();

    private BiMap<String, Production> conses;

    public int numModules, numSentences, numProductions, numCells;

    public void printStatistics() {
        Formatter f = new Formatter(System.out);
        f.format("%n");
        f.format("%-60s = %5d%n", "Number of Modules", numModules);
        f.format("%-60s = %5d%n", "Number of Sentences", numSentences);
        f.format("%-60s = %5d%n", "Number of Productions", numProductions);
        f.format("%-60s = %5d%n", "Number of Cells", numCells);
    }

    /**
     * The two structures below are populated by the InitializeConfigurationStructure step of the compilation.
     * configurationStructureMap represents a map from cell names to structures containing cell data.
     * maxConfigurationLevel represent the maximum level of cell nesting in the configuration.
     */
    private ConfigurationStructureMap configurationStructureMap = new ConfigurationStructureMap();
    private int maxConfigurationLevel = -1;

    /**
     * {@link Map} of sort names into {@link DataStructureSort} instances.
     */
    private Map<Sort, DataStructureSort> dataStructureSorts;

    /**
     * {@link Set} of sorts with lexical productions.
     */
    private Set<Sort> tokenSorts;


    public java.util.List<String> getKomputationCells() {
        return kompileOptions.experimental.kCells;
    }

    public ConfigurationStructureMap getConfigurationStructureMap() {
        return configurationStructureMap;
    }

    public int getMaxConfigurationLevel() {
        return maxConfigurationLevel;
    }

    public void setMaxConfigurationLevel(int maxConfigurationLevel) {
        this.maxConfigurationLevel = maxConfigurationLevel;
    }

    private void initSubsorts() {
        subsorts.addElement(Sort.KLABEL);
        subsorts.addRelation(Sort.KLIST, Sort.K);
        subsorts.addRelation(Sort.K, Sort.KITEM);
        subsorts.addRelation(Sort.KITEM, Sort.KRESULT);
        subsorts.addRelation(Sort.BAG, Sort.BAG_ITEM);
    }

    // TODO(dwightguth): remove these fields and replace with injected dependencies
    @Deprecated @Inject public transient GlobalOptions globalOptions;
    @Deprecated public KompileOptions kompileOptions;
    @Deprecated @Inject(optional=true) public transient SMTOptions smtOptions;
    @Deprecated @Inject(optional=true) public KRunOptions krunOptions;
    @Deprecated @Inject(optional=true) public ColorOptions colorOptions;

    public Context() {
        initSubsorts();
    }

    public void addProduction(Production p) {
        productions.add(p);
        if (p.getKLabel() != null) {
            klabels.put(p.getKLabel(), p);
            tags.put(p.getKLabel(), p);
            if (p.isListDecl()) {
                listKLabels.put(p.getTerminatorKLabel(), p);
            }
        }
        if (p.isListDecl()) {
            listProductions.put(p.getSort(), p);
        }
        for (Attribute<?> a : p.getAttributes().values()) {
            tags.put(a.getKey().toString(), p);
        }
    }

    public void removeProduction(Production p) {
        productions.remove(p);
         if (p.getKLabel() != null) {
            klabels.remove(p.getKLabel(), p);
            tags.remove(p.getKLabel(), p);
            if (p.isListDecl()) {
                listKLabels.remove(p.getTerminatorKLabel(), p);
            }
        }
        if (p.isListDecl()) {
            // AndreiS: this code assumes each list sort has only one production
            listProductions.remove(p.getSort());
        }
        for (Attribute<?> a : p.getAttributes().values()) {
            tags.remove(a.getKey().toString(), p);
        }
    }

    public void addCellDecl(Cell c) {
        cells.put(c.getLabel(), c);

        String sortName = c.getCellAttributes().get(Cell.SORT_ATTRIBUTE);
        Sort sort = sortName == null ? c.getContents().getSort() : Sort.of(sortName);
        if (sort.equals(Sort.BAG_ITEM))
            sort = Sort.BAG;
        cellSorts.put(c.getLabel(), sort);
    }

    public Sort getCellSort(Cell cell) {
        Sort sort = cellSorts.get(cell.getLabel());

        if (sort == null) {
            if (cell.getLabel().equals("k"))
                sort = Sort.K;
            else if (cell.getLabel().equals("T"))
                sort = Sort.BAG;
            else if (cell.getLabel().equals("generatedTop"))
                sort = Sort.BAG;
            else if (cell.getLabel().equals("freshCounter"))
                sort = Sort.K;
            else if (cell.getLabel().equals("path-condition"))
                sort = Sort.K;
        } else {
            // if the k cell is opened, then the sort needs to take into consideration desugaring
            if (cell.getEllipses() != Ellipses.NONE) {
                if (isSubsortedEq(Sort.LIST, sort))
                    sort = Sort.LIST;
                else if (isSubsortedEq(Sort.BAG, sort))
                    sort = Sort.BAG;
                else if (isSubsortedEq(Sort.MAP, sort))
                    sort = Sort.MAP;
                else if (isSubsortedEq(Sort.SET, sort))
                    sort = Sort.SET;
                else // any other computational sort
                    sort = Sort.K;
            }
        }
        return sort;
    }

    public boolean isListSort(Sort sort) {
        return listProductions.containsKey(sort);
    }

    /**
     * Returns a unmodifiable view of all sorts.
     */
    public Set<Sort> getAllSorts() {
        return Collections.unmodifiableSet(subsorts.getElements());
    }

    /**
     * Takes a List sort and returns the sort of the elements of that List sort. e.g, for List{Exp, ","}, returns Exp.
     *
     * returns null if not a List sort
     *
     * we suppress cast warnings because we know that the sort must be UserList
     */
    @SuppressWarnings("cast")
    public Sort getListElementSort(Sort sort) {
        if (!isListSort(sort))
            return null;
        return ((UserList) listProductions.get(sort).getItems().get(0)).getSort();
    }

    /**
     * Finds the LUB (Least Upper Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the LUB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public Sort getLUBSort(Set<Sort> sorts) {
        return subsorts.getLUB(sorts);
    }

    /**
     * Finds the LUB (Least Upper Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the LUB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public Sort getLUBSort(Sort... sorts) {
        return subsorts.getLUB(Sets.newHashSet(sorts));
    }

    /**
     * Finds the GLB (Greatest Lower Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the GLB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public Sort getGLBSort(Set<Sort> sorts) {
        return subsorts.getGLB(sorts);
    }

    /**
     * Finds the GLB (Greatest Lower Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the GLB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public Sort getGLBSort(Sort... sorts) {
        return subsorts.getGLB(Sets.newHashSet(sorts));
    }

    /**
     * Checks if there is any well-defined common subsort of a given set of
     * sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return {@code true} if there is at least one well-defined common
     *         subsort; otherwise, {@code false}
     */
    public boolean hasCommonSubsort(Sort... sorts) {
        Set<Sort> maximalLowerBounds = subsorts.getMaximalLowerBounds(Sets.newHashSet(sorts));

        if (maximalLowerBounds.isEmpty()) {
            return false;
        } else if (maximalLowerBounds.size() == 1) {
            Sort sort = maximalLowerBounds.iterator().next();
            /* checks if the only common subsort is undefined */
            if (sort.equals(Sort.BUILTIN_BOT)
                    || isListSort(sort)
                    && getListElementSort(sort).equals(Sort.BUILTIN_BOT)) {
                return false;
            }
        }

        return true;
    }

    public void addPriority(String bigPriority, String smallPriority) {
        // add the new priority
        priorities.addRelation(bigPriority, smallPriority);
    }

    public void finalizePriority() {
        priorities.transitiveClosure();
    }

    public void addLeftAssoc(String label1, String label2) {
        assocLeft.addRelation(label1, label2);
    }

    public void addRightAssoc(String label1, String label2) {
        assocRight.addRelation(label1, label2);
    }

    public boolean isLeftAssoc(String label1, String label2) {
        return assocLeft.isInRelation(label1, label2);
    }

    public boolean isRightAssoc(String label1, String label2) {
        return assocRight.isInRelation(label1, label2);
    }

    /**
     * Check to see if the two klabels are in the wrong order according to the priority filter.
     *
     * @param klabelParent
     * @param klabelChild
     * @return
     */
    public boolean isPriorityWrong(String klabelParent, String klabelChild) {
        return priorities.isInRelation(klabelParent, klabelChild);
    }

    public void addFileRequirement(String required, String local) {
        // add the new subsorting
        if (required.equals(local))
            return;

        fileRequirements.addRelation(required, local);
    }

    public void finalizeRequirements() {
        fileRequirements.transitiveClosure();
    }

    public void addModuleImport(String mainModule, String importedModule) {
        // add the new subsorting
        if (mainModule.equals(importedModule))
            return;

        modules.addRelation(mainModule, importedModule);
    }

    public void finalizeModules() {
        modules.transitiveClosure();
    }

    public boolean isModuleIncluded(String localModule, String importedModule) {
        return modules.isInRelation(localModule, importedModule);
    }

    public boolean isModuleIncludedEq(String localModule, String importedModule) {
        if (localModule.equals(importedModule))
            return true;
        return modules.isInRelation(localModule, importedModule);
    }

    public boolean isRequiredEq(String required, String local) {
        try {
            required = new File(required).getCanonicalPath();
            local = new File(local).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (required.equals(local))
            return true;
        return fileRequirements.isInRelation(required, local);
    }

    public void addSubsort(Sort bigSort, Sort smallSort) {
        subsorts.addRelation(bigSort, smallSort);
    }

    /**
     * Computes the transitive closure of the subsort relation to make it
     * becomes a partial order set.
     */
    public void computeSubsortTransitiveClosure() {
        List<Sort> circuit = subsorts.checkForCycles();
        if (circuit != null) {
            String msg = "Circularity detected in subsorts: ";
            for (Sort sort : circuit)
                msg += sort + " < ";
            msg += circuit.get(0);
            GlobalSettings.kem.registerCriticalError(msg);
        }
        subsorts.transitiveClosure();
        // detect if lists are subsorted (Vals Ids < Exps)
        for (Production prod1 : listProductions.values()) {
            for (Production prod2 : listProductions.values()) {
                Sort sort1 = ((UserList) prod1.getItems().get(0)).getSort();
                Sort sort2 = ((UserList) prod2.getItems().get(0)).getSort();
                if (isSubsorted(sort1, sort2)) {
                    subsorts.addRelation(prod1.getSort(), prod2.getSort());
                }
            }
        }
        subsorts.transitiveClosure();
    }

    /**
     * Check to see if smallSort is subsorted to bigSort (strict)
     *
     * @param bigSort
     * @param smallSort
     * @return
     */
    public boolean isSubsorted(Sort bigSort, Sort smallSort) {
        return subsorts.isInRelation(bigSort, smallSort);
    }

    /**
     * Check to see if smallSort is subsorted or equal to bigSort
     *
     * @param bigSort
     * @param smallSort
     * @return
     */
    public boolean isSubsortedEq(Sort bigSort, Sort smallSort) {
        if (bigSort.equals(smallSort))
            return true;
        return subsorts.isInRelation(bigSort, smallSort);
    }

    public boolean isTagGenerated(String key) {
        return generatedTags.contains(key);
    }

    public boolean isSpecialTerminal(String terminal) {
        return specialTerminals.contains(terminal);
    }

    public boolean isParsingTag(Key<?> key) {
        return parsingTags.contains(key);
    }

    public static final int HASH_PRIME = 37;

    /**
     * Returns a {@link Set} of productions associated with the specified KLabel
     *
     * @param label
     *            string representation of the KLabel
     * @return list of productions associated with the label
     */
    public Set<Production> productionsOf(String label) {
        return klabels.get(label);
    }

    public Term kWrapper(Term t) {
        if (isSubsortedEq(Sort.K, t.getSort()))
            return t;
        return KApp.of(new KInjectedLabel(t));
    }

    public Map<Sort, DataStructureSort> getDataStructureSorts() {
        return Collections.unmodifiableMap(dataStructureSorts);
    }

    public void setDataStructureSorts(Map<Sort, DataStructureSort> dataStructureSorts) {
        assert !initialized;

        this.dataStructureSorts = new HashMap<Sort, DataStructureSort>(dataStructureSorts);
    }

    public DataStructureSort dataStructureSortOf(Sort sort) {
        assert initialized : "Context is not initialized yet";

        return dataStructureSorts.get(sort);
    }

    public DataStructureSort dataStructureListSortOf(Sort sort) {
        assert initialized : "Context is not initialized yet";
        DataStructureSort dataStructSort = dataStructureSorts.get(sort);
        if (dataStructSort == null) return null;
        if (!dataStructSort.type().equals(Sort.LIST)) return null;
        return dataStructSort;
    }

    /**
     * Returns the set of sorts that have lexical productions.
     */
    public Set<Sort> getTokenSorts() {
        return Collections.unmodifiableSet(tokenSorts);
    }

    public void setTokenSorts(Set<Sort> tokenSorts) {
        assert !initialized;

        this.tokenSorts = new HashSet<>(tokenSorts);
    }

    public void makeFreshFunctionNamesMap(Set<Production> freshProductions) {
        for (Production production : freshProductions) {
            if (!production.containsAttribute(Attribute.FUNCTION_KEY)) {
                GlobalSettings.kem.registerCompilerError(
                        "missing [function] attribute for fresh function " + production,
                        production);
            }

            if (freshFunctionNames.containsKey(production.getSort())) {
                GlobalSettings.kem.registerCompilerError(
                        "multiple fresh functions for sort " + production.getSort(),
                        production);
            }

            freshFunctionNames.put(production.getSort(), production.getKLabel());
        }
    }

    /**
     * @deprecated DO NOT USE outside the SDF frontend!
     */
    @Deprecated
    public BiMap<String, Production> getConses() {
        return conses;
    }

    public void computeConses() {
        assert conses == null : "can only compute conses once";
        conses = HashBiMap.create();
        for (Production p : productions) {
            // add cons to productions that don't have it already
            if (p.containsAttribute("bracket")) {
                // don't add cons to bracket production
                String cons2 = StringUtil.escapeSort(p.getSort()) + "1Bracket";
                conses.put(cons2, p);
            } else if (p.isLexical()) {
            } else if (p.isSubsort()) {
                if (p.getKLabel() != null) {
                    conses.put(StringUtil.escapeSort(p.getSort()) + "1" + StringUtil.getUniqueId() + "Syn", p);
                }
            } else {
                String cons;
                if (p.isListDecl())
                    cons = StringUtil.escapeSort(p.getSort()) + "1" + "ListSyn";
                else
                    cons = StringUtil.escapeSort(p.getSort()) + "1" + StringUtil.getUniqueId() + "Syn";
                conses.put(cons, p);
            }
        }
    }

}
