// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.KApp;
import org.kframework.kil.ListTerminator;
import org.kframework.kil.Production;
import org.kframework.kil.ProductionItem;
import org.kframework.kil.NonTerminal;
import org.kframework.kil.Sort;
import org.kframework.kil.Term;
import org.kframework.kil.TermCons;
import org.kframework.kil.Token;
import org.kframework.kil.UserList;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Transformer class adding the implicit terminator (.List{"<klabel>"}) to user defined lists.
 */
public class AddEmptyLists extends CopyOnWriteTransformer {

    public AddEmptyLists(Context context) {
        super("Add empty lists", context);
    }

    @Override
    public ASTNode visit(TermCons tc, Void _) {
        // traverse
        Production p = tc.getProduction();

        if (p.isListDecl()) {
            Term t = tc.getContents().get(0);
            UserList ul = (UserList) p.getItems().get(0);
            if (isAddEmptyList(ul.getSort(), t.getSort())) {
                if (!isUserListElement(ul.getSort(), t, context)) {
                    String msg = "Found sort '" + t.getSort() + "' where list sort '" + ul.getSort() + "' was expected. Moving on.";
                    GlobalSettings.kem.register(new KException(ExceptionType.HIDDENWARNING, KExceptionGroup.LISTS, msg, t.getSource(), t.getLocation()));
                } else
                    tc.getContents().set(0, addEmpty(t, ul.getSort()));
            }

            // if the term should be a list, append the empty element
            t = tc.getContents().get(1);
            if (isAddEmptyList(p.getSort(), t.getSort())) {
                if (!isUserListElement(p.getSort(), t, context)) {
                    String msg = "Found sort '" + t.getSort() + "' where list sort '" + p.getSort() + "' was expected. Moving on.";
                    GlobalSettings.kem.register(new KException(ExceptionType.HIDDENWARNING, KExceptionGroup.LISTS, msg, t.getSource(), t.getLocation()));
                } else
                    tc.getContents().set(1, addEmpty(t, tc.getProduction().getSort()));
            }
        } else {
            for (int i = 0, j = 0; j < p.getItems().size(); j++) {
                ProductionItem pi = p.getItems().get(j);
                if (!(pi instanceof NonTerminal))
                    continue;

                Sort sort = ((NonTerminal) pi).getSort();
                if (context.isListSort(sort)) {
                    Term t = tc.getContents().get(i);
                    // if the term should be a list, append the empty element
                    if (isAddEmptyList(sort, t.getSort())) {
                        if (!isUserListElement(sort, t, context)) {
                            String msg = "Found sort '" + t.getSort() + "' where list sort '" + sort + "' was expected. Moving on.";
                            GlobalSettings.kem.register(new KException(ExceptionType.HIDDENWARNING, KExceptionGroup.LISTS, msg, t.getSource(), t.getLocation()));
                        } else
                            tc.getContents().set(i, addEmpty(t, sort));
                    }
                }
                i++;
            }
        }

        return super.visit(tc, _);
    }

    private boolean isUserListElement(Sort listSort, Term element, Context context) {
        Sort elementSort = element.getSort();

        /* TODO: properly infer sort of KApp */
        if (elementSort.equals(Sort.KITEM) && element instanceof KApp) {
            /* infer sort for builtins and tokens */
            if (((KApp) element).getLabel() instanceof Token) {
                elementSort = ((Token) ((KApp) element).getLabel()).tokenSort();
            }
        }

        return !elementSort.equals(Sort.KITEM)
               && context.isSubsortedEq(listSort, elementSort);
    }

    public boolean isAddEmptyList(Sort expectedSort, Sort termSort) {
        if (!context.isListSort(expectedSort))
            return false;
        if (context.isSubsortedEq(expectedSort, termSort)
                && context.isListSort(termSort))
            return false;
        return true;
    }

    private Term addEmpty(Term node, Sort sort) {
        TermCons tc = new TermCons(sort, getListProduction(sort));
        List<Term> genContents = new ArrayList<Term>();
        genContents.add(node);
        genContents.add(new ListTerminator(sort, null));

        tc.setContents(genContents);
        return tc;
    }

    private Production getListProduction(Sort psort) {
        return context.listProductions.get(psort);
    }
}
