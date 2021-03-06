/*******************************************************************************
 *
 *	Copyright (c) 2008 Fujitsu Services Ltd.
 *
 *	Author: Nick Battle
 *
 *	This file is part of VDMJ.
 *
 *	VDMJ is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	VDMJ is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with VDMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package org.overturetool.vdmj.definitions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Vector;

import org.overturetool.vdmj.Settings;
import org.overturetool.vdmj.expressions.Expression;
import org.overturetool.vdmj.expressions.NotYetSpecifiedExpression;
import org.overturetool.vdmj.expressions.SubclassResponsibilityExpression;
import org.overturetool.vdmj.lex.Dialect;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.lex.Token;
import org.overturetool.vdmj.patterns.IdentifierPattern;
import org.overturetool.vdmj.patterns.Pattern;
import org.overturetool.vdmj.patterns.PatternList;
import org.overturetool.vdmj.pog.ParameterPatternObligation;
import org.overturetool.vdmj.pog.POFunctionDefinitionContext;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.POFunctionResultContext;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.pog.FuncPostConditionObligation;
import org.overturetool.vdmj.pog.SubTypeObligation;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.FlatCheckedEnvironment;
import org.overturetool.vdmj.typechecker.FlatEnvironment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.typechecker.Pass;
import org.overturetool.vdmj.typechecker.TypeComparator;
import org.overturetool.vdmj.types.BooleanType;
import org.overturetool.vdmj.types.FunctionType;
import org.overturetool.vdmj.types.NaturalType;
import org.overturetool.vdmj.types.ParameterType;
import org.overturetool.vdmj.types.ProductType;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.types.UnknownType;
import org.overturetool.vdmj.util.Utils;
import org.overturetool.vdmj.values.FunctionValue;
import org.overturetool.vdmj.values.NameValuePair;
import org.overturetool.vdmj.values.NameValuePairList;


/**
 * A class to hold an explicit function definition.
 */

public class ExplicitFunctionDefinition extends Definition
{
	private static final long serialVersionUID = 1L;
	public final LexNameList typeParams;
	public FunctionType type;
	public final List<PatternList> paramPatternList;
	public final Expression precondition;
	public final Expression postcondition;
	public final Expression body;
	public final boolean isTypeInvariant;
	public final LexNameToken measure;
	public final boolean isCurried;

	public ExplicitFunctionDefinition predef;
	public ExplicitFunctionDefinition postdef;
	public List<DefinitionList> paramDefinitionList;

	private Type expectedResult = null;
	private Type actualResult = null;
	public boolean isUndefined = false;
	public boolean recursive = false;
	public int measureLexical = 0;
	public Definition measuredef;
	private Map<TypeList, FunctionValue> polyfuncs = null;

	public ExplicitFunctionDefinition(LexNameToken name, NameScope scope,
		LexNameList typeParams, FunctionType type,
		List<PatternList> parameters,
		Expression body, Expression precondition, Expression postcondition,
		boolean typeInvariant, LexNameToken measure)
	{
		super(Pass.DEFS, name.location, name, scope);

		this.typeParams = typeParams;
		this.type = type;
		this.paramPatternList = parameters;
		this.precondition = precondition;
		this.postcondition = postcondition;
		this.body = body;
		this.isTypeInvariant = typeInvariant;
		this.measure = measure;
		this.isCurried = parameters.size() > 1;

		type.definitions = new DefinitionList(this);
		type.instantiated = typeParams == null ? null : false;
	}

	@Override
	public String toString()
	{
		StringBuilder params = new StringBuilder();

		for (PatternList plist: paramPatternList)
		{
			params.append("(" + Utils.listToString(plist) + ")");
		}

		return accessSpecifier.ifSet(" ") + name.name +
				(typeParams == null ? ": " : "[" + typeParams + "]: ") + type +
				"\n\t" + name.name + params + " ==\n" + body +
				(precondition == null ? "" : "\n\tpre " + precondition) +
				(postcondition == null ? "" : "\n\tpost " + postcondition);
	}

	@Override
	public void implicitDefinitions(Environment base)
	{
		if (precondition != null)
		{
			predef = getPreDefinition();
			predef.markUsed();
		}
		else
		{
			predef = null;
		}

		if (postcondition != null)
		{
			postdef = getPostDefinition();
			postdef.markUsed();
		}
		else
		{
			postdef = null;
		}
	}

	@Override
	public void setClassDefinition(ClassDefinition def)
	{
		super.setClassDefinition(def);

		if (predef != null)
		{
			predef.setClassDefinition(def);
		}

		if (postdef != null)
		{
			postdef.setClassDefinition(def);
		}
	}

	public DefinitionList getTypeParamDefinitions()
	{
		DefinitionList defs = new DefinitionList();

		for (LexNameToken pname: typeParams)
		{
			Definition p = new LocalDefinition(
				pname.location, pname, NameScope.NAMES, new ParameterType(pname));

			p.markUsed();
			defs.add(p);
		}

		return defs;
	}

	@Override
	public void typeResolve(Environment base)
	{
		if (typeParams != null)
		{
			FlatCheckedEnvironment params =	new FlatCheckedEnvironment(
				getTypeParamDefinitions(), base, NameScope.NAMES);

			type = type.typeResolve(params, null);
		}
		else
		{
			type = type.typeResolve(base, null);
		}

		if (base.isVDMPP())
		{
			name.setTypeQualifier(type.parameters);
		}

		if (body instanceof SubclassResponsibilityExpression ||
			body instanceof NotYetSpecifiedExpression)
		{
			isUndefined = true;
		}

		if (precondition != null)
		{
			predef.typeResolve(base);
		}

		if (postcondition != null)
		{
			postdef.typeResolve(base);
		}

		for (PatternList pp: paramPatternList)
		{
			pp.typeResolve(base);
		}
	}

	@Override
	public void typeCheck(Environment base, NameScope scope)
	{
		DefinitionList defs = new DefinitionList();

		if (typeParams != null)
		{
			defs.addAll(getTypeParamDefinitions());
		}
		
		TypeComparator.checkComposeTypes(type, base, false);

		expectedResult = checkParams(paramPatternList.listIterator(), type);

		paramDefinitionList = getParamDefinitions();

		for (DefinitionList pdef: paramDefinitionList)
		{
			defs.addAll(pdef);	// All definitions of all parameter lists
		}

		FlatEnvironment local = new FlatCheckedEnvironment(defs, base, scope);
		FlatCheckedEnvironment checked = (FlatCheckedEnvironment)local;
		checked.setStatic(accessSpecifier);
		checked.setEnclosingDefinition(this);
		checked.setFunctional(true);

		defs.typeCheck(local, scope);

		if (base.isVDMPP() && !accessSpecifier.isStatic)
		{
			local.add(getSelfDefinition());
		}

		if (predef != null)
		{
			BooleanType expected = new BooleanType(location);
			Type b = predef.body.typeCheck(local, null, NameScope.NAMES, expected);

			if (!b.isType(BooleanType.class, location))
			{
				report(3018, "Precondition returns unexpected type");
				detail2("Actual", b, "Expected", expected);
			}

			DefinitionList qualified = predef.body.getQualifiedDefs(local);
			
			if (!qualified.isEmpty())
			{
				local = new FlatEnvironment(qualified, local);	// NB Not checked!
			}
		}

		if (postdef != null)
		{
			LexNameToken result = new LexNameToken(name.module, "RESULT", location);
			Pattern rp = new IdentifierPattern(result);
			DefinitionList rdefs = rp.getDefinitions(expectedResult, NameScope.NAMES);
			FlatCheckedEnvironment post =
				new FlatCheckedEnvironment(rdefs, local, NameScope.NAMES);

			BooleanType expected = new BooleanType(location);
			Type b = postdef.body.typeCheck(post, null, NameScope.NAMES, expected);

			if (!b.isType(BooleanType.class, location))
			{
				report(3018, "Postcondition returns unexpected type");
				detail2("Actual", b, "Expected", expected);
			}
		}

		// This check returns the type of the function body in the case where
		// all of the curried parameter sets are provided.

		actualResult = body.typeCheck(local, null, scope, expectedResult);

		if (!TypeComparator.compatible(expectedResult, actualResult))
		{
			report(3018, "Function returns unexpected type");
			detail2("Actual", actualResult, "Expected", expectedResult);
		}

		if (type.narrowerThan(accessSpecifier))
		{
			report(3019, "Function parameter visibility less than function definition");
		}
		
		if (base.isVDMPP()
			&& accessSpecifier.access == Token.PRIVATE
			&& body instanceof SubclassResponsibilityExpression)
		{
			report(3329, "Abstract function/operation must be public or protected");
		}

		if (measure == null && recursive)
		{
			warning(5012, "Recursive function has no measure");
		}
		else if (measure != null)
		{
			if (base.isVDMPP()) measure.setTypeQualifier(getMeasureParams());
			measuredef = base.findName(measure, scope);

			if (measuredef == null)
			{
				measure.report(3270, "Measure " + measure + " is not in scope");
			}
			else if (!(measuredef instanceof ExplicitFunctionDefinition))
			{
				measure.report(3271, "Measure " + measure + " is not an explicit function");
			}
			else if (measuredef == this)
			{
				measure.report(3304, "Recursive function cannot be its own measure");
			}
			else
			{
				ExplicitFunctionDefinition efd = (ExplicitFunctionDefinition)measuredef;
				
				if (this.typeParams == null && efd.typeParams != null)
				{
					measure.report(3309, "Measure must not be polymorphic");
				}
				else if (this.typeParams != null && efd.typeParams == null)
				{
					measure.report(3310, "Measure must also be polymorphic");
				}
				else if (this.typeParams != null && efd.typeParams != null
						&& !this.typeParams.equals(efd.typeParams))
				{
					measure.report(3318, "Measure's type parameters must match function's");
					detail2("Actual", efd.typeParams, "Expected", typeParams);
				}
		
				FunctionType mtype = (FunctionType)measuredef.getType();
				
				if (typeParams != null)		// Polymorphic, so compare "shape" of param signature
				{
					if (!mtype.parameters.toString().equals(getMeasureParams().toString()))
					{
						measure.report(3303, "Measure parameters different to function");
						detail2(measure.name, mtype.parameters, "Expected", getMeasureParams());
					}
				}
				else if (!TypeComparator.compatible(mtype.parameters, getMeasureParams()))
				{
					measure.report(3303, "Measure parameters different to function");
					detail2(measure.name, mtype.parameters, "Expected", getMeasureParams());
				}

				if (!(mtype.result instanceof NaturalType))
				{
					if (mtype.result.isProduct(location))
					{
						ProductType pt = mtype.result.getProduct();

						for (Type t: pt.types)
						{
							if (!(t instanceof NaturalType))
							{
								measure.report(3272,
									"Measure range is not a nat, or a nat tuple");
								measure.detail("Actual", mtype.result);
								break;
							}
						}

						measureLexical = pt.types.size();
					}
					else
					{
						measure.report(3272,
							"Measure range is not a nat, or a nat tuple");
						measure.detail("Actual", mtype.result);
					}
				}
			}
		}

		if (!(body instanceof NotYetSpecifiedExpression) &&
			!(body instanceof SubclassResponsibilityExpression))
		{
			local.unusedCheck();
		}
	}

	@Override
	public Type getType()
	{
		return type;		// NB entire "->" type, not the result
	}

	public FunctionType getType(TypeList actualTypes)
	{
		Iterator<Type> ti = actualTypes.iterator();
		FunctionType ftype = type;

		if (typeParams != null)
		{
			for (LexNameToken pname: typeParams)
			{
				Type ptype = ti.next();
				ftype = (FunctionType)ftype.polymorph(pname, ptype);
			}

			ftype.instantiated = true;
		}

		return ftype;
	}
	
	private TypeList getMeasureParams()
	{
		TypeList params = new TypeList();
		params.addAll(type.parameters);
		
		if (isCurried)
		{
			Type rtype = type.result;

			while (rtype instanceof FunctionType)
			{
				FunctionType ftype = (FunctionType)rtype;
				params.addAll(ftype.parameters);
				rtype = ftype.result;
			}
		}
		
		return params;
	}

	private Type checkParams(ListIterator<PatternList> plists, FunctionType ftype)
	{
		TypeList ptypes = ftype.parameters;
		PatternList patterns = plists.next();

		if (patterns.size() > ptypes.size())
		{
			report(3020, "Too many parameter patterns");
			detail2("Pattern(s)", patterns, "Type(s)", ptypes);
			return ftype.result;
		}
		else if (patterns.size() < ptypes.size())
		{
			report(3021, "Too few parameter patterns");
			detail2("Pattern(s)", patterns, "Type(s)", ptypes);
			return ftype.result;
		}

		if (ftype.result instanceof FunctionType)
		{
			if (!plists.hasNext())
			{
				// We're returning the function itself
				return ftype.result;
			}

			// We're returning what the function returns, assuming we
			// pass the right parameters. Note that this recursion
			// means that we finally return the result of calling the
			// function with *all* of the curried argument sets applied.
			// This is because the type check of the body determines
			// the return type when all of the curried parameters are
			// provided.

			return checkParams(plists, (FunctionType)ftype.result);
		}

		if (plists.hasNext())
		{
			report(3022, "Too many curried parameters");
		}

		return ftype.result;
	}

	private List<DefinitionList> getParamDefinitions()
	{
		List<DefinitionList> defList = new Vector<DefinitionList>();
		FunctionType ftype = type;	// Start with the overall function
		Iterator<PatternList> piter = paramPatternList.iterator();

		while (piter.hasNext())
		{
			PatternList plist = piter.next();
			DefinitionList defs = new DefinitionList();
			TypeList ptypes = ftype.parameters;
			Iterator<Type> titer = ptypes.iterator();

			if (plist.size() != ptypes.size())
			{
				// This is a type/param mismatch, reported elsewhere. But we
				// have to create definitions to avoid a cascade of errors.

				Type unknown = new UnknownType(location);

				for (Pattern p: plist)
				{
					defs.addAll(p.getDefinitions(unknown, NameScope.LOCAL));
				}
			}
			else
			{
    			for (Pattern p: plist)
    			{
    				defs.addAll(p.getDefinitions(titer.next(), NameScope.LOCAL));
    			}
			}
			
			defList.add(checkDuplicatePatterns(defs));

			if (ftype.result instanceof FunctionType)	// else???
			{
				ftype = (FunctionType)ftype.result;
			}
		}

		return defList;
	}

	@Override
	public Expression findExpression(int lineno)
	{
		if (predef != null)
		{
			Expression found = predef.findExpression(lineno);
			if (found != null) return found;
		}

		if (postdef != null)
		{
			Expression found = postdef.findExpression(lineno);
			if (found != null) return found;
		}

		return body.findExpression(lineno);
	}

	@Override
	public Definition findName(LexNameToken sought, NameScope scope)
	{
		if (super.findName(sought, scope) != null)
		{
			return this;
		}

		if (predef != null && predef.findName(sought, scope) != null)
		{
			return predef;
		}

		if (postdef != null && postdef.findName(sought, scope) != null)
		{
			return postdef;
		}

		return null;
	}

	@Override
	public NameValuePairList getNamedValues(Context ctxt)
	{
		NameValuePairList nvl = new NameValuePairList();
		Context free = ctxt.getVisibleVariables();

		FunctionValue prefunc =
			(predef == null) ? null : new FunctionValue(predef, null, null, free);

		FunctionValue postfunc =
			(postdef == null) ? null : new FunctionValue(postdef, null, null, free);

		FunctionValue func = new FunctionValue(this, prefunc, postfunc, free);
		func.isStatic = accessSpecifier.isStatic;
		func.uninstantiated = (typeParams != null);
		nvl.add(new NameValuePair(name, func));

		if (predef != null)
		{
			nvl.add(new NameValuePair(predef.name, prefunc));
			prefunc.uninstantiated = (typeParams != null);
		}

		if (postdef != null)
		{
			nvl.add(new NameValuePair(postdef.name, postfunc));
			postfunc.uninstantiated = (typeParams != null);
		}

		if (Settings.dialect == Dialect.VDM_SL)
		{
			// This is needed for recursive local functions
			free.putList(nvl);
		}

		return nvl;
	}

	public FunctionValue getPolymorphicValue(TypeList actualTypes)
	{
		if (polyfuncs == null)
		{
			polyfuncs = new HashMap<TypeList, FunctionValue>();
		}
		else
		{
			// We always return the same function value for a polymorph
			// with a given set of types. This is so that the one function
			// value can record measure counts for recursive polymorphic
			// functions.
			
			FunctionValue rv = polyfuncs.get(actualTypes);
			
			if (rv != null)
			{
				return rv;
			}
		}
		
		FunctionValue prefv = null;
		FunctionValue postfv = null;

		if (predef != null)
		{
			prefv = predef.getPolymorphicValue(actualTypes);
		}
		else
		{
			prefv = null;
		}

		if (postdef != null)
		{
			postfv = postdef.getPolymorphicValue(actualTypes);
		}
		else
		{
			postfv = null;
		}

		FunctionValue rv = new FunctionValue(
				this, actualTypes, prefv, postfv, null);

		polyfuncs.put(actualTypes, rv);
		return rv;
	}

	@Override
	public DefinitionList getDefinitions()
	{
		DefinitionList defs = new DefinitionList(this);

		if (predef != null)
		{
			defs.add(predef);
		}

		if (postdef != null)
		{
			defs.add(postdef);
		}

		return defs;
	}

	@Override
	public LexNameList getVariableNames()
	{
		return new LexNameList(name);
	}

	private ExplicitFunctionDefinition getPreDefinition()
	{
		ExplicitFunctionDefinition def = new ExplicitFunctionDefinition(
			name.getPreName(precondition.location), NameScope.GLOBAL,
			typeParams, type.getCurriedPreType(isCurried),
			paramPatternList, precondition, null, null, false, null);

		def.setAccessSpecifier(accessSpecifier);
		def.classDefinition = classDefinition;
		return def;
	}

	private ExplicitFunctionDefinition getPostDefinition()
	{
		PatternList last = new PatternList();
		int psize = paramPatternList.size();

		for (Pattern p: paramPatternList.get(psize - 1))
		{
			last.add(p);
		}

		LexNameToken result = new LexNameToken(name.module, "RESULT", location);
		last.add(new IdentifierPattern(result));

		List<PatternList> parameters = new Vector<PatternList>();

		if (psize > 1)
		{
			parameters.addAll(paramPatternList.subList(0, psize - 1));
		}

		parameters.add(last);

		ExplicitFunctionDefinition def = new ExplicitFunctionDefinition(
			name.getPostName(postcondition.location), NameScope.GLOBAL,
			typeParams, type.getCurriedPostType(isCurried),
			parameters, postcondition, null, null, false, null);

		def.setAccessSpecifier(accessSpecifier);
		def.classDefinition = classDefinition;
		return def;
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		ProofObligationList obligations = new ProofObligationList();
		LexNameList pids = new LexNameList();
		boolean matchNeeded = false;

		for (PatternList pl: paramPatternList)
		{
			for (Pattern p: pl)
			{
				pids.addAll(p.getVariableNames());
			}
			
			if (!pl.alwaysMatches())
			{
				matchNeeded = true;
			}
		}

		if (pids.hasDuplicates() || matchNeeded)
		{
			obligations.add(new ParameterPatternObligation(this, ctxt));
		}

		if (precondition != null)
		{
			ctxt.push(new POFunctionDefinitionContext(this, false));
			obligations.addAll(precondition.getProofObligations(ctxt));
			ctxt.pop();
		}

		if (postcondition != null)
		{
			ctxt.push(new POFunctionDefinitionContext(this, false));
			obligations.add(new FuncPostConditionObligation(this, ctxt));
			ctxt.push(new POFunctionResultContext(this));
			obligations.addAll(postcondition.getProofObligations(ctxt));
			ctxt.pop();
			ctxt.pop();
		}

		ctxt.push(new POFunctionDefinitionContext(this, true));
		obligations.addAll(body.getProofObligations(ctxt));

		if (isUndefined ||
			!TypeComparator.isSubType(actualResult, expectedResult))
		{
			obligations.add(
				new SubTypeObligation(this, expectedResult, actualResult, ctxt));
		}

		ctxt.pop();

		return obligations;
	}

	@Override
	public String kind()
	{
		return "explicit function";
	}

	@Override
	public boolean isFunction()
	{
		return true;
	}

	@Override
	public boolean isCallableFunction()
	{
		return true;
	}

	@Override
	public boolean isSubclassResponsibility()
	{
		return body instanceof SubclassResponsibilityExpression;
	}
}
