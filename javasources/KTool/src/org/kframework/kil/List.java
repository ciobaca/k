package org.kframework.kil;

import org.kframework.kil.visitors.Transformer;
import org.kframework.kil.visitors.Visitor;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.w3c.dom.Element;


public class List extends Collection {

	public List(Element element) {
		super(element);
	}

	public List(List node) {
		super(node);
	}

	public List(String location, String filename) {
		super(location, filename, "List");
	}

	public List() {
		super("List");
	}

	public List(java.util.List<Term> col) {
		super("List", col);
	}

	@Override
	public String toString() {
		return super.toString();
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public ASTNode accept(Transformer visitor) throws TransformerException {
		return visitor.transform(this);
	}

	@Override
	public List shallowCopy() {
		return new List(this);
	}
}