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

import org.overturetool.vdmj.Release;
import org.overturetool.vdmj.Settings;
import org.overturetool.vdmj.definitions.BUSClassDefinition;
import org.overturetool.vdmj.definitions.CPUClassDefinition;
import org.overturetool.vdmj.definitions.ClassDefinition;
import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.definitions.SystemDefinition;
import org.overturetool.vdmj.lex.LexIdentifierToken;
import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.runtime.ValueException;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.types.UnknownType;
import org.overturetool.vdmj.util.Utils;
import org.overturetool.vdmj.values.ObjectValue;
import org.overturetool.vdmj.values.Value;
import org.overturetool.vdmj.values.ValueList;

public class NewExpression extends Expression
{
	private static final long serialVersionUID = 1L;
	public final LexIdentifierToken classname;
	public final ExpressionList args;

	private ClassDefinition classdef;
	private Definition ctorDefinition = null;

	public NewExpression(LexLocation location,
		LexIdentifierToken classname, ExpressionList args)
	{
		super(location);
		this.classname = classname;
		this.args = args;
		this.classname.location.executable(true);
	}

	@Override
	public String toString()
	{
		return "new " + classname + "("+ Utils.listToString(args) + ")";
	}

	@Override
	public Type typeCheck(Environment env, TypeList qualifiers, NameScope scope, Type constraint)
	{
		Definition cdef = env.findType(classname.getClassName(), null);

		if (cdef == null || !(cdef instanceof ClassDefinition))
		{
			report(3133, "Class name " + classname + " not in scope");
			return new UnknownType(location);
		}
		
		if (Settings.release == Release.VDM_10 && env.isFunctional())
		{
			report(3348, "Cannot use 'new' in a functional context");
		}

		classdef = (ClassDefinition)cdef;

		if (classdef instanceof SystemDefinition)
		{
			report(3279, "Cannot instantiate system class " + classdef.name);
		}
		
		if (classdef.isAbstract)
		{
			report(3330, "Cannot instantiate abstract class " + classdef.name);
			
			for (Definition d: classdef.getLocalDefinitions())
			{
				if (d.isSubclassResponsibility())
				{
					detail("Unimplemented", d.name.name + d.getType());
				}
			}
		}

		TypeList argtypes = new TypeList();

		for (Expression a: args)
		{
			argtypes.add(a.typeCheck(env, null, scope, null));
		}

		Definition opdef = classdef.findConstructor(argtypes);

		if (opdef == null)
		{
			if (!args.isEmpty())	// Not having a default ctor is OK
    		{
    			report(3134, "Class has no constructor with these parameter types");
    			detail("Called", classdef.getCtorName(argtypes));
    		}
			else if (classdef instanceof CPUClassDefinition ||
					 classdef instanceof BUSClassDefinition)
			{
				report(3297, "Cannot use default constructor for this class");
			}
		}
		else
		{
			if (!opdef.isCallableOperation())
    		{
    			report(3135, "Class has no constructor with these parameter types");
    			detail("Called", classdef.getCtorName(argtypes));
    		}
			else if (!ClassDefinition.isAccessible(env, opdef, false)) // (opdef.accessSpecifier.access == Token.PRIVATE)
			{
    			report(3292, "Constructor is not accessible");
    			detail("Called", classdef.getCtorName(argtypes));
			}
			else
			{
				ctorDefinition = opdef;
			}
		}

		return checkConstraint(constraint, classdef.getType());
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);
		classname.location.hit();

		try
		{
    		ValueList argvals = new ValueList();

     		for (Expression arg: args)
    		{
    			argvals.add(arg.eval(ctxt));
    		}

			ObjectValue objval =
				classdef.newInstance(ctorDefinition, argvals, ctxt);

    		if (objval.invlistener != null)
    		{
    			// Check the initial values of the object's fields
    			objval.invlistener.doInvariantChecks = true;
    			objval.invlistener.changedValue(location, objval, ctxt);
    		}

    		return objval;
		}
		catch (ValueException e)
		{
			return abort(e);
		}
	}

	@Override
	public Expression findExpression(int lineno)
	{
		Expression found = super.findExpression(lineno);
		if (found != null) return found;

		return args.findExpression(lineno);
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		return args.getProofObligations(ctxt);
	}

	@Override
	public String kind()
	{
		return "new";
	}

	@Override
	public ValueList getValues(Context ctxt)
	{
		return args.getValues(ctxt);
	}

	@Override
	public LexNameList getOldNames()
	{
		return args.getOldNames();
	}
}
