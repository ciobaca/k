package org.kframework.backend.pdmc.pda;

import org.kframework.backend.pdmc.automaton.BasicAutomaton;
import org.kframework.backend.pdmc.automaton.StateLetterPair;
import org.kframework.backend.pdmc.automaton.Transition;
import org.kframework.backend.pdmc.pda.pautomaton.PAutomaton;
import org.kframework.backend.pdmc.pda.pautomaton.PAutomatonState;
import org.kframework.backend.pdmc.pda.pautomaton.util.IndexedTransitions;

import java.util.*;

/**
 * Class wrapping (and computing) the <b>post</b> automaton corresponding to a BPDS.
 * <ul>
 *    <li>
 *        The states of this automaton include all states of the BPDS reachable from the initial configuration;
 *    </li>
 *    <li>
 *        Any path q -w->* f from a BPDS state to a final state encodes the reachable configuration (q,w).
 *    </li>
 * </ul>
 *
 * Implements some pushdown model-checking related algorithms from  Stefan Schwoon's Phd Thesis:
 * S. Schwoon.  Model-Checking Pushdown Systems.  Ph.D. Thesis, Technische Universitaet Muenchen, June 2002.
 *
 * @author TraianSF
 */
public class PostStar<Control, Alphabet> extends BasicAutomaton<PAutomatonState<Control, Alphabet>, Alphabet>  {

    final PushdownSystemInterface<Control, Alphabet> bps;
    private final TrackingLabelFactory<Control, Alphabet> labelFactory;
    private BasicAutomaton<PAutomatonState<Control, Alphabet>, Alphabet> automaton = null;



    public PostStar(PushdownSystemInterface<Control, Alphabet> bps,
                    TrackingLabelFactory<Control, Alphabet> labelFactory) {
        this.labelFactory = labelFactory;
        this.bps = bps;
        compute();
    }

    /**
     * Main method of the class. Implements the post* algorithm
     * The post* algorithm implemented is presented in Figure 3.4, Section 3.1.4 of S. Schwoon's PhD thesis (p. 48)
     * The modification to compute the repeated heads graph is explained in Section 3.2.3 of Schwoon's thesis
     * (see also Algorithm 4 in Figure 3.9, p. 81)
     */
    private void compute() {
        if (automaton != null) return;
        Set<Transition<PAutomatonState<Control, Alphabet>, Alphabet>> trans = new HashSet<>();
        IndexedTransitions<PAutomatonState<Control, Alphabet>, Alphabet> rel = new IndexedTransitions<>();

        Configuration<Control, Alphabet> initial = bps.initialConfiguration();
        ConfigurationHead<Control, Alphabet> initialHead = initial.getHead();
        PAutomatonState<Control, Alphabet> initialState =
                PAutomatonState.of(initialHead.getState());
        Stack<Alphabet> initialStack = initial.getFullStack();
        assert !initialStack.empty() : "We must have something to process";
        PAutomatonState<Control, Alphabet> finalState = null;
        for (Alphabet letter : initialStack) {
            finalState = new PAutomatonState<>();
            Transition<PAutomatonState<Control, Alphabet>, Alphabet> transition1 =
                    Transition.of(initialState, letter, finalState);
            TrackingLabel<Control, Alphabet> initialLabel = labelFactory.newLabel();
            labelFactory.updateLabel(transition1, initialLabel);
            if (initialState.isControlState()) {
                trans.add(transition1);
            } else {
                rel.add(transition1);
            }
            initialState = finalState;
        }

        while (!trans.isEmpty()) {
            Iterator<Transition<PAutomatonState<Control, Alphabet>, Alphabet>> iterator
                    = trans.iterator();
            Transition<PAutomatonState<Control, Alphabet>, Alphabet> transition = iterator.next();
            iterator.remove();
            Alphabet gamma = transition.getLetter();
            assert labelFactory.get(transition) != null : "Each transition must have a label";
            if (!rel.contains(transition)) {
                rel.add(transition);
                PAutomatonState<Control, Alphabet> tp = transition.getStart();
                PAutomatonState<Control, Alphabet> q = transition.getEnd();
                if (gamma != null) {
                    assert tp.isControlState() : "Expecting PDS state on the lhs of " + transition;
                    Control p = tp.getState();
                    final ConfigurationHead<Control, Alphabet> configurationHead
                            = ConfigurationHead.of(p, gamma);
                    Set<Rule<Control, Alphabet>> rules =
                            bps.getRules(configurationHead);
                    for (Rule<Control, Alphabet> rule : rules) {
                        Control pPrime = rule.endState();
                        Stack<Alphabet> stack = rule.endStack();
                        assert stack.size() <= 2 : "At most 2 elements are allowed in the stack for now";
                        Alphabet gamma1, gamma2;
                        TrackingLabel<Control, Alphabet> headLetterLabel;
                        headLetterLabel = new TrackingLabel();
                        headLetterLabel.update(labelFactory.get(transition));
                        headLetterLabel.update(bps, pPrime);
                        headLetterLabel.setRule(rule);
                        Transition<PAutomatonState<Control, Alphabet>, Alphabet> newHeadTransition;
                        switch (stack.size()) {
                            case 0:
                                newHeadTransition = Transition.of(PAutomatonState.<Control, Alphabet>of(pPrime),
                                        null, q);
                                trans.add(newHeadTransition);
                                labelFactory.updateLabel(newHeadTransition, headLetterLabel);
                                break;
                            case 1:
                                gamma1 = stack.peek();
                                newHeadTransition = Transition.of(
                                        PAutomatonState.<Control, Alphabet>of(pPrime),
                                        gamma1, q);
                                trans.add(newHeadTransition);
                                labelFactory.updateLabel(newHeadTransition, headLetterLabel);
                                break;
                            case 2:
                                gamma1 = stack.get(1);
                                gamma2 = stack.get(0);
                                PAutomatonState<Control, Alphabet> qPPrimeGamma1
                                        = PAutomatonState.of(pPrime, gamma1);
                                newHeadTransition = Transition.of(
                                        PAutomatonState.<Control, Alphabet>of(pPrime),
                                        gamma1, qPPrimeGamma1);
                                trans.add(newHeadTransition);
                                labelFactory.updateLabel(newHeadTransition, headLetterLabel);
                                TrackingLabel<Control, Alphabet> secondLetterabel = new TrackingLabel();
                                secondLetterabel.setRule(rule);
                                Transition<PAutomatonState<Control, Alphabet>, Alphabet> secondLetterTransition = Transition.of(qPPrimeGamma1, gamma2, q);
                                rel.add(secondLetterTransition);
                                labelFactory.updateLabel(secondLetterTransition, secondLetterabel);
                                for (Transition<PAutomatonState<Control, Alphabet>, Alphabet> t
                                        : rel.getBackEpsilonTransitions(qPPrimeGamma1)) {
                                    TrackingLabel<Control, Alphabet> tLabel = labelFactory.get(t);
                                    TrackingLabel<Control, Alphabet> backEpsilonLabel = new TrackingLabel();
                                    backEpsilonLabel.update(tLabel);
                                    backEpsilonLabel.setRule(rule);
                                    backEpsilonLabel.setBackState(qPPrimeGamma1);
                                    Transition<PAutomatonState<Control, Alphabet>, Alphabet> newBackEpsilonTransition = Transition.of(t.getStart(), gamma2, q);
                                    trans.add(newBackEpsilonTransition);
                                    labelFactory.updateLabel(newBackEpsilonTransition, backEpsilonLabel);
                                }
                        }
                    }
                } else { // gamma == null --- epsilon transitions p - eps -> q   t :  q - gamma -> q' => p - gamma -> q'
                    for (Transition<PAutomatonState<Control, Alphabet>, Alphabet> t
                            : rel.getFrontTransitions(q)) {
                        if (t.getLetter() == null) continue;
//                        if (t.getEnd().getLetter() != null) continue;
                        TrackingLabel<Control, Alphabet> tLetter = labelFactory.get(t);
                        TrackingLabel<Control, Alphabet> epsilonClosureLabel = new TrackingLabel();
                        epsilonClosureLabel.update(tLetter);
                        epsilonClosureLabel.update(labelFactory.get(transition));
                        epsilonClosureLabel.setBackState(q);
                        epsilonClosureLabel.setRule(tLetter.getRule());
                        Transition<PAutomatonState<Control, Alphabet>, Alphabet> epsilonClosureTransition = Transition.of(tp, t.getLetter(), t.getEnd());
                        trans.add(epsilonClosureTransition);
                        labelFactory.updateLabel(epsilonClosureTransition, epsilonClosureLabel);
                    }
                }
            }
        }

        automaton = new PAutomaton<>(
                rel.getTransitions(),
                initialState,
                Collections.singleton(finalState));

    }

    private Collection<ConfigurationHead<Control, Alphabet>> getReachableHeads() {
        Collection<ConfigurationHead<Control, Alphabet>> heads = new ArrayList<>();
        for (StateLetterPair<PAutomatonState<Control, Alphabet>, Alphabet> index : automaton.getTransitionHeads()) {
            PAutomatonState<Control, Alphabet> pState = index.getState();
            if (pState.isControlState()) {
                heads.add(ConfigurationHead.of(pState.getState(), index.getLetter()));
            }
        }
        return heads;

    }

   /**
     * Implements Witness generation algorithm from Schwoon's thesis, Section 3.1.6
     * @param head A reachable configuration head
     * @return The path (of rules) from the initial configuraiton to {@code head}
     */
    public Deque<Rule<Control, Alphabet>> getReachableConfiguration(
           ConfigurationHead<Control, Alphabet> head
    ) {
        Deque<Rule<Control, Alphabet>> result = new ArrayDeque<>();
        //Step 1
        Deque<Transition<PAutomatonState<Control, Alphabet>, Alphabet>> path = automaton.getPath(
                PAutomatonState.<Control, Alphabet>of(head.getState()),
                head.getLetter(),
                automaton.getFinalStates().iterator().next());
        //Step 2
        Transition<PAutomatonState<Control, Alphabet>, Alphabet> transition = path.removeFirst();
        TrackingLabel<Control, Alphabet> label = labelFactory.get(transition);
        Rule<Control, Alphabet> rule = label.getRule();
        PAutomatonState<Control, Alphabet> backState;
        while (rule != null) {
            if (rule.endStack().size() == 2) { // reduce step 3.2 to step 3.1 by shifting transition & rules
                transition = path.removeFirst();
                label = labelFactory.get(transition);
                rule = label.getRule();
                assert rule != null && rule.endStack().size() == 2;
            }
            // Step 3.1
            result.addFirst(rule);
            head = rule.getHead();
            backState = label.getBackState();
            if (backState == null) {
                transition = Transition.of(
                        PAutomatonState.<Control, Alphabet>of(head.getState()),
                        head.getLetter(),
                        transition.getEnd()
                );
                assert labelFactory.get(transition) != null : "Each transition must have a label";
            } else {
                transition = Transition.of(
                        backState,
                        head.getLetter(),
                        transition.getEnd()
                );
                assert labelFactory.get(transition) != null : "Each transition must have a label";
                path.addFirst(transition);
                transition = Transition.of(
                        PAutomatonState.<Control, Alphabet>of(head.getState()),
                        null,
                        backState
                );
                assert labelFactory.get(transition) != null : "Each transition must have a label";
            }

            label = labelFactory.get(transition);
            rule = label.getRule();
        }
        return result;
    }

    @Override
    public Set<Transition<PAutomatonState<Control, Alphabet>, Alphabet>> getTransitions(PAutomatonState<Control, Alphabet> state, Alphabet letter) {
        return automaton.getTransitions(state, letter);
    }

    @Override
    public Set<Transition<PAutomatonState<Control, Alphabet>, Alphabet>> getBackTransitions(PAutomatonState<Control, Alphabet> state, Alphabet letter) {
        return automaton.getBackTransitions(state, letter);
    }

    @Override
    public PAutomatonState<Control, Alphabet> initialState() {
        return automaton.initialState();
    }

    @Override
    public Set<PAutomatonState<Control, Alphabet>> getFinalStates() {
        return automaton.getFinalStates();
    }

    @Override
    public Collection<Alphabet> getLetters() {
        return automaton.getLetters();
    }

    @Override
    public Collection<? extends StateLetterPair<PAutomatonState<Control, Alphabet>, Alphabet>> getTransitionHeads() {
        return automaton.getTransitionHeads();
    }

    @Override
    public String toString() {
        return  automaton.toString();
    }
}