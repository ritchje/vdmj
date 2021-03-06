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

package org.overturetool.vdmj.expressions;

import org.overturetool.vdmj.definitions.DefinitionList;
import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.POImpliesContext;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.runtime.ValueException;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.FlatEnvironment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.types.BooleanType;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.values.Value;
import org.overturetool.vdmj.values.ValueList;

public class ElseIfExpression extends Expression
{
	private static final long serialVersionUID = 1L;
	public final Expression elseIfExp;
	public final Expression thenExp;

	public ElseIfExpression(LexLocation location,
			Expression elseIfExp, Expression thenExp)
	{
		super(location);
		this.elseIfExp = elseIfExp;
		this.thenExp = thenExp;
	}

	@Override
	public String toString()
	{
		return "elseif " + elseIfExp + "\nthen " + thenExp;
	}

	@Override
	public Type typeCheck(Environment env, TypeList qualifiers, NameScope scope, Type constraint)
	{
		if (!elseIfExp.typeCheck(env, null, scope, null).isType(BooleanType.class, location))
		{
			report(3086, "Else clause is not a boolean");
		}

		DefinitionList qualified = elseIfExp.getQualifiedDefs(env);
		Environment qenv = env;
		
		if (!qualified.isEmpty())
		{
			qenv = new FlatEnvironment(qualified, env);
		}

		return thenExp.typeCheck(qenv, null, scope, constraint);
	}

	@Override
	public Expression findExpression(int lineno)
	{
		Expression found = super.findExpression(lineno);
		if (found != null) return found;

		return thenExp.findExpression(lineno);
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);

		try
		{
			return elseIfExp.eval(ctxt).boolValue(ctxt) ? thenExp.eval(ctxt) : null;
		}
        catch (ValueException e)
        {
        	return abort(e);
        }
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		ctxt.push(new POImpliesContext(elseIfExp));
		ProofObligationList obligations = thenExp.getProofObligations(ctxt);
		ctxt.pop();

		return obligations;
	}

	@Override
	public String kind()
	{
		return "elseif";
	}

	@Override
	public ValueList getValues(Context ctxt)
	{
		ValueList list = elseIfExp.getValues(ctxt);
		list.addAll(thenExp.getValues(ctxt));
		return list;
	}

	@Override
	public LexNameList getOldNames()
	{
		LexNameList list = elseIfExp.getOldNames();
		list.addAll(thenExp.getOldNames());
		return list;
	}

	@Override
	public ExpressionList getSubExpressions()
	{
		ExpressionList subs = elseIfExp.getSubExpressions();
		subs.addAll(thenExp.getSubExpressions());
		subs.add(this);
		return subs;
	}
}
