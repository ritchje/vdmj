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

import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.definitions.MultiBindListDefinition;
import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.patterns.Bind;
import org.overturetool.vdmj.patterns.TypeBind;
import org.overturetool.vdmj.pog.POForAllContext;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.runtime.PatternMatchException;
import org.overturetool.vdmj.runtime.ValueException;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.FlatCheckedEnvironment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.types.BooleanType;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.values.BooleanValue;
import org.overturetool.vdmj.values.Value;
import org.overturetool.vdmj.values.ValueList;

public class Exists1Expression extends Expression
{
	private static final long serialVersionUID = 1L;
	public final Bind bind;
	public final Expression predicate;

	private Definition def = null;

	public Exists1Expression(LexLocation location, Bind bind, Expression predicate)
	{
		super(location);
		this.bind = bind;
		this.predicate = predicate;
	}

	@Override
	public String toString()
	{
		return "(exists1 " + bind + " & " + predicate + ")";
	}

	@Override
	public Type typeCheck(Environment base, TypeList qualifiers, NameScope scope, Type constraint)
	{
		def = new MultiBindListDefinition(bind.location, bind.getMultipleBindList());
		def.typeCheck(base, scope);
		Environment local = new FlatCheckedEnvironment(def, base, scope);
		
		if (bind instanceof TypeBind)
		{
			TypeBind tb = (TypeBind)bind;
			tb.typeResolve(base);
		}

		if (!predicate.typeCheck(local, null, scope, new BooleanType(location)).isType(BooleanType.class, location))
		{
			predicate.report(3088, "Predicate is not boolean");
		}

		local.unusedCheck();
		return checkConstraint(constraint, new BooleanType(location));
	}

	@Override
	public Expression findExpression(int lineno)
	{
		Expression found = super.findExpression(lineno);
		if (found != null) return found;

		return predicate.findExpression(lineno);
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);
		ValueList allValues = null;
		boolean alreadyFound = false;
		
		try
		{
			allValues = bind.getBindValues(ctxt, false);
		}
		catch (ValueException e)
		{
			abort(e);
		}

		for (Value val: allValues)
		{
			try
			{
				Context evalContext = new Context(location, "exists1", ctxt);
				evalContext.putList(bind.pattern.getNamedValues(val, ctxt));

				if (predicate.eval(evalContext).boolValue(ctxt))
				{
					if (alreadyFound)
					{
						return new BooleanValue(false);
					}

					alreadyFound = true;
				}
	        }
	        catch (ValueException e)
	        {
	        	abort(e);
	        }
			catch (PatternMatchException e)
			{
				// Ignore pattern mismatches
			}
		}

		return new BooleanValue(alreadyFound);
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		ProofObligationList obligations = bind.getProofObligations(ctxt);

		ctxt.push(new POForAllContext(this));
		obligations.addAll(predicate.getProofObligations(ctxt));
		ctxt.pop();

		return obligations;
	}

	@Override
	public String kind()
	{
		return "exists1";
	}

	@Override
	public ValueList getValues(Context ctxt)
	{
		ValueList list = bind.getValues(ctxt);
		list.addAll(predicate.getValues(ctxt));
		return list;
	}

	@Override
	public LexNameList getOldNames()
	{
		LexNameList list = bind.getOldNames();
		list.addAll(predicate.getOldNames());
		return list;
	}
}
