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

import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.types.Seq1Type;
import org.overturetool.vdmj.types.SeqType;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.types.TypeSet;
import org.overturetool.vdmj.util.Utils;
import org.overturetool.vdmj.values.SeqValue;
import org.overturetool.vdmj.values.Value;
import org.overturetool.vdmj.values.ValueList;

public class SeqEnumExpression extends SeqExpression
{
	private static final long serialVersionUID = 1L;
	public final ExpressionList members;
	public TypeList types = null;

	public SeqEnumExpression(LexLocation location)
	{
		super(location);
		members = new ExpressionList();
	}

	public SeqEnumExpression(LexLocation location, ExpressionList members)
	{
		super(location);
		this.members = members;
	}

	@Override
	public String toString()
	{
		return Utils.listToString("[", members, ", ", "]");
	}

	@Override
	public Type typeCheck(Environment env, TypeList qualifiers, NameScope scope, Type constraint)
	{
		TypeSet ts = new TypeSet();
		types = new TypeList();
		
		Type elemConstraint = null;
		
		if (constraint != null && constraint.isSeq(location))
		{
			elemConstraint = constraint.getSeq().seqof;
		}

		for (Expression ex: members)
		{
			Type mt = ex.typeCheck(env, null, scope, elemConstraint);
  			ts.add(mt);
  			types.add(mt);
		}

		return ts.isEmpty() ? new SeqType(location) :
			new Seq1Type(location, ts.getType(location));
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);

		ValueList values = new ValueList();

		for (Expression e: members)
		{
			values.add(e.eval(ctxt));
		}

		return new SeqValue(values);
	}

	@Override
	public Expression findExpression(int lineno)
	{
		Expression found = super.findExpression(lineno);
		if (found != null) return found;

		return members.findExpression(lineno);
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		return members.getProofObligations(ctxt);
	}

	@Override
	public String kind()
	{
		return "seq enumeration";
	}

	@Override
	public ValueList getValues(Context ctxt)
	{
		return members.getValues(ctxt);
	}

	@Override
	public LexNameList getOldNames()
	{
		return members.getOldNames();
	}
}
