/**
 * Copyright (c) 2012-2014 Marsha Chechik, Alessio Di Sandro, Michalis Famelis,
 * Rick Salay.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Alessio Di Sandro - Implementation.
 */
package solver;

public class Z3Utils {

	public static final String SMTLIB_FILE_EXTENSION = "smt2";
	public static final String SMTLIB_PREDICATE_START = "(";
	public static final String SMTLIB_PREDICATE_END = ")";
	public static final String SMTLIB_TRUE = " true ";
	public static final String SMTLIB_FALSE = " false ";
	public static final String SMTLIB_ASSERT = SMTLIB_PREDICATE_START + "assert ";
	public static final String SMTLIB_EXISTS = SMTLIB_PREDICATE_START + "exists ";
	public static final String SMTLIB_FORALL = SMTLIB_PREDICATE_START + "forall ";
	public static final String SMTLIB_AND = SMTLIB_PREDICATE_START + "and ";
	public static final String SMTLIB_OR = SMTLIB_PREDICATE_START + "or ";
	public static final String SMTLIB_NOT = SMTLIB_PREDICATE_START + "not ";
	public static final String SMTLIB_EQUALITY = SMTLIB_PREDICATE_START + "= ";
	public static final String SMTLIB_IMPLICATION = SMTLIB_PREDICATE_START + "=> ";
	public static final String SMTLIB_CONST = SMTLIB_PREDICATE_START + "declare-const ";
	public static final String SMTLIB_TYPE_BOOL = "Bool";
	public static final String SMTLIB_TYPE_INT = "Int";

	public static final String SMTLIB_NODE = "node";
	public static final String SMTLIB_EDGE = "edge";
	public static final String SMTLIB_NODE_FUNCTION = SMTLIB_PREDICATE_START + SMTLIB_NODE + " ";
	public static final String SMTLIB_EDGE_FUNCTION = SMTLIB_PREDICATE_START + SMTLIB_EDGE + " ";

	public static final String Z3_MODEL_SEPARATOR = "!";
	public static final String Z3_MODEL_DEFINITION = " -> ";
	public static final String Z3_MODEL_ELSE = "else";
	public static final String Z3_MODEL_FUNCTION_START = "{";
	public static final String Z3_MODEL_FUNCTION_END = "}";

	public static String predicate(String predicateStart, String smtTerms) {

		return predicateStart + smtTerms + SMTLIB_PREDICATE_END;
	}

	public static String emptyPredicate(String smtTerms) {

		return predicate(SMTLIB_PREDICATE_START, smtTerms);
	}

	public static String assertion(String smtTerms) {

		return predicate(SMTLIB_ASSERT, smtTerms);
	}

	public static String not(String smtTerms) {

		return predicate(SMTLIB_NOT, smtTerms);
	}

	public static String and(String smtTerms) {

		return predicate(SMTLIB_AND, smtTerms);
	}

	public static String or(String smtTerms) {

		return predicate(SMTLIB_OR, smtTerms);
	}

	public static String constant(String smtConstantName, String smtConstantType) {

		return predicate(SMTLIB_CONST, smtConstantName + " " + smtConstantType);
	}

	public static String equality(String smtTerms) {

		return predicate(SMTLIB_EQUALITY, smtTerms);
	}

	public static String implication(String smtIfTerms, String smtThenTerms) {

		return predicate(SMTLIB_IMPLICATION, smtIfTerms + smtThenTerms);
	}

	public static String exists(String smtQuantification, String smtTerms) {

		return predicate(
			SMTLIB_EXISTS,
			SMTLIB_PREDICATE_START + smtQuantification + SMTLIB_PREDICATE_END + smtTerms
		);
	}

	public static String forall(String smtQuantification, String smtTerms) {

		return predicate(
			SMTLIB_FORALL,
			SMTLIB_PREDICATE_START + smtQuantification + SMTLIB_PREDICATE_END + smtTerms
		);
	}

}
