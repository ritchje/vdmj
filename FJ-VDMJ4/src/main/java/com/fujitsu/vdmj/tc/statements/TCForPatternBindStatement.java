/*******************************************************************************
 *
 *	Copyright (c) 2016 Fujitsu Services Ltd.
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

package com.fujitsu.vdmj.tc.statements;

import java.util.concurrent.atomic.AtomicBoolean;

import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.tc.definitions.TCDefinitionList;
import com.fujitsu.vdmj.tc.expressions.TCExpression;
import com.fujitsu.vdmj.tc.lex.TCNameSet;
import com.fujitsu.vdmj.tc.patterns.TCPatternBind;
import com.fujitsu.vdmj.tc.types.TCSeqType;
import com.fujitsu.vdmj.tc.types.TCType;
import com.fujitsu.vdmj.tc.types.TCTypeSet;
import com.fujitsu.vdmj.typechecker.Environment;
import com.fujitsu.vdmj.typechecker.FlatCheckedEnvironment;
import com.fujitsu.vdmj.typechecker.NameScope;

public class TCForPatternBindStatement extends TCStatement
{
	private static final long serialVersionUID = 1L;
	public final TCPatternBind patternBind;
	public final boolean reverse;
	public final TCExpression exp;
	public final TCStatement statement;

	private TCSeqType seqType;

	public TCForPatternBindStatement(LexLocation location,
		TCPatternBind patternBind, boolean reverse, TCExpression exp, TCStatement body)
	{
		super(location);
		this.patternBind = patternBind;
		this.reverse = reverse;
		this.exp = exp;
		this.statement = body;
	}

	@Override
	public String toString()
	{
		return "for " + patternBind + " in " +
			(reverse ? " reverse " : "") + exp + " do\n" + statement;
	}

	@Override
	public TCType typeCheck(Environment base, NameScope scope, TCType constraint)
	{
		TCType stype = exp.typeCheck(base, null, scope, null);
		Environment local = base;

		if (stype.isSeq(location))
		{
			seqType = stype.getSeq();
			patternBind.typeCheck(base, scope, seqType.seqof);
			TCDefinitionList defs = patternBind.getDefinitions();
			defs.typeCheck(base, scope);
			local = new FlatCheckedEnvironment(defs, base, scope);
		}
		else
		{
			exp.report(3223, "Expecting sequence type after 'in'");
		}

		TCType rt = statement.typeCheck(local, scope, constraint);
		local.unusedCheck();
		return rt;
	}

	@Override
	public TCTypeSet exitCheck()
	{
		return statement.exitCheck();
	}

	@Override
	public TCNameSet getFreeVariables(Environment globals, Environment env, AtomicBoolean returns)
	{
		return exp.getFreeVariables(globals, env);
	}
}
