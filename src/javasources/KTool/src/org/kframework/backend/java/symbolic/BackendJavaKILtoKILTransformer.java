// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.Multimap;

import org.kframework.backend.java.builtins.BitVector;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.builtins.IntToken;
import org.kframework.backend.java.builtins.StringToken;
import org.kframework.backend.java.builtins.UninterpretedToken;
import org.kframework.backend.java.kil.*;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.kil.ASTNode;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.ListBuiltin;
import org.kframework.kil.MapBuiltin;
import org.kframework.kil.SetBuiltin;
import org.kframework.kil.Sort;
import org.kframework.kil.loader.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Convert a term from the Java Rewrite engine internal representation into the KIL representation.
 *
 * @author: AndreiS
 */
public class BackendJavaKILtoKILTransformer implements Transformer {

    private final Context context;
    private final ConfigurationStructureMap configurationStructureMap;
    private org.kframework.kil.Cell currentCell;

    public BackendJavaKILtoKILTransformer(Context context) {
        this.context = context;
        configurationStructureMap = context.getConfigurationStructureMap();
    }

    @Override
    public String getName() {
        return this.getClass().toString();
    }

    /**
     * Private helper method that translates Java backend specific KIL term back
     * to generic KIL term.
     *
     * @param term
     *            the term to be translated
     * @return the translated term
     */
    private ASTNode transformJavaBackendSpecificTerm(Term term) {
        ASTNode kil = new org.kframework.kil.BackendTerm(term.sort().toFrontEnd(), term.toString());
        kil.copyAttributesFrom(term);
        return kil;
    }

    @Override
    public ASTNode transform(Cell cell) {
        final CellLabel label = cell.getLabel();
        // TODO(AndreiS): fix the printing of the cells which are representing maps
        if (cell.getLabel().isMapCell()) {
            currentCell = configurationStructureMap.get(label.getRealCellLabel().name()).cell;
        } else {
            currentCell = configurationStructureMap.get(label.name()).cell;
        }

        org.kframework.kil.Cell returnCell = new org.kframework.kil.Cell();
        returnCell.setLabel(label.name());
        returnCell.setEndLabel(label.name());
        returnCell.setContents((org.kframework.kil.Term) cell.getContent().accept(this));
        returnCell.copyAttributesFrom(cell);
        return returnCell;
    }

    @Override
    public ASTNode transform(CellCollection cellCollection) {
        final Multimap<CellLabel, Cell> cellMap = cellCollection.cellMap();
        List<org.kframework.kil.Term> terms = currentCell.getCellTerms();
        List<org.kframework.kil.Term> contents = new ArrayList<org.kframework.kil.Term>();
        for (org.kframework.kil.Term term : terms) {
            if (! (term instanceof org.kframework.kil.Cell)) continue;
            String key = ((org.kframework.kil.Cell) term).getLabel();
            for (Cell<?> cell : cellMap.get(CellLabel.of(key))) {
                contents.add((org.kframework.kil.Cell) transform(cell));
            }
        }
        if (cellCollection.hasFrame()) {
            contents.add((org.kframework.kil.Term) cellCollection.frame().accept(this));
        }
        ASTNode kil = new org.kframework.kil.Bag(contents);
        kil.copyAttributesFrom(cellCollection);
        return kil;
    }

    @Override
    public ASTNode transform(Hole hole) {
        //return new org.kframework.kil.FreezerHole(0);
        return org.kframework.kil.Hole.KITEM_HOLE;
    }

    @Override
    public ASTNode transform(KItem kItem) {
        ASTNode kil = new org.kframework.kil.KApp(
                (org.kframework.kil.Term) kItem.kLabel().accept(this),
                (org.kframework.kil.Term) kItem.kList().accept(this));
        kil.copyAttributesFrom(kItem);
        return kil;
    }

    @Override
    public ASTNode transform(KItemProjection kItemProj) {
        ASTNode kil = new org.kframework.kil.KItemProjection(
                Sort.of(kItemProj.kind().toString()),
                (org.kframework.kil.Term) kItemProj.term().accept(this));
        kil.copyAttributesFrom(kItemProj);
        return kil;
    }

    @Override
    public ASTNode transform(KLabelConstant kLabelConstant) {
        ASTNode kil = org.kframework.kil.KLabelConstant.of(kLabelConstant.label(), context);
        kil.copyAttributesFrom(kLabelConstant);
        return kil;
    }

    @Override
    public ASTNode transform(KLabelFreezer kLabelFreezer) {
        ASTNode kil = new org.kframework.kil.FreezerLabel(
                (org.kframework.kil.Term) kLabelFreezer.term().accept(this));
        kil.copyAttributesFrom(kLabelFreezer);
        return kil;
    }

    @Override
    public ASTNode transform(KLabelInjection kLabelInjection) {
        ASTNode kil = new org.kframework.kil.KInjectedLabel(
                (org.kframework.kil.Term) kLabelInjection.term().accept(this));
        kil.copyAttributesFrom(kLabelInjection);
        return kil;
    }

    @Override
    public ASTNode transform(KList kList) {
        List<org.kframework.kil.Term> terms = transformTerms(kList);
        ASTNode kil = new org.kframework.kil.KList(terms);
        kil.copyAttributesFrom(kList);
        return kil;
    }

    @Override
    public ASTNode transform(KSequence kSequence) {
        List<org.kframework.kil.Term> terms = transformTerms(kSequence);
        ASTNode kil = new org.kframework.kil.KSequence(terms);
        kil.copyAttributesFrom(kSequence);
        return kil;
    }

    private List<org.kframework.kil.Term> transformTerms(KCollection kCollection) {
        List<org.kframework.kil.Term> terms = new ArrayList<org.kframework.kil.Term>();
        for (Term term : kCollection) {
            terms.add((org.kframework.kil.Term) term.accept(this));
        }
        if (kCollection.hasFrame()) {
            terms.add((org.kframework.kil.Term) kCollection.frame().accept(this));
        }
        return terms;
    }

    @Override
    public ASTNode transform(BuiltinSet set) {
        List<org.kframework.kil.Term> elements = new ArrayList<org.kframework.kil.Term>();
        List<org.kframework.kil.Term> baseTerms = new ArrayList<org.kframework.kil.Term>();
        for (Term entry : set.elements()) {
            elements.add((org.kframework.kil.Term)entry.accept(this));
        }
        Collections.sort(elements);
        for (Term term : set.baseTerms()) {
            baseTerms.add((org.kframework.kil.Term) term.accept(this));
        }
        ASTNode kil = new SetBuiltin(
                context.dataStructureSortOf(DataStructureSort.DEFAULT_SET_SORT),
                baseTerms,
                elements);
        kil.copyAttributesFrom(set);
        return kil;
    }


    @Override
    public ASTNode transform(BuiltinList builtinList) {
        List<org.kframework.kil.Term> elementsLeft = new ArrayList<org.kframework.kil.Term>();
        List<org.kframework.kil.Term> baseTerms = new ArrayList<org.kframework.kil.Term>();
        List<org.kframework.kil.Term> elementsRight = new ArrayList<org.kframework.kil.Term>();
        for (Term entry : builtinList.elementsLeft()) {
            elementsLeft.add((org.kframework.kil.Term)entry.accept(this));
        }
        for (Term term : builtinList.baseTerms()) {
            baseTerms.add((org.kframework.kil.Term) term.accept(this));
        }
        for (Term entry : builtinList.elementsRight()) {
            elementsRight.add((org.kframework.kil.Term)entry.accept(this));
        }
        ASTNode kil = ListBuiltin.of(context.dataStructureSortOf(DataStructureSort.DEFAULT_LIST_SORT),
                baseTerms, elementsLeft, elementsRight);
        kil.copyAttributesFrom(builtinList);
        return kil;
    }

    @Override
    public ASTNode transform(BuiltinMap map) {
        final Map<Term, Term> entries = map.getEntries();
        List<Term> keys = new ArrayList<Term>(entries.keySet());
        Collections.sort(keys);
        Map<org.kframework.kil.Term, org.kframework.kil.Term> elements = new HashMap<>();
        List<org.kframework.kil.Term> baseTerms = new ArrayList<>();
        for (Term key : keys) {
            Term value = entries.get(key);
            elements.put(
                    (org.kframework.kil.Term) key.accept(this),
                    (org.kframework.kil.Term) value.accept(this));
        }
        for (Term term : map.baseTerms()) {
            baseTerms.add((org.kframework.kil.Term) term.accept(this));
        }
        ASTNode kil = new MapBuiltin(
                context.dataStructureSortOf(DataStructureSort.DEFAULT_MAP_SORT),
                baseTerms,
                elements);
        kil.copyAttributesFrom(map);
        return kil;
    }

    @Override
    public ASTNode transform(Token token) {
        ASTNode kil = org.kframework.kil.Token.kAppOf(token.sort().toFrontEnd(), token.value());
        kil.copyAttributesFrom(token);
        return kil;
    }

    @Override
    public ASTNode transform(Variable variable) {
//        System.out.println("VARIABLE*************"+ variable.name()+"->"+variable.sort());
        ASTNode node = new org.kframework.kil.Variable(variable.name(), variable.sort().toFrontEnd());
//        System.out.println("NODE: "+node.toString());
//        System.out.println("**********VARIABLE"+ variable.name()+"->"+variable.sort());
        node.copyAttributesFrom(variable);
        return node;
    }

    @Override
    public ASTNode transform(BuiltinMgu mgu) {
        // TODO(YilongL): properly translate the Mgu into KItem form using the toK function
        return transformJavaBackendSpecificTerm(mgu);
    }

    @Override
    public ASTNode transform(BitVector bitVector) {
        return transform((Token) bitVector);
    }

    @Override
    public ASTNode transform(BoolToken boolToken) {
        return transform((Token) boolToken);
    }

    @Override
    public ASTNode transform(Collection collection) {
        throw new UnsupportedOperationException("This method should never be called");
    }

    @Override
    public ASTNode transform(ConstrainedTerm constrainedTerm) {
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    @Override
    public ASTNode transform(IntToken intToken) {
        return transform((Token) intToken);
    }

    @Override
    public ASTNode transform(KCollection kCollection) {
        throw new UnsupportedOperationException("This method should never be called");
    }

    @Override
    public ASTNode transform(KLabel kLabel) {
        throw new UnsupportedOperationException("This method should never be called");
    }

    @Override
    public ASTNode transform(ListLookup listLookup) {
        return transformJavaBackendSpecificTerm(listLookup);
    }

    @Override
    public ASTNode transform(ListUpdate listUpdate) {
        return transformJavaBackendSpecificTerm(listUpdate);
    }

    @Override
    public ASTNode transform(MapKeyChoice mapKeyChoice) {
        return transformJavaBackendSpecificTerm(mapKeyChoice);
    }

    @Override
    public ASTNode transform(MapLookup mapLookup) {
        return transformJavaBackendSpecificTerm(mapLookup);
    }

    @Override
    public ASTNode transform(MapUpdate mapUpdate) {
        return transformJavaBackendSpecificTerm(mapUpdate);
    }

    @Override
    public ASTNode transform(MetaVariable metaVariable) {
        return transform((Token) metaVariable);
    }

    @Override
    public ASTNode transform(Rule rule) {
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    @Override
    public ASTNode transform(SetElementChoice setElementChoice) {
        return transformJavaBackendSpecificTerm(setElementChoice);
    }

    @Override
    public ASTNode transform(SetLookup setLookup) {
        return transformJavaBackendSpecificTerm(setLookup);
    }

    @Override
    public ASTNode transform(SetUpdate setUpdate) {
        return transformJavaBackendSpecificTerm(setUpdate);
    }

    @Override
    public ASTNode transform(SymbolicConstraint symbolicConstraint) {
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    @Override
    public ASTNode transform(StringToken stringToken) {
        return transform((Token) stringToken);
    }

    @Override
    public ASTNode transform(Term node) {
        throw new UnsupportedOperationException("This method should never be called");
    }

    @Override
    public ASTNode transform(UninterpretedConstraint uninterpretedConstraint) {
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    @Override
    public ASTNode transform(UninterpretedToken uninterpretedToken) {
        return transform((Token) uninterpretedToken);
    }

}
