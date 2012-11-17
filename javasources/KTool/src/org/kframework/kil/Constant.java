package org.kframework.kil;

import org.kframework.kil.loader.Constants;
import org.kframework.kil.visitors.Transformer;
import org.kframework.kil.visitors.Visitor;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.w3c.dom.Element;

public class Constant extends Term {
	String value;

	public Constant(String sort, String value) {
		super(sort);
		if (value.equals("'_``,_"))
			System.out.println(value);
		this.value = value;
	}

	public Constant(String location, String filename, String sort, String value) {
		super(location, filename, sort);
		this.value = value;
	}

	public Constant(Element element) {
		super(element);
		this.sort = element.getAttribute(Constants.SORT_sort_ATTR);
		this.value = element.getAttribute(Constants.VALUE_value_ATTR);
	}

	public Constant(Constant constant) {
		super(constant);
		this.value = constant.value;
	}

	public String toString() {
		return value + " ";
	}

	public String getSort() {
		return sort;
	}

	public String getValue() {
		return value;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public void setValue(String value) {
		this.value = value;
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
	public Constant shallowCopy() {
		return new Constant(this);
	}
}