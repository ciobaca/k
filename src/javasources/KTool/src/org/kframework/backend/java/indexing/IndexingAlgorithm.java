// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.backend.java.indexing;

import org.kframework.backend.java.indexing.pathIndex.PathIndex;

public enum IndexingAlgorithm {
    /**
     * Represents an index backed by {@link IndexingTable}
     */
    RULE_TABLE(IndexingTable.class),

    /**
     * Represents an index backed by {@link PathIndex}
     * @deprecated as of 04/16/2014 and will be replaced with a more general, faster algorithm in
     *              the future
     */
    @Deprecated
    PATH(PathIndex.class);

    IndexingAlgorithm(Class<? extends RuleIndex> clazz) {
        this.clazz = clazz;
    }

    public final Class<? extends RuleIndex> clazz;
}
