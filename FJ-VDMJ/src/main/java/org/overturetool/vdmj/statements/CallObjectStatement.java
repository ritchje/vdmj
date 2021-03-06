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

package org.overturetool.vdmj.statements;

import org.overturetool.vdmj.Settings;
import org.overturetool.vdmj.definitions.ClassDefinition;
import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.expressions.Expression;
import org.overturetool.vdmj.expressions.ExpressionList;
import org.overturetool.vdmj.expressions.StringLiteralExpression;
import org.overturetool.vdmj.expressions.VariableExpression;
import org.overturetool.vdmj.lex.Dialect;
import org.overturetool.vdmj.lex.LexIdentifierToken;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.lex.LexStringToken;
import org.overturetool.vdmj.pog.POContextStack;
import org.overturetool.vdmj.pog.ProofObligationList;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.runtime.ValueException;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.typechecker.PrivateClassEnvironment;
import org.overturetool.vdmj.typechecker.PublicClassEnvironment;
import org.overturetool.vdmj.typechecker.TypeComparator;
import org.overturetool.vdmj.types.ClassType;
import org.overturetool.vdmj.types.FunctionType;
import org.overturetool.vdmj.types.OperationType;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.types.TypeList;
import org.overturetool.vdmj.types.TypeSet;
import org.overturetool.vdmj.types.UnionType;
import org.overturetool.vdmj.types.UnknownType;
import org.overturetool.vdmj.types.VoidType;
import org.overturetool.vdmj.util.Utils;
import org.overturetool.vdmj.values.FunctionValue;
import org.overturetool.vdmj.values.ObjectValue;
import org.overturetool.vdmj.values.OperationValue;
import org.overturetool.vdmj.values.Value;
import org.overturetool.vdmj.values.ValueList;

public class CallObjectStatement extends Statement
{
	private static final long serialVersionUID = 1L;
	public final ObjectDesignator designator;
	public final LexNameToken classname;
	public final LexIdentifierToken fieldname;
	public final ExpressionList args;
	public final boolean explicit;

	public LexNameToken field = null;

	public CallObjectStatement(ObjectDesignator designator,
		LexNameToken classname, ExpressionList args)
	{
		super(designator.location);

		this.designator = designator;
		this.classname = classname;
		this.fieldname = null;
		this.args = args;
		this.explicit = classname.explicit;
	}

	public CallObjectStatement(ObjectDesignator designator,
		LexIdentifierToken fieldname, ExpressionList args)
	{
		super(designator.location);

		this.designator = designator;
		this.classname = null;
		this.fieldname = fieldname;
		this.args = args;
		this.explicit = false;
	}

	@Override
	public String toString()
	{
		return designator + "." +
			(classname != null ? classname : fieldname) +
			"(" + Utils.listToString(args) + ")";
	}

	@Override
	public String kind()
	{
		return "object call";
	}

	@Override
	public Type typeCheck(Environment env, NameScope scope, Type constraint)
	{
		Type dtype = designator.typeCheck(env, null);

		if (!dtype.isClass(env))
		{
			report(3207, "Object designator is not an object type");
			return new UnknownType(location);
		}

		ClassType ctype = dtype.getClassType(env);
		ClassDefinition classdef = ctype.classdef;
		ClassDefinition self = env.findClassDefinition();
		Environment classenv = null;

		if (self == classdef || self.hasSupertype(classdef.getType()))
		{
			// All fields visible. Note that protected fields are inherited
			// into "locals" so they are effectively private
			classenv = new PrivateClassEnvironment(self);
		}
		else
		{
			// Only public fields externally visible
			classenv = new PublicClassEnvironment(classdef);
		}

		if (classname == null)
		{
			field = new LexNameToken(
				ctype.name.name, fieldname.name, fieldname.location);
		}
		else
		{
			field = classname;
		}

		field.location.executable(true);
		TypeList atypes = getArgTypes(env, scope);
		field.setTypeQualifier(atypes);
		Definition fdef = classenv.findName(field, scope);

		if (isConstructor(fdef) && !inConstructor(env))
		{
			report(3337, "Cannot call a constructor from here");
			return new UnknownType(location);				
		}

		// Special code for the deploy method of CPU

		if (Settings.dialect == Dialect.VDM_RT &&
			field.module.equals("CPU") && field.name.equals("deploy"))
		{
			if (!atypes.get(0).isType(ClassType.class, location))
			{
				args.get(0).report(3280, "Argument to deploy must be an object");
			}

			return new VoidType(location);
		}
		else if (Settings.dialect == Dialect.VDM_RT &&
			field.module.equals("CPU") && field.name.equals("setPriority"))
		{
			if (!(atypes.get(0) instanceof OperationType))
			{
				args.get(0).report(3290, "Argument to setPriority must be an operation");
			}
			else
			{
				// Convert the variable expression to a string...
    			VariableExpression a1 = (VariableExpression)args.get(0);
    			args.remove(0);
    			args.add(0, new StringLiteralExpression(
    				new LexStringToken(
    					a1.name.getExplicit(true).getName(), a1.location)));

    			if (a1.name.module.equals(a1.name.name))	// it's a constructor
    			{
    				args.get(0).report(3291, "Argument to setPriority cannot be a constructor");
    			}
			}

			return new VoidType(location);
		}
		else if (fdef == null)
		{
			report(3209, "Member " + field + " is not in scope");
			return new UnknownType(location);
		}
		else if (fdef.isStatic() && !env.isStatic())
		{
			// warning(5005, "Should invoke member " + field + " from a static context");
		}

		Type type = fdef.getType();

		if (type.isOperation(location))
		{
			OperationType optype = type.getOperation();
			optype.typeResolve(env, null);
    		Definition encl = env.getEnclosingDefinition();
    		
    		if (encl != null && encl.isPure() && !optype.isPure())
    		{
    			report(3339, "Cannot call impure operation from a pure operation");
    		}

    		field.setTypeQualifier(optype.parameters);
			checkArgTypes(optype.parameters, atypes);	// Not necessary?
			return checkReturnType(constraint, optype.result);
		}
		else if (type.isFunction(location))
		{
			// This is the case where a function is called as an operation without
			// a "return" statement.

			FunctionType ftype = type.getFunction();
			ftype.typeResolve(env, null);
			field.setTypeQualifier(ftype.parameters);
			checkArgTypes(ftype.parameters, atypes);	// Not necessary?
			return checkReturnType(constraint, ftype.result);
		}
		else
		{
			report(3210, "Object member is neither a function nor an operation");
			return new UnknownType(location);
		}
	}

	@Override
	public TypeSet exitCheck()
	{
		// We don't know what an operation call will raise
		return new TypeSet(new UnknownType(location));
	}

	private TypeList getArgTypes(Environment env, NameScope scope)
	{
		TypeList types = new TypeList();

		for (Expression a: args)
		{
			types.add(a.typeCheck(env, null, scope, null));
		}

		return types;
	}

	private void checkArgTypes(TypeList ptypes, TypeList atypes)
	{
		if (ptypes.size() != atypes.size())
		{
			report(3211, "Expecting " + ptypes.size() + " arguments");
		}
		else
		{
			int i=0;

			for (Type atype: atypes)
			{
				Type ptype = ptypes.get(i++);

				if (!TypeComparator.compatible(ptype, atype))
				{
					atype.report(3212, "Unexpected type for argument " + i);
					detail2("Expected", ptype, "Actual", atype);
				}
			}
		}
	}

	@Override
	public Value eval(Context ctxt)
	{
		breakpoint.check(location, ctxt);
		field.location.hit();

		// The check above increments the hit counter for the call, but so
		// do the evaluations of the designator below, so we correct the
		// hit count here...

		location.hits--;

		try
		{
			ValueList argValues = new ValueList();

			for (Expression arg: args)
			{
				argValues.add(arg.eval(ctxt));
			}
			
			// Work out the actual types of the arguments, so we bind the right op/fn
			TypeList argTypes = new TypeList();
			int arg = 0;
			
			for (Type argType: field.typeQualifier)
			{
				if (argType instanceof UnionType)
				{
					UnionType u = (UnionType)argType;
					
					for (Type possible: u.types)
					{
						try
						{
							argValues.get(arg).convertTo(possible, ctxt);
							argTypes.add(possible);
							break;
						}
						catch (ValueException e)
						{
							// Try again
						}
					}
				}
				else
				{
					argTypes.add(argType);
				}
				
				arg++;
			}
			
			if (argTypes.size() != field.typeQualifier.size())
			{
				field.abort(4168, "Arguments do not match parameters: " + field, ctxt);
			}
			else
			{
				field = field.getModifiedName(argTypes);
			}

			ObjectValue obj = designator.eval(ctxt).objectValue(ctxt);
			Value v = obj.get(field, explicit);

			if (v == null)
			{
    			field.abort(4035, "Object has no field: " + field.name, ctxt);
			}

			v = v.deref();

			if (v instanceof OperationValue)
			{
    			OperationValue op = v.operationValue(ctxt);
    			return op.eval(location, argValues, ctxt);
			}
			else
			{
    			FunctionValue op = v.functionValue(ctxt);
    			return op.eval(location, argValues, ctxt);
			}
		}
		catch (ValueException e)
		{
			return abort(e);
		}
	}

	@Override
	public Expression findExpression(int lineno)
	{
		return args.findExpression(lineno);
	}

	@Override
	public ProofObligationList getProofObligations(POContextStack ctxt)
	{
		ProofObligationList obligations = new ProofObligationList();

		for (Expression exp: args)
		{
			obligations.addAll(exp.getProofObligations(ctxt));
		}

		return obligations;
	}
}
