// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module INORDER-SPEC
  imports KERNELC

rule
  <struct>... STRUCT:Map ...</struct>
  <fun>... FUN:Map ...</fun>
  <k>
    inorder(tv(struct treeNode *, TP:Pointer))
  =>
    tv(void, undef)
  ...</k>
  <heap>...
    tree(TP)(T:Tree)
  ...</heap>
  <out>... .List => intseq2list(tree2inorder(T)) </out>

endmodule
